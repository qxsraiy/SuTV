package top.yogiczy.mytv.tv.ui.screen.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import top.yogiczy.mytv.tv.ui.screen.vod.VodFavoriteItem
import top.yogiczy.mytv.tv.ui.screen.vod.VodFavoriteManager

@Composable
fun VodDetailScreen(
    vm: VodViewModel,
    sourceKey: String,
    vodId: String,
    vodName: String,
    vodPic: String,
    initialEpIdx: Int,
    onBack: () -> Unit,
) {
    val state by vm.state
    var epIdx by remember { mutableIntStateOf(initialEpIdx) }
    var isPlaying by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val detail = state.detail
    val mergedSources = state.mergedSources

    // 选含 m3u8 URL 的线路
    var selectedLineIdx by remember(mergedSources) {
        mutableIntStateOf(
            mergedSources.indexOfFirst { ms ->
                ms.episodes.any { e -> e.url.contains("m3u8", ignoreCase = true) }
            }.let { if (it >= 0) it else 0 }
        )
    }

    // 播放时保存观看历史
    LaunchedEffect(isPlaying, epIdx) {
        if (isPlaying) {
            val ms = mergedSources.getOrNull(selectedLineIdx)
            val realSourceKey = ms?.sourceKey ?: sourceKey
            val realVodId = ms?.vodId ?: vodId
            VodHistoryManager.add(context, VodHistoryItem(realSourceKey, realVodId, vodName, vodPic, epIdx, System.currentTimeMillis()))
        }
    }

    // 加载详情 (内部自动触发跨源搜索)
    LaunchedEffect(sourceKey, vodId) {
        vm.loadDetail(sourceKey, vodId)
    }

    // 创建 ExoPlayer
    val player = remember { ExoPlayer.Builder(context).build() }
    val activity = remember {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) break
            ctx = ctx.baseContext
        }
        ctx as? android.app.Activity
    }
    DisposableEffect(Unit) {
        onDispose {
            player.release()
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
    ) {
        if (isPlaying && detail != null && mergedSources.isNotEmpty()) {
            // ===== 全屏播放 =====
            val ms = mergedSources.getOrNull(selectedLineIdx)
            val currentEp = ms?.episodes?.getOrNull(epIdx)
            val epCount = ms?.episodes?.size ?: 0

            // 自动连播: 播放完毕自动下一集
            val latestEpIdx = rememberUpdatedState(epIdx)
            val latestEpCount = rememberUpdatedState(epCount)
            DisposableEffect(player) {
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
                            if (latestEpIdx.value < latestEpCount.value - 1) {
                                epIdx = latestEpIdx.value + 1
                            }
                        }
                    }
                }
                player.addListener(listener)
                onDispose { player.removeListener(listener) }
            }

            LaunchedEffect(currentEp?.url) {
                if (currentEp != null && currentEp.url.isNotBlank()) {
                    if (currentEp.url.contains("m3u8", ignoreCase = true)) {
                        val dataSourceFactory = DefaultHttpDataSource.Factory()
                            .setUserAgent("Mozilla/5.0")
                            .setConnectTimeoutMs(15000)
                            .setReadTimeoutMs(20000)
                        val hlsSource = HlsMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(currentEp.url))
                        player.setMediaSource(hlsSource)
                    } else {
                        player.setMediaItem(MediaItem.fromUri(currentEp.url))
                    }
                    player.prepare()
                    player.playWhenReady = true
                }
            }

            var controllerVisible by remember { mutableStateOf(true) }
            var showEpList by remember { mutableStateOf(false) }
            var isPortraitMode by remember { mutableStateOf(false) }
            val epListState = androidx.compose.foundation.lazy.rememberLazyListState(epIdx)
            val playerViewRef = remember { mutableStateOf<PlayerView?>(null) }

            // controllerVisible变化时同步PlayerView控制器
            LaunchedEffect(controllerVisible) {
                val pv = playerViewRef.value
                if (pv != null) {
                    if (controllerVisible) pv.showController()
                    else pv.hideController()
                }
            }
            // 自动隐藏
            LaunchedEffect(controllerVisible, showEpList) {
                if (controllerVisible && !showEpList) {
                    kotlinx.coroutines.delay(5000)
                    controllerVisible = false
                }
            }
            // 换集后显示
            LaunchedEffect(currentEp?.url) {
                showEpList = false
                controllerVisible = true
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // PlayerView (内置控制器: 快退/暂停/快进/进度条)
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = true
                            setControllerAutoShow(true)
                            setControllerHideOnTouch(false)
                            setControllerShowTimeoutMs(0)
                            setShowPreviousButton(false)
                            setShowNextButton(false)
                            playerViewRef.value = this
                        }
                    },
                )

                // 触摸捕获层: 控件隐藏时点击唤出
                if (!controllerVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { controllerVisible = true }
                    )
                }

                // 覆盖层
                if (controllerVisible) {
                    // 竖屏适配: 尺寸和间距随方向变化
                    val btnH = if (isPortraitMode) 52.dp else 40.dp
                    val btnFs = if (isPortraitMode) 18.sp else 14.sp
                    val epOffset = if (isPortraitMode) 100.dp else 220.dp
                    val epFs = if (isPortraitMode) 16.sp else 13.sp
                    val epPadV = if (isPortraitMode) 10.dp else 6.dp
                    val epPadH = if (isPortraitMode) 14.dp else 10.dp

                    // 仅当剧集列表打开时: 点击外部关闭列表+隐藏控件
                    if (showEpList) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    showEpList = false
                                    controllerVisible = false
                                }
                        )
                    }

                    // 顶部按钮栏: 返回 ——— 竖屏 + 收藏 + 剧集
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(if (isPortraitMode) 12.dp else 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // 左: 返回
                        var backFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .height(btnH)
                                .onFocusChanged { backFocused = it.isFocused }
                                .focusable()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (backFocused) Color(0xAA000000) else Color(0x80000000))
                                .border(if (backFocused) 2.dp else 0.dp, Color(0xFF8888FF), RoundedCornerShape(8.dp))
                                .clickable { isPlaying = false }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("← 返回", color = Color.White, fontSize = btnFs)
                        }

                        // 右: 竖屏 + 收藏 + 剧集
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 竖屏/横屏切换
                            var rotFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .height(btnH)
                                    .onFocusChanged { rotFocused = it.isFocused }
                                    .focusable()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (rotFocused) Color(0xAA000000) else Color(0x80000000))
                                    .border(if (rotFocused) 2.dp else 0.dp, Color(0xFF8888FF), RoundedCornerShape(8.dp))
                                    .clickable {
                                        isPortraitMode = !isPortraitMode
                                        activity?.let { act ->
                                            act.requestedOrientation = if (isPortraitMode)
                                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                            else
                                                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        }
                                    }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (isPortraitMode) "横屏" else "竖屏",
                                    color = if (isPortraitMode) Color(0xFFFFD700) else Color.White,
                                    fontSize = btnFs,
                                )
                            }

                            // 收藏
                            var favState by remember { mutableStateOf(VodFavoriteManager.isFavorite(context, sourceKey, vodId)) }
                            var favFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .height(btnH)
                                    .onFocusChanged { favFocused = it.isFocused }
                                    .focusable()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (favFocused) Color(0xAA000000) else Color(0x80000000))
                                    .border(if (favFocused) 2.dp else 0.dp, Color(0xFF8888FF), RoundedCornerShape(8.dp))
                                    .clickable {
                                        favState = VodFavoriteManager.toggle(context, VodFavoriteItem(
                                            sourceKey = sourceKey, vodId = vodId, vodName = vodName, vodPic = vodPic,
                                        ))
                                    }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (favState) "★已收藏" else "☆收藏", color = if (favState) Color(0xFFFFD700) else Color.White, fontSize = btnFs)
                            }

                            // 剧集列表
                            var infoFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .height(btnH)
                                    .onFocusChanged { infoFocused = it.isFocused }
                                    .focusable()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (infoFocused) Color(0xAA000000) else Color(0x80000000))
                                    .border(if (infoFocused) 2.dp else 0.dp, Color(0xFF8888FF), RoundedCornerShape(8.dp))
                                    .clickable { showEpList = !showEpList }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (isPortraitMode) "剧集▾" else "${detail.vod_name} · ${currentEp?.name ?: ""} ▾",
                                    color = Color.White, fontSize = btnFs, fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                )
                            }
                        }
                    }

                    // 屏幕正中间偏左: 上一集
                    var prevFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = -epOffset)
                            .onFocusChanged { prevFocused = it.isFocused }
                            .focusable()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (prevFocused) Color(0xFF4466AA) else Color(0x80000000))
                            .clickable { if (epIdx > 0) epIdx-- }
                            .padding(horizontal = epPadH, vertical = epPadV),
                    ) { Text("◀上一集", color = if (epIdx > 0) Color.White else Color(0xFF666666), fontSize = epFs) }

                    // 屏幕正中间偏右: 下一集
                    var nextFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = epOffset)
                            .onFocusChanged { nextFocused = it.isFocused }
                            .focusable()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (nextFocused) Color(0xFF4466AA) else Color(0x80000000))
                            .clickable { if (epIdx < epCount - 1) epIdx++ }
                            .padding(horizontal = epPadH, vertical = epPadV),
                    ) { Text("下一集▶", color = if (epIdx < epCount - 1) Color.White else Color(0xFF666666), fontSize = epFs) }
                }

                // 右侧剧集列表
                if (showEpList && ms != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(if (isPortraitMode) 0.65f else 0.75f)
                            .width(if (isPortraitMode) 160.dp else 200.dp)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xDD000000)),
                    ) {
                        androidx.compose.foundation.lazy.LazyColumn(
                            state = epListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(4.dp),
                        ) {
                            items(ms.episodes.size) { i ->
                                val ep = ms.episodes[i]
                                var epFocused by remember { mutableStateOf(false) }
                                val isCurrent = i == epIdx
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 1.dp)
                                        .onFocusChanged { epFocused = it.isFocused }
                                        .focusable()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            when {
                                                epFocused -> Color(0xFF4466AA)
                                                isCurrent -> Color(0x44AA0000)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .border(if (isCurrent) 1.dp else 0.dp, Color(0xFFFF4444), RoundedCornerShape(6.dp))
                                        .clickable { epIdx = i; showEpList = false }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        ep.name,
                                        color = if (isCurrent) Color(0xFFFF6666) else Color.White,
                                        fontSize = if (isPortraitMode) 15.sp else 13.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (detail != null) {
            // ===== 详情页: 上半图片+简介, 下半换源+选集 =====
            Column(modifier = Modifier.fillMaxSize()) {
                // ---- 上半部分: 返回 + 封面 + 信息 (屏幕一半高度) ----
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.5f)
                        .padding(8.dp),
                ) {
                    // 返回 + 收藏 (同一行)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        var backFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .onFocusChanged { backFocused = it.isFocused }
                                .focusable()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (backFocused) Color(0xFF444466) else Color(0xFF333333))
                                .border(if (backFocused) 2.dp else 0.dp, Color(0xFF8888FF), RoundedCornerShape(8.dp))
                                .clickable { player.release(); onBack() }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text("← 返回", color = Color.White, fontSize = 14.sp)
                        }

                        // 收藏按钮
                        var favState by remember { mutableStateOf(VodFavoriteManager.isFavorite(context, sourceKey, vodId)) }
                        var favFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .onFocusChanged { favFocused = it.isFocused }
                                .focusable()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (favFocused) Color(0xFF444466) else Color(0xFF333333))
                                .border(if (favFocused) 2.dp else 0.dp, Color(0xFF8888FF), RoundedCornerShape(8.dp))
                                .clickable {
                                    favState = VodFavoriteManager.toggle(context, VodFavoriteItem(
                                        sourceKey = sourceKey, vodId = vodId, vodName = vodName, vodPic = vodPic,
                                    ))
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(if (favState) "★ 已收藏" else "☆ 收藏", color = if (favState) Color(0xFFFFD700) else Color.White, fontSize = 14.sp)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    ) {
                        // 封面: 屏幕一半宽度
                        AsyncImage(
                            model = detail.vod_pic.ifBlank { vodPic },
                            contentDescription = detail.vod_name,
                            modifier = Modifier
                                .width(120.dp)
                                .aspectRatio(3f / 4f)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(detail.vod_name.ifBlank { vodName }, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(2.dp))
                            Text("${detail.type_name}  ${detail.vod_year}  ${detail.vod_area}", color = Color(0xFF888888), fontSize = 12.sp, maxLines = 1)
                            if (detail.vod_score.isNotBlank()) {
                                Text("评分: ${detail.vod_score}", color = Color(0xFFAAAA44), fontSize = 12.sp)
                            }
                            if (detail.vod_remarks.isNotBlank()) {
                                Text("备注: ${detail.vod_remarks}", color = Color(0xFF888888), fontSize = 12.sp)
                            }
                            if (detail.vod_director.isNotBlank()) {
                                Text("导演: ${detail.vod_director}", color = Color(0xFFAAAAAA), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (detail.vod_actor.isNotBlank()) {
                                Text("演员: ${detail.vod_actor}", color = Color(0xFFAAAAAA), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                detail.vod_content.replace("<[^>]+>".toRegex(), "").trim(),
                                color = Color(0xFFCCCCCC),
                                fontSize = 11.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // ---- 下半部分: 换源 + 选集 (屏幕一半高度) ----
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                ) {
                    // 换源标签 (横向滚动)
                    if (mergedSources.isNotEmpty()) {
                        Text("换源 (${mergedSources.size}条线路)", color = Color(0xFF888888), fontSize = 12.sp, modifier = Modifier.padding(bottom = 2.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 4.dp),
                        ) {
                            items(mergedSources.size) { i ->
                                val ms = mergedSources[i]
                                var srcFocused by remember { mutableStateOf(false) }
                                Box(
                                    modifier = Modifier
                                        .onFocusChanged { srcFocused = it.isFocused }
                                        .focusable()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (srcFocused) Color(0xFF6666BB)
                                            else if (i == selectedLineIdx) Color(0xFF5555AA)
                                            else Color(0xFF333333)
                                        )
                                        .border(if (srcFocused) 2.dp else 0.dp, Color(0xFF8888FF), RoundedCornerShape(6.dp))
                                        .clickable { selectedLineIdx = i; epIdx = 0 }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                ) {
                                    Text(ms.label, color = Color.White, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                    }

                    // 选集网格
                    val eps = mergedSources.getOrNull(selectedLineIdx)?.episodes ?: emptyList()
                    Text("选集 (${eps.size}集)", color = Color(0xFF888888), fontSize = 12.sp, modifier = Modifier.padding(bottom = 2.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(70.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(eps.size) { i ->
                            var epFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .onFocusChanged { epFocused = it.isFocused }
                                    .focusable()
                                    .scale(if (epFocused) 1.08f else 1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (epFocused) Color(0xFF6666BB)
                                        else if (i == epIdx) Color(0xFF5555AA)
                                        else Color(0xFF333333)
                                    )
                                    .border(if (epFocused) 2.dp else 0.dp, Color(0xFF8888FF), RoundedCornerShape(6.dp))
                                    .clickable {
                                        epIdx = i
                                        isPlaying = true
                                    }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    eps[i].name.ifBlank { "第${i+1}集" },
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        } else if (state.loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("加载中...", color = Color.White, fontSize = 18.sp)
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text("加载失败", color = Color.White, fontSize = 18.sp)
            }
        }
    }
}
