package top.yogiczy.mytv.tv.ui.screen.vod

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class VodFavoriteItem(
    val sourceKey: String,
    val vodId: String,
    val vodName: String,
    val vodPic: String,
    val timestamp: Long = System.currentTimeMillis(),
)

object VodFavoriteManager {
    private const val PREFS_NAME = "vod_favorites"
    private const val KEY = "items"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val version = mutableStateOf(0)

    fun load(context: Context): List<VodFavoriteItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString(ListSerializer(VodFavoriteItem.serializer()), raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isFavorite(context: Context, sourceKey: String, vodId: String): Boolean {
        return load(context).any { it.sourceKey == sourceKey && it.vodId == vodId }
    }

    fun toggle(context: Context, item: VodFavoriteItem): Boolean {
        val current = load(context).toMutableList()
        val existing = current.any { it.sourceKey == item.sourceKey && it.vodId == item.vodId }
        if (existing) {
            current.removeAll { it.sourceKey == item.sourceKey && it.vodId == item.vodId }
        } else {
            current.add(0, item)
        }
        save(context, current)
        version.value++
        return !existing // true = 已收藏, false = 已取消
    }

    fun remove(context: Context, sourceKey: String, vodId: String) {
        val current = load(context).toMutableList()
        current.removeAll { it.sourceKey == sourceKey && it.vodId == vodId }
        save(context, current)
        version.value++
    }

    private fun save(context: Context, items: List<VodFavoriteItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY, json.encodeToString(ListSerializer(VodFavoriteItem.serializer()), items)).apply()
    }
}
