package top.yogiczy.mytv.tv.ui.screen.vod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.Channel
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
        .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
        .build()
        .also { top.yogiczy.mytv.tv.utlis.NetworkMonitor.trackPool(it.connectionPool) }

    init {
        loadSources()
    }

    fun loadSources() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(loading = true)
            try {
                val json = apiGet("$serverUrl/vod/sources")
                val sources = Globals.json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(VodSource.serializer()), json
                )
                val sourceList = VodSourceList(sources)
                _state.value = _state.value.copy(sources = sourceList, loading = false)
                // 自动换源: 先试默认源, 失败则并发试其他源, 谁先用谁
                loadCategoriesWithFallback(sourceList)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun selectSource(source: VodSource) {
        _state.value = _state.value.copy(currentSource = source, categories = emptyList(), items = emptyList())
        loadCategories(source.key)
    }

    // 自动换源加载分类: 默认源失败时并发尝试其他源
    private suspend fun loadCategoriesWithFallback(sourceList: VodSourceList) {
        val allSources = sourceList.toList()
        if (allSources.isEmpty()) {
            _state.value = _state.value.copy(loading = false, error = "无可用采集源")
            return
        }

        val defaultSource = allSources.find { it.default } ?: allSources.first()
        _state.value = _state.value.copy(loading = true, error = null)

        // 1. 先试默认源 (快速路径, 5s超时)
        val defaultResult = withTimeoutOrNull(5000) {
            val json = apiGet("$serverUrl/vod/categories?source=${defaultSource.key}")
            val cats = Globals.json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(VodCategory.serializer()), json
            )
            cats.filter { it.type_pid != 0 }
        }

        if (defaultResult != null) {
            _state.value = _state.value.copy(
                currentSource = defaultSource, categories = defaultResult, loading = false,
            )
            loadList(defaultSource.key, "", 1)
            return
        }

        // 2. 默认源失败, 并发试其他源
        loadCategoriesConcurrent(allSources, defaultSource)
    }

    // 并发尝试所有源, 谁先成功用谁
    private suspend fun loadCategoriesConcurrent(
        allSources: List<VodSource>,
        excludeSource: VodSource,
    ) {
        val others = allSources.filter { it.key != excludeSource.key }
        if (others.isEmpty()) {
            _state.value = _state.value.copy(loading = false, error = "所有源均无法连接")
            return
        }

        val resultChannel = Channel<Pair<VodSource, List<VodCategory>>>(others.size)
        var foundSource: VodSource? = null
        var foundCats: List<VodCategory>? = null

        coroutineScope {
            val jobs = others.map { source ->
                async(Dispatchers.IO) {
                    try {
                        withTimeoutOrNull(4000) {
                            val json = apiGet("$serverUrl/vod/categories?source=${source.key}")
                            val cats = Globals.json.decodeFromString(
                                kotlinx.serialization.builtins.ListSerializer(VodCategory.serializer()), json
                            )
                            source to cats.filter { it.type_pid != 0 }
                        }?.let { resultChannel.send(it) }
                    } catch (_: Exception) { }
                }
            }

            val result = withTimeoutOrNull(6000) { resultChannel.receive() }
            jobs.forEach { it.cancel() }
            resultChannel.close()

            if (result != null) {
                foundSource = result.first
                foundCats = result.second
            }
        }

        if (foundSource != null && foundCats != null) {
            _state.value = _state.value.copy(
                currentSource = foundSource!!, categories = foundCats!!, loading = false, error = null,
            )
            loadList(foundSource!!.key, "", 1)
        } else {
            _state.value = _state.value.copy(loading = false, error = "所有源均无法连接, 请检查网络后重试")
        }
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

    // 当前搜索Job — 新搜索前取消旧Job, 防止状态竞态
    private var searchJob: Job? = null

    // 当前搜索批次偏移 (每次刷新+5, 循环)
    private var searchBatchOffset = 0
    // 上次搜索关键词
    private var lastSearchWd = ""

    fun searchMulti(wd: String) {
        searchJob?.cancel()
        lastSearchWd = wd
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            // 清空旧结果, 开始搜索
            _state.value = _state.value.copy(loading = true, error = null, searchResults = emptyList())

            val all = top.yogiczy.mytv.core.data.utils.Constants.VOD_SOURCE_LIST +
                top.yogiczy.mytv.tv.ui.utils.Configs.vodSourceList
            if (all.isEmpty()) {
                _state.value = _state.value.copy(loading = false, error = "无可用搜索源")
                return@launch
            }

            val size = all.size
            val batch = (0 until 5).map { i -> all[(searchBatchOffset + i) % size] }
            val encoded = java.net.URLEncoder.encode(wd, "UTF-8")

            // Channel 收集结果: 谁先回来谁先显示
            val resultChannel = Channel<List<top.yogiczy.mytv.core.data.entities.vod.VodItem>>(5)

            // 并发搜索5个源, 每个源独立超时, 完成后发到 Channel
            val jobs = batch.map { source ->
                async(Dispatchers.IO) {
                    val results = try {
                        withTimeoutOrNull(5000) {
                            val url = "$serverUrl/vod/search?source=${source.key}&wd=$encoded"
                            val json = apiGet(url)
                            val resp = Globals.json.decodeFromString(VodItemList.serializer(), json)
                            resp.list.map { it.copy(sourceKey = source.key, sourceName = source.name) }
                        } ?: emptyList()
                    } catch (_: Exception) { emptyList() }
                    resultChannel.send(results)
                }
            }

            // 按完成顺序逐个收结果, 每到一个就更新UI
            val seenNames = mutableSetOf<String>()
            var completed = 0
            val total = jobs.size

            while (completed < total) {
                val results = withTimeoutOrNull(6000) {
                    resultChannel.receive()
                } ?: break
                completed++
                val newItems = results.filter { seenNames.add(it.vod_name) }
                if (newItems.isNotEmpty()) {
                    ensureActive()
                    val current = _state.value.searchResults
                    _state.value = _state.value.copy(
                        searchResults = current + newItems,
                        error = null,
                    )
                }
            }

            jobs.forEach { it.cancel() }
            resultChannel.close()

            ensureActive()
            val final = _state.value.searchResults
            _state.value = _state.value.copy(
                loading = false,
                error = if (final.isEmpty()) "未找到结果, 点刷新换源重试" else null,
            )
        }
    }

    // 刷新搜索: 换5个源重新搜
    fun refreshSearch() {
        if (lastSearchWd.isBlank()) return
        searchBatchOffset += 5
        searchMulti(lastSearchWd)
    }

    // 立即取消搜索 (进入详情页时调用)
    fun cancelSearch() {
        searchJob?.cancel()
        searchJob = null
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
            resp.body?.string() ?: throw Exception("空响应")
        }
    }

    override fun onCleared() {
        searchJob?.cancel()
    }
}
