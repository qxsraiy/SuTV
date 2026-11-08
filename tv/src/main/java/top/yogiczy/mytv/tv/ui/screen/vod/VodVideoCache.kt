package top.yogiczy.mytv.tv.ui.screen.vod

import android.content.Context
import android.os.StatFs
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * 点播视频缓存单例（media3 SimpleCache，LRU 淘汰）。
 *
 * 删除机制（三层）：
 *  1. 容量淘汰：超过 [maxBytes] 时由 LRU 自动淘汰最旧分片；
 *  2. 过期清理：启动/进入播放页时清理 [maxAgeMs] 前的 .v3 分片；
 *  3. 低空间保护：可用空间低于 500MB 时把容量上限临时减半。
 * Application/播放页退出时 release（见 [release]）。
 */
object VodVideoCache {
    private const val MAX_BYTES = 1024L * 1024 * 1024          // 1 GB
    private const val LOW_SPACE_MAX_BYTES = 512L * 1024 * 1024 // 低空间时 512 MB
    private const val LOW_SPACE_THRESHOLD = 500L * 1024 * 1024 // 可用 <500MB 触发保护
    private const val MAX_AGE_MS = 3L * 24 * 3600 * 1000       // 分片保留 3 天
    private const val CLEAN_INTERVAL_MS = 6L * 3600 * 1000     // 过期清理最小间隔 6h

    @Volatile
    private var cache: SimpleCache? = null
    private var lastCleanAt = 0L

    @Synchronized
    fun get(context: Context): Cache {
        cache?.let { return it }
        val dir = File(context.cacheDir, "vod_video")
        val maxBytes = runCatching {
            val stat = StatFs(dir.apply { mkdirs() }.absolutePath)
            if (stat.availableBytes < LOW_SPACE_THRESHOLD) LOW_SPACE_MAX_BYTES else MAX_BYTES
        }.getOrDefault(MAX_BYTES)
        return SimpleCache(
            dir,
            LeastRecentlyUsedCacheEvictor(maxBytes),
            StandaloneDatabaseProvider(context),
        ).also {
            cache = it
            cleanExpired()
        }
    }

    /** 按时间清理过期分片（限频：6 小时最多一次） */
    fun cleanExpired() {
        val now = System.currentTimeMillis()
        if (now - lastCleanAt < CLEAN_INTERVAL_MS) return
        lastCleanAt = now
        Thread {
            runCatching {
                val c = cache ?: return@Thread
                // SimpleCache 没有公开目录访问，通过 keys 遍历删除过期 span
                val expired = mutableListOf<androidx.media3.datasource.cache.CacheSpan>()
                for (key in c.keys) {
                    val spans = c.getCachedSpans(key)
                    for (span in spans) {
                        if (span.isCached && now - span.lastTouchTimestamp > MAX_AGE_MS) {
                            expired.add(span)
                        }
                    }
                }
                for (span in expired) {
                    runCatching { c.removeSpan(span) }
                }
            }
        }.start()
    }

    /** 手动清空全部缓存（设置页"清除缓存"可调用） */
    fun clearAll() {
        Thread {
            runCatching {
                val c = cache ?: return@Thread
                for (key in c.keys.toList()) {
                    runCatching { c.removeResource(key) }
                }
            }
        }.start()
    }

    @Synchronized
    fun release() {
        runCatching { cache?.release() }
        cache = null
    }
}
