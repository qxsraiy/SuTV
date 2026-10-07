package top.yogiczy.mytv.tv.ui.screen.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.core.data.entities.vod.Episode
import top.yogiczy.mytv.core.data.entities.vod.PlaySource
import top.yogiczy.mytv.core.data.entities.vod.VodCategory
import top.yogiczy.mytv.core.data.entities.vod.VodDetail
import top.yogiczy.mytv.core.data.entities.vod.VodItem
import top.yogiczy.mytv.core.data.entities.vod.VodItemList
import top.yogiczy.mytv.core.data.entities.vod.VodSource
import top.yogiczy.mytv.core.data.entities.vod.VodSourceList
import top.yogiczy.mytv.core.data.utils.Globals
import okhttp3.Request

data class VodUiState(
    val sources: VodSourceList = VodSourceList(),
    val currentSource: VodSource = VodSource(),
    val categories: List<VodCategory> = emptyList(),
    val items: List<VodItem> = emptyList(),
    val currentTypeId: String = "",
    val detail: VodDetail? = null,
    val mergedSources: List<MergedPlaySource> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val page: Int = 1,
    val pageCount: Int = 1,
    val searchResults: List<VodItem> = emptyList(),
)

// 跨源合并的播放线路
data class MergedPlaySource(
    val sourceKey: String,
    val sourceName: String,
    val vodId: String,
    val lineName: String,    // 线路名 (feifan, ffm3u8等)
    val episodes: List<Episode>,
) {
    val label get() = "$sourceName/$lineName"
}

class VodViewModel : ViewModel() {
    private val serverUrl = "http://localhost:10481"

    private val _state = androidx.compose.runtime.mutableStateOf(VodUiState())
    val state: androidx.compose.runtime.State<VodUiState> = _state

    // 共享OkHttpClient, 避免每次创建
    private val httpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    fun loadSources() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(loading = true)
            try {
                val json = apiGet("$serverUrl/vod/sources")
                val sources = Globals.json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(VodSource.serializer()), json
                )
                val sourceList = VodSourceList(sources)
                val default = sourceList.find { it.default } ?: sourceList.firstOrNull() ?: VodSource()
                _state.value = _state.value.copy(
                    sources = sourceList, currentSource = default, loading = false
                )
                loadCategories(default.key)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun selectSource(source: VodSource) {
        _state.value = _state.value.copy(currentSource = source, categories = emptyList(), items = emptyList())
        loadCategories(source.key)
    }

    fun loadCategories(sourceKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = apiGet("$serverUrl/vod/categories?source=$sourceKey")
                val cats = Globals.json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(VodCategory.serializer()), json
                )
                // 只显示子分类(有视频)，父分类不直接返回结果
                val leafCats = cats.filter { it.type_pid != 0 }
                _state.value = _state.value.copy(categories = leafCats)
                // 默认加载最新(不带分类)
                loadList(sourceKey, "", 1)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    fun loadList(sourceKey: String, typeId: String, page: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(loading = true)
            try {
                val url = "$serverUrl/vod/list?source=$sourceKey" +
                    (if (typeId.isNotEmpty()) "&t=$typeId" else "") +
                    "&page=$page"
                val json = apiGet(url)
                val resp = Globals.json.decodeFromString(VodItemList.serializer(), json)
                _state.value = _state.value.copy(
                    items = if (page == 1) resp.list else _state.value.items + resp.list,
                    currentTypeId = typeId,
                    page = resp.page, pageCount = resp.pagecount, loading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message)
            }
        }
    }

    // 并发搜索 — 直接调CMS API, 绕过HttpServer
    // 当前搜索Job — 新搜索前取消旧Job, 防止状态竞态
    private var searchJob: Job? = null

    // 当前搜索批次偏移 (每次刷新+5, 循环)
    private var searchBatchOffset = 0
    // 上次搜索关键词
    private var lastSearchWd = ""

    fun searchMulti(wd: String) {
        // 取消上一次搜索, 防止并发状态竞态
        searchJob?.cancel()
        lastSearchWd = wd
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val encoded = java.net.URLEncoder.encode(wd, "UTF-8")
                val url = "$serverUrl/vod/search-multi?wd=$encoded&offset=$searchBatchOffset"
                val json = apiGet(url)
                val resp = Globals.json.decodeFromString(VodItemList.serializer(), json)
                ensureActive()
                _state.value = _state.value.copy(
                    searchResults = resp.list,
                    loading = false,
                    error = if (resp.list.isEmpty()) "未找到结果, 点刷新换源重试" else null,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = "搜索失败: ${e.message}")
            }
        }
    }

    // 刷新搜索: 换5个源重新搜
    fun refreshSearch() {
        if (lastSearchWd.isBlank()) return
        searchBatchOffset += 5
        searchMulti(lastSearchWd)
    }

    fun search(wd: String, sourceKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // 只改搜索状态, 不动源/分类/列表 — 防止搜索失败后全空
            _state.value = _state.value.copy(loading = true)
            try {
                val json = apiGet("$serverUrl/vod/search?source=$sourceKey&wd=${java.net.URLEncoder.encode(wd, "UTF-8")}")
                val resp = Globals.json.decodeFromString(VodItemList.serializer(), json)
                _state.value = _state.value.copy(
                    searchResults = resp.list, loading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun clearSearch() {
        _state.value = _state.value.copy(searchResults = emptyList())
    }

    fun loadDetail(sourceKey: String, id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(loading = true, detail = null, mergedSources = emptyList())
            try {
                val json = apiGet("$serverUrl/vod/detail?source=$sourceKey&id=$id")
                val detail = Globals.json.decodeFromString(VodDetail.serializer(), json)
                val merged = detail.play_sources.map { ps ->
                    MergedPlaySource(sourceKey, detail.source, id, ps.name, ps.episodes)
                }
                _state.value = _state.value.copy(detail = detail, mergedSources = merged, loading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun clearDetail() {
        _state.value = _state.value.copy(detail = null)
    }

    private suspend fun apiGet(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val body = resp.body?.string() ?: throw Exception("空响应")
            if (!body.trimStart().startsWith("{")) throw Exception("非JSON响应")
            body
        }
    }

    override fun onCleared() {
        searchJob?.cancel()
    }
}
