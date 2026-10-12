package top.yogiczy.mytv.tv.ui.screen.vod

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class VodHistoryItem(
    val sourceKey: String,
    val vodId: String,
    val vodName: String,
    val vodPic: String,
    val epIdx: Int,
    val timestamp: Long,
    val positionMs: Long = 0,
)

object VodHistoryManager {
    private const val PREFS_NAME = "vod_history"
    private const val KEY = "items"
    private const val MAX_ITEMS = 20
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // 全局版本号 — 每次修改时自增，观察者自动刷新
    val version = mutableStateOf(0)

    fun load(context: Context): List<VodHistoryItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString(ListSerializer(VodHistoryItem.serializer()), raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(context: Context, item: VodHistoryItem) {
        val current = load(context).toMutableList()
        // 去重: 同源同片覆盖
        current.removeAll { it.sourceKey == item.sourceKey && it.vodId == item.vodId }
        current.add(0, item)
        if (current.size > MAX_ITEMS) current.subList(MAX_ITEMS, current.size).clear()
        save(context, current)
        version.value++
    }

    fun remove(context: Context, sourceKey: String, vodId: String) {
        val current = load(context).toMutableList()
        current.removeAll { it.sourceKey == sourceKey && it.vodId == vodId }
        save(context, current)
        version.value++
    }

    // 获取某剧某集的播放进度
    fun getPosition(context: Context, sourceKey: String, vodId: String, epIdx: Int): Long {
        return load(context).find {
            it.sourceKey == sourceKey && it.vodId == vodId && it.epIdx == epIdx
        }?.positionMs ?: 0
    }

    // 更新播放进度 (不改变列表顺序)
    fun updatePosition(context: Context, sourceKey: String, vodId: String, epIdx: Int, positionMs: Long) {
        val current = load(context).toMutableList()
        val idx = current.indexOfFirst { it.sourceKey == sourceKey && it.vodId == vodId && it.epIdx == epIdx }
        if (idx >= 0) {
            val item = current[idx]
            current[idx] = item.copy(positionMs = positionMs, timestamp = System.currentTimeMillis())
        }
        save(context, current)
        version.value++
    }

    private fun save(context: Context, items: List<VodHistoryItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY, json.encodeToString(ListSerializer(VodHistoryItem.serializer()), items)).apply()
    }
}
