package top.yogiczy.mytv.tv.ui.screen.vod

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import top.yogiczy.mytv.core.data.entities.vod.VodSource
import top.yogiczy.mytv.core.data.utils.Globals

/**
 * 点播源健康分管理器。
 *
 * 每次搜索/拉列表/拉详情时记录每个源的成败与耗时（见 [record]），
 * 综合成功率与平均响应时间算分（0~1），用于：
 *  - 聚合推荐选源（[topSources]）
 *  - 播放失败时优先换健康分高的线路
 *
 * 持久化在 SharedPreferences(vod_source_health)，仅保留最近 200 个源。
 */
object VodSourceHealthManager {
    private const val PREFS_NAME = "vod_source_health"
    private const val KEY = "records"
    private const val MAX_ENTRIES = 200
    /** 响应时间基准：8 秒及以上视为最差 */
    private const val TIME_BASELINE_MS = 8000.0

    @Serializable
    data class Record(
        val success: Int = 0,
        val fail: Int = 0,
        val totalMs: Long = 0,
    )

    private var records: MutableMap<String, Record>? = null

    @Synchronized
    private fun load(): MutableMap<String, Record> {
        records?.let { return it }
        val prefs = Globals.appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, "") ?: ""
        val map = runCatching {
            Globals.json.decodeFromString(
                MapSerializer(String.serializer(), Record.serializer()), raw
            )
        }.getOrDefault(emptyMap())
        return map.toMutableMap().also { records = it }
    }

    @Synchronized
    private fun save() {
        val map = records ?: return
        val prefs = Globals.appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(
            KEY,
            Globals.json.encodeToString(
                MapSerializer(String.serializer(), Record.serializer()), map
            )
        ).apply()
    }

    /** 记录一次探测结果（success=成功拿到响应; elapsedMs=耗时） */
    @Synchronized
    fun record(sourceKey: String, success: Boolean, elapsedMs: Long) {
        val map = load()
        val old = map[sourceKey] ?: Record()
        map[sourceKey] = Record(
            success = (old.success + if (success) 1 else 0).coerceAtMost(1000),
            fail = (old.fail + if (success) 0 else 1).coerceAtMost(1000),
            totalMs = old.totalMs + if (success) elapsedMs else 0,
        )
        // 超量时淘汰最久未更新且失败的记录（简单起见按 fail 最多的淘汰）
        if (map.size > MAX_ENTRIES) {
            val victim = map.maxByOrNull { it.value.fail }?.key
            if (victim != null) map.remove(victim)
        }
        save()
    }

    /** 健康分 0~1：成功率 70% + 响应时间 30%；无数据的新源给 0.5 中立法 */
    fun score(sourceKey: String): Double {
        val r = load()[sourceKey] ?: return 0.5
        val total = r.success + r.fail
        if (total == 0) return 0.5
        val rate = r.success.toDouble() / total
        val avgMs = if (r.success > 0) r.totalMs.toDouble() / r.success else TIME_BASELINE_MS
        val timeFactor = (1.0 - avgMs / TIME_BASELINE_MS).coerceIn(0.2, 1.0)
        return rate * 0.7 + timeFactor * 0.3
    }

    /** 选出健康分最高的 n 个源（无数据源按 0.5 参与排序） */
    fun topSources(all: List<VodSource>, n: Int): List<VodSource> =
        all.sortedByDescending { score(it.key) }.take(n)
}
