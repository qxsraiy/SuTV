package top.yogiczy.mytv.tv.ui.screen.dashboard.components

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import top.yogiczy.mytv.core.data.entities.vod.VodItem
import top.yogiczy.mytv.core.data.entities.vod.VodItemList
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.tv.ui.utils.Configs
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 点播海报缓存管理器
 *
 * 流程：
 * 1. 打开页面时：如果有 new.jpg，替换 current.jpg（删旧current），显示 current.jpg
 * 2. 后台：随机选源+随机取影片，下载 vod_pic 存为 new.jpg
 * 3. 下次打开：new→current，显示新的，再后台抓下一张
 */
object DashboardPosterManager {
    private const val TAG = "PosterManager"
    private const val CURRENT_FILE = "vod_poster_current.jpg"
    private const val NEW_FILE = "vod_poster_new.jpg"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 获取当前应显示的海报文件（如果存在）
     * 同时执行轮换：new→current
     */
    fun getCurrentPoster(context: Context): File? {
        val dir = context.cacheDir
        val current = File(dir, CURRENT_FILE)
        val newFile = File(dir, NEW_FILE)

        // 如果有新缓存，替换当前
        if (newFile.exists() && newFile.length() > 0) {
            if (current.exists()) current.delete()
            newFile.renameTo(current)
            Log.i(TAG, "轮换海报: new→current")
        }

        return if (current.exists() && current.length() > 0) current else null
    }

    /**
     * 后台抓取一张新的点播海报
     * 随机选源→随机取列表→随机选影片→下载图片
     */
    suspend fun fetchNewPoster(context: Context) = withContext(Dispatchers.IO) {
        try {
            val allSources = Constants.VOD_SOURCE_LIST + Configs.vodSourceList
            if (allSources.isEmpty()) return@withContext

            // 随机选源（最多尝试3个）
            val shuffled = allSources.shuffled().take(3)
            for (source in shuffled) {
                try {
                    // 随机页码 1-5
                    val page = (1..5).random()
                    val url = "${source.api}?ac=videolist&pg=$page"
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0")
                        .build()

                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use
                        val body = resp.body?.string() ?: return@use
                        val cms = json.decodeFromString(VodItemList.serializer(), body)
                        val items = cms.list.filter { it.vod_pic.isNotBlank() }
                        if (items.isEmpty()) return@use

                        // 随机选一个影片
                        val item = items.random()
                        downloadImage(context, item.vod_pic)
                        Log.i(TAG, "缓存新海报: ${item.vod_name} from ${source.name}")
                        return@withContext
                    }
                } catch (_: Exception) {
                    // 继续尝试下一个源
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "抓取海报失败: ${e.message}")
        }
    }

    private fun downloadImage(context: Context, url: String) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return
            val bytes = resp.body?.bytes() ?: return
            if (bytes.size < 1024) return // 太小可能是错误图

            val newFile = File(context.cacheDir, NEW_FILE)
            if (newFile.exists()) newFile.delete()
            newFile.writeBytes(bytes)
        }
    }
}
