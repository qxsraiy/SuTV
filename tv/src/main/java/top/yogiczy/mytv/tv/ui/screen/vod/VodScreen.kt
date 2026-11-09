package top.yogiczy.mytv.tv.ui.screen.vod

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Update
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.screen.components.AppThemeWrapper
import top.yogiczy.mytv.tv.ui.screen.components.EmptyState
import top.yogiczy.mytv.tv.ui.screen.components.QrcodePopup
import top.yogiczy.mytv.tv.ui.screen.components.AppPill
import top.yogiczy.mytv.tv.ui.screen.components.AppActionRow
import top.yogiczy.mytv.tv.ui.screen.components.appControlContainer
import top.yogiczy.mytv.tv.ui.screen.components.appControlContent
import top.yogiczy.mytv.tv.ui.screen.components.UiText
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.rememberLazyListState
import top.yogiczy.mytv.tv.ui.theme.colors
import top.yogiczy.mytv.tv.ui.utils.clickableNoIndication
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import top.yogiczy.mytv.core.data.entities.vod.VodItem
import top.yogiczy.mytv.core.data.entities.vod.VodSource

@Composable
fun VodScreen(
    onPlayVod: (sourceKey: String, vodId: String, vodName: String, vodPic: String, epIdx: Int) -> Unit,
    onBack: () -> Unit,
) {
    val vm = androidx.lifecycle.viewmodel.compose.viewModel<VodViewModel>()
    val state by vm.state
    val context = LocalContext.current

    // 搜索模式：激进=全源聚合(隐藏源列表/分类, 首页显示聚合推荐); 保守=当前样子
    var aggressive by remember { mutableStateOf(top.yogiczy.mytv.tv.ui.utils.Configs.vodSearchAggressive) }

    // 初始加载 / 从设置页返回时同步默认源与搜索模式
    LaunchedEffect(Unit) {
        aggressive = top.yogiczy.mytv.tv.ui.utils.Configs.vodSearchAggressive
        if (state.sources.isEmpty()) vm.loadSources()
        else vm.applyConfiguredDefaultSourceIfChanged()
    }

    // 激进模式：进入点播页(未搜索时)加载聚合推荐
    LaunchedEffect(aggressive) {
        if (aggressive && state.searchResults.isEmpty() && state.recommendItems.isEmpty()) {
            vm.loadRecommendations()
        }
    }

    // 观看历史 — 版本号变化时自动刷新

    AppThemeWrapper {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部栏
            VodTopBar(
                vm = vm,
                state = state,
                aggressive = aggressive,
                onBack = onBack,
                onPlayVod = onPlayVod,
            )

            // 分类导航（激进模式隐藏：首页为聚合推荐，分类属于单一源）
            if (!aggressive && state.categories.isNotEmpty()) {
                CategoryTabs(
                    categories = state.categories,
                    sourceKey = state.currentSource.key,
                    onSelect = { cat ->
                        vm.loadList(state.currentSource.key, cat.type_id.toString(), 1)
                    },
                )
            }

            // 主体: 影片网格（历史记录已移到「收藏记录」页面，这里全宽展示）
            Row(modifier = Modifier.fillMaxSize()) {
                // 影片网格（全宽）
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // 根据可用高度计算: 2行完整的最小列宽
                    // 卡片高度 = 图片高 + 文字高(~36dp) + padding(~8dp) ≈ 图片高 + 44dp
                    // 2行 = 2*(图片高+44) + 1*间距(6) ≤ 可用高度
                    // 图片高 ≤ (可用高度 - 6) / 2 - 44
                    // 列宽 = 图片高 * 3/4 (3:4竖版比例)
                    val availH = maxHeight
                    val maxImageH = (availH - 6.dp) / 2f - 44.dp
                    val minColW = (maxImageH * 3f / 4f).coerceAtLeast(80.dp)

                    val displayItems = when {
                        state.searchResults.isNotEmpty() -> state.searchResults
                        aggressive && state.recommendItems.isNotEmpty() -> state.recommendItems
                        else -> state.items
                    }
                    val isLoading = (state.loading || (aggressive && state.recommendLoading)) && displayItems.isEmpty()

                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("搜索中...", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp)
                        }
                    } else if (displayItems.isNotEmpty()) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minColW),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(displayItems) { item ->
                                VodCard(
                                    item = item,
                                    onClick = {
                                        vm.cancelSearch()
                                        val sk = if (item.sourceKey.isNotEmpty()) item.sourceKey else state.currentSource.key
                                        onPlayVod(sk, item.vod_id, item.vod_name, item.vod_pic, 0)
                                    },
                                )
                            }
                            // 加载更多（激进模式推荐视图不分页）
                            if (state.searchResults.isEmpty() && !aggressive && state.page < state.pageCount) {
                                item {
                                    LaunchedEffect(Unit) {
                                        vm.loadList(state.currentSource.key, state.currentTypeId, state.page + 1)
                                    }
                                }
                            }
                        }
                    } else if (state.error != null && displayItems.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(state.error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                            Spacer(Modifier.height(12.dp))
                            var retryFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .onFocusChanged { retryFocused = it.isFocused }
                                    .focusable()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(appControlContainer(retryFocused))
                                    .border(if (retryFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (state.sources.isEmpty()) vm.loadSources()
                                        else vm.loadList(state.currentSource.key, state.currentTypeId, 1)
                                    }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                            ) {
                                Text("重试", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                            }
                        }
                    } else if (displayItems.isEmpty() && !state.loading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyState(fillMaxSize = false, title = UiText.EMPTY_DATA)
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun VodTopBar(
    vm: VodViewModel,
    state: VodUiState,
    aggressive: Boolean,
    onBack: () -> Unit,
    onPlayVod: (String, String, String, String, Int) -> Unit,
) {
    // 搜索框文字持久化：从详情页返回时恢复上次搜索内容（存在 ViewModel 里）
    var searchText by remember { mutableStateOf(vm.lastSearchWdText) }
    var showQrPopup by remember { mutableStateOf(false) }
    // ===== 手机扫码搜索：仅在点播主界面生效 =====
    // 进入点播页启用；进入剧集详情或退出点播页时自动断开（NavHost 会 dispose 本界面）
    DisposableEffect(Unit) {
        VodRemoteSearchBus.setActive(true)
        onDispose { VodRemoteSearchBus.setActive(false) }
    }

    // 手机端提交的关键词 → 立即搜索
    val pendingRemoteQuery by VodRemoteSearchBus.pending.collectAsState()
    LaunchedEffect(pendingRemoteQuery) {
        val q = pendingRemoteQuery ?: return@LaunchedEffect
        VodRemoteSearchBus.consume()
        searchText = q
        vm.searchMulti(q)
    }

    QrcodePopup(
        visibleProvider = { showQrPopup },
        onDismissRequest = { showQrPopup = false },
        text = top.yogiczy.mytv.tv.utlis.HttpServer.serverUrl + "/vod/remote-search",
        description = "手机扫码 · 输入片名 · 电视立即搜索",
    )
    var showSources by remember { mutableStateOf(false) }
    var backFocused by remember { mutableStateOf(false) }
    var sourceFocused by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    var btnFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 返回（设置页同款胶囊）
        AppPill(text = "← 返回", onClick = onBack)

        // 采集源选择（激进模式隐藏：聚合展示/全源搜索，无需关心当前源）
        if (!aggressive) {
            AppPill(
                text = "源: ${state.currentSource.name} ▾",
                selected = showSources,
                onClick = { showSources = !showSources },
            )
        }

        // 搜索框（容器/文字与设置页一致；聚焦反色）
        val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        val searchFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(Unit) {
            searchFocusRequester.requestFocus()
        }
        BasicTextField(
            value = searchText,
            onValueChange = { searchText = it },
            textStyle = TextStyle(
                color = if (searchFocused) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
            ),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { searchFocused = it.isFocused }
                .focusRequester(searchFocusRequester)
                .focusable()
                .clip(MaterialTheme.shapes.small)
                .background(
                    if (searchFocused) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                )
                .padding(horizontal = 14.dp, vertical = 9.dp),
            singleLine = true,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(
                if (searchFocused) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.onSurface
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = {
                    if (searchText.isNotEmpty()) {
                        keyboardController?.hide()
                        vm.searchMulti(searchText)
                    }
                },
            ),
        )

        // 刷新按钮：搜索中→换批源重搜；激进模式→换一批推荐；保守模式→随机换一页片单
        fun onRefresh() {
            when {
                state.searchResults.isNotEmpty() || vm.lastSearchWdText.isNotBlank() -> vm.refreshSearch()
                aggressive -> vm.refreshRecommendations()
                else -> {
                    val nextPage = if (state.pageCount > 1) (1..state.pageCount).random() else 1
                    vm.loadList(state.currentSource.key, state.currentTypeId, nextPage)
                }
            }
        }
        var refreshFocused by remember { mutableStateOf(false) }
        val rotationTransition = rememberInfiniteTransition(label = "refresh")
        val rotation by rotationTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)),
            label = "rotation",
        )
        Box(
            modifier = Modifier
                .onFocusChanged { refreshFocused = it.isFocused || it.hasFocus }
                .focusable()
                .clip(MaterialTheme.shapes.small)
                .background(
                    if (refreshFocused) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                )
                .clickableNoIndication { onRefresh() }
                .handleKeyEvents(onSelect = { onRefresh() })
                .padding(9.dp),
        ) {
            Icon(
                Icons.Outlined.Update,
                contentDescription = "刷新",
                tint = if (refreshFocused) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        rotationZ = if (state.loading) rotation else 0f
                    },
            )
        }

        // 搜索按钮（输入为空 → 弹出扫码搜索）
        AppPill(
            text = "搜索",
            selected = true,
            onClick = {
                if (searchText.isNotEmpty()) {
                    keyboardController?.hide()
                    vm.searchMulti(searchText)
                } else {
                    keyboardController?.hide()
                    showQrPopup = true
                }
            },
        )
    }
    // 源下拉列表（设置页同款行；LazyColumn 支持上下滚动 + 焦点自动跟随）
    if (showSources) {
        val listState = rememberLazyListState()
        LaunchedEffect(showSources) {
            if (showSources) {
                // 打开时把焦点所在行滚到可见区
                val idx = state.sources.indexOfFirst { it.key == state.currentSource.key }
                if (idx >= 0) listState.scrollToItem(idx)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                // 限高并允许滚动：源多时不会再顶出屏幕，遥控上下可移动焦点
                .heightIn(max = 360.dp)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.sources) { source ->
                AppActionRow(
                    title = source.name,
                    supporting = if (source.key == state.currentSource.key) "当前使用" else null,
                    trailing = {
                        if (source.key == state.currentSource.key) {
                            Text("✓", style = MaterialTheme.typography.titleMedium)
                        }
                    },
                    onClick = {
                        vm.selectSource(source)
                        showSources = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CategoryTabs(
    categories: List<top.yogiczy.mytv.core.data.entities.vod.VodCategory>,
    sourceKey: String,
    onSelect: (top.yogiczy.mytv.core.data.entities.vod.VodCategory) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories) { cat ->
            var focused by remember { mutableStateOf(false) }
            AppPill(
                text = cat.type_name,
                style = MaterialTheme.typography.bodyMedium,
                onClick = { onSelect(cat) },
            )
        }
    }
}

@Composable
private fun VodCard(
    item: VodItem,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .scale(if (focused) 1.05f else 1f)
            .clip(RoundedCornerShape(8.dp))
            .background(appControlContainer(focused))
            .border(if (focused) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(4.dp),
    ) {
        // 3:4 竖版长方形比例
        AsyncImage(
            model = item.vod_pic,
            contentDescription = item.vod_name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            item.vod_name,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
        )
        if (item.vod_remarks.isNotEmpty()) {
            Text(item.vod_remarks, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.sourceName.isNotEmpty()) {
            Text(item.sourceName, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), fontSize = 8.sp, maxLines = 1)
        }
    }
}
