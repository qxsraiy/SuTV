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
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
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

    // 初始加载
    LaunchedEffect(Unit) {
        if (state.sources.isEmpty()) vm.loadSources()
    }

    // 观看历史 — 版本号变化时自动刷新
    val historyVersion by VodHistoryManager.version
    var historyItems by remember(historyVersion) { mutableStateOf(VodHistoryManager.load(context)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部栏
            VodTopBar(
                vm = vm,
                state = state,
                onBack = onBack,
                onPlayVod = onPlayVod,
            )

            // 分类导航
            if (state.categories.isNotEmpty()) {
                CategoryTabs(
                    categories = state.categories,
                    sourceKey = state.currentSource.key,
                    onSelect = { cat ->
                        vm.loadList(state.currentSource.key, cat.type_id.toString(), 1)
                    },
                )
            }

            // 主体: 左1/5最近观看 + 右4/5影片网格
            val showHistory = historyItems.isNotEmpty()
            Row(modifier = Modifier.fillMaxSize()) {
                // 左侧: 最近观看 (1/5宽, 纯文字列表, 最多5行)
                if (showHistory) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color(0xFF161616))
                            .padding(6.dp),
                    ) {
                        Text(
                            "最近观看",
                            color = Color(0xFFAAAAAA),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        val histList = historyItems.take(5)
                        histList.forEach { hist ->
                            var focused by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp)
                                    .onFocusChanged { focused = it.isFocused }
                                    .focusable()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (focused) Color(0xFF444466) else Color.Transparent)
                                    .border(if (focused) 2.dp else 0.dp, Color(0xFF8888FF), RoundedCornerShape(6.dp))
                                    .clickable {
                                        onPlayVod(hist.sourceKey, hist.vodId, hist.vodName, hist.vodPic, hist.epIdx)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    hist.vodName,
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .weight(1f)
                                        .basicMarquee(),
                                )
                                Text(
                                    "第${hist.epIdx + 1}集",
                                    color = Color(0xFF888888),
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                // 右侧: 影片网格 (4/5宽, 自适应列数, 至少2行完整)
                val gridWeight = if (showHistory) 4f else 1f
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(gridWeight)
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

                    val displayItems = if (state.searchResults.isNotEmpty()) state.searchResults else state.items
                    val isLoading = state.loading && displayItems.isEmpty()

                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("搜索中...", color = Color.White, fontSize = 18.sp)
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
                            // 加载更多
                            if (state.searchResults.isEmpty() && state.page < state.pageCount) {
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
                            Text(state.error ?: "", color = Color(0xFFAAAAAA), fontSize = 16.sp)
                            Spacer(Modifier.height(12.dp))
                            var retryFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .onFocusChanged { retryFocused = it.isFocused }
                                    .focusable()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (retryFocused) Color(0xFF6666BB) else Color(0xFF444477))
                                    .border(if (retryFocused) 2.dp else 0.dp, Color(0xFF8888FF), RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (state.sources.isEmpty()) vm.loadSources()
                                        else vm.loadList(state.currentSource.key, state.currentTypeId, 1)
                                    }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                            ) {
                                Text("重试", color = Color.White, fontSize = 14.sp)
                            }
                        }
                    } else if (displayItems.isEmpty() && !state.loading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("暂无数据", color = Color(0xFFAAAAAA), fontSize = 16.sp)
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
    onBack: () -> Unit,
    onPlayVod: (String, String, String, String, Int) -> Unit,
) {
    var searchText by remember { mutableStateOf("") }
    var showSources by remember { mutableStateOf(false) }
    var backFocused by remember { mutableStateOf(false) }
    var sourceFocused by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    var btnFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 返回
        Box(
            modifier = Modifier
                .onFocusChanged { backFocused = it.isFocused }
                .focusable()
                .clip(RoundedCornerShape(8.dp))
                .background(if (backFocused) Color(0xFF444466) else Color(0xFF333333))
                .border(if (backFocused) 2.dp else 0.dp, Color(0xFF8888FF), RoundedCornerShape(8.dp))
                .clickable { onBack() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("← 返回", color = Color.White, fontSize = 16.sp)
        }
        Spacer(Modifier.width(12.dp))

        // 采集源选择
        Box(
            modifier = Modifier
                .onFocusChanged { sourceFocused = it.isFocused }
                .focusable()
                .clip(RoundedCornerShape(8.dp))
                .background(if (sourceFocused) Color(0xFF444466) else Color(0xFF333333))
                .border(if (sourceFocused) 2.dp else 0.dp, Color(0xFF8888FF), RoundedCornerShape(8.dp))
                .clickable { showSources = !showSources }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("源: ${state.currentSource.name} ▾", color = Color.White, fontSize = 14.sp)
        }
        Spacer(Modifier.width(12.dp))

        // 搜索框
        val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        val searchFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(Unit) {
            searchFocusRequester.requestFocus()
        }
        BasicTextField(
            value = searchText,
            onValueChange = { searchText = it },
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { searchFocused = it.isFocused }
                .focusRequester(searchFocusRequester)
                .focusable()
                .clip(RoundedCornerShape(8.dp))
                .background(if (searchFocused) Color(0xFF444466) else Color(0xFF333333))
                .border(if (searchFocused) 2.dp else 0.dp, Color(0xFF8888FF), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            singleLine = true,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
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
        Spacer(Modifier.width(8.dp))

        // 刷新搜索按钮 (换5个源重新搜, loading时旋转动画)
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
                .onFocusChanged { refreshFocused = it.isFocused }
                .focusable()
                .clip(RoundedCornerShape(8.dp))
                .background(if (refreshFocused) Color(0xFF6666BB) else Color(0xFF444477))
                .border(if (refreshFocused) 2.dp else 0.dp, Color(0xFF8888FF), RoundedCornerShape(8.dp))
                .clickable { vm.refreshSearch() }
                .padding(8.dp),
        ) {
            Icon(
                Icons.Outlined.Update,
                contentDescription = "刷新",
                tint = Color.White,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        rotationZ = if (state.loading) rotation else 0f
                    },
            )
        }
        Spacer(Modifier.width(8.dp))

        // 搜索按钮
        Box(
            modifier = Modifier
                .onFocusChanged { btnFocused = it.isFocused }
                .focusable()
                .clip(RoundedCornerShape(8.dp))
                .background(if (btnFocused) Color(0xFF6666BB) else Color(0xFF5555AA))
                .border(if (btnFocused) 2.dp else 0.dp, Color(0xFF8888FF), RoundedCornerShape(8.dp))
                .clickable {
                    if (searchText.isNotEmpty()) {
                        keyboardController?.hide()
                        vm.searchMulti(searchText)
                    }
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("搜索", color = Color.White, fontSize = 14.sp)
        }
    }

    // 源下拉列表
    if (showSources) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF222222))
                .padding(8.dp),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.sources) { source ->
                    var focused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .onFocusChanged { focused = it.isFocused }
                            .focusable()
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (focused) Color(0xFF5555AA)
                                else if (source.key == state.currentSource.key) Color(0xFF444466)
                                else Color(0xFF333333)
                            )
                            .border(if (focused) 2.dp else 0.dp, Color(0xFF8888FF), RoundedCornerShape(6.dp))
                            .clickable {
                                vm.selectSource(source)
                                showSources = false
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(source.name, color = Color.White, fontSize = 13.sp)
                    }
                }
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
            Box(
                modifier = Modifier
                    .onFocusChanged { focused = it.isFocused }
                    .focusable()
                    .scale(if (focused) 1.1f else 1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (focused) Color(0xFF5555AA) else Color(0xFF333333))
                    .border(if (focused) 2.dp else 0.dp, Color(0xFF8888FF), RoundedCornerShape(6.dp))
                    .clickable { onSelect(cat) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(cat.type_name, color = Color.White, fontSize = 13.sp)
            }
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
            .background(Color(0xFF222222))
            .border(if (focused) 3.dp else 0.dp, Color(0xFF8888FF), RoundedCornerShape(8.dp))
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
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
        )
        if (item.vod_remarks.isNotEmpty()) {
            Text(item.vod_remarks, color = Color(0xFF888888), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (item.sourceName.isNotEmpty()) {
            Text(item.sourceName, color = Color(0xFF6688AA), fontSize = 8.sp, maxLines = 1)
        }
    }
}
