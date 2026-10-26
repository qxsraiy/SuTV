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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.screen.components.AppButton
import top.yogiczy.mytv.tv.ui.screen.components.AppThemeWrapper
import top.yogiczy.mytv.tv.ui.theme.colors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.focusRequester
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
import kotlinx.coroutines.launch
import top.yogiczy.mytv.tv.ui.screen.vod.VodFavoriteItem
import top.yogiczy.mytv.tv.ui.screen.vod.VodFavoriteManager

/** 毫秒 → mm:ss / h:mm:ss */
private fun fmtTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

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
    // 播放控制层状态 (提到顶层, 供 BackHandler 使用)
    var controllerVisible by remember { mutableStateOf(true) }
    var showEpList by remember { mutableStateOf(false) }
    // 最后一次交互时间 — 任何按键都会刷新, 用于重置隐藏倒计时
    var lastInteractionAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
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

    // 生命周期: 后台暂停播放, 防止音频泄漏
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // 保存播放进度 (用当前选中的源和集数, 不是固定的0)
            if (player.currentPosition > 5000) {
                val ms = state.mergedSources.getOrNull(selectedLineIdx)
                val realSk = ms?.sourceKey ?: sourceKey
                val realVid = ms?.vodId ?: vodId
                VodHistoryManager.updatePosition(context, realSk, realVid, epIdx, player.currentPosition)
            }
            player.release()
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            vm.clearSearch()
        }
    }

    // 返回键分层处理:
    // 1) 选集列表开着 → 关闭列表并隐藏控制层
    // 2) 控制层可见 → 隐藏控制层
    // 3) 正在全屏播放 → 退出播放回到详情页
    // 4) 否则 → 退出详情页
    androidx.activity.compose.BackHandler(enabled = true) {
        when {
            isPlaying && showEpList -> {
                showEpList = false
                controllerVisible = false
            }
            isPlaying && controllerVisible -> controllerVisible = false
            isPlaying -> isPlaying = false
            else -> onBack()
        }
    }

    AppThemeWrapper {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        if (isPlaying && detail != null && mergedSources.isNotEmpty()) {
            // ===== 全屏播放 =====
            val ms = mergedSources.getOrNull(selectedLineIdx)
            val currentEp = ms?.episodes?.getOrNull(epIdx)
            val epCount = ms?.episodes?.size ?: 0

            // 定期保存播放进度 (每10秒)
            LaunchedEffect(currentEp?.url) {
                if (currentEp != null) {
                    while (true) {
                        kotlinx.coroutines.delay(10000)
                        val ms2 = mergedSources.getOrNull(selectedLineIdx)
                        val realSk = ms2?.sourceKey ?: sourceKey
                        val realVid = ms2?.vodId ?: vodId
                        VodHistoryManager.updatePosition(context, realSk, realVid, epIdx, player.currentPosition)
                    }
                }
            }

            // 自动连播 + 播放出错自动重试
            val latestEpIdx = rememberUpdatedState(epIdx)
            val latestEpCount = rememberUpdatedState(epCount)
            var retryCount by remember { mutableIntStateOf(0) }
            var playerError by remember { mutableStateOf<String?>(null) }
            // 播放状态 (用于 Compose 播放/暂停按钮, 遥控可操作)
            var isPlayerPlaying by remember { mutableStateOf(false) }
            var playerPositionMs by remember { mutableLongStateOf(0L) }
            var playerDurationMs by remember { mutableLongStateOf(0L) }
            val coroutineScope = rememberCoroutineScope()
            DisposableEffect(player) {
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
                            if (latestEpIdx.value < latestEpCount.value - 1) {
                                epIdx = latestEpIdx.value + 1
                            }
                        }
                        // 播放成功后重置重试计数
                        if (state == Player.STATE_READY) {
                            retryCount = 0
                            playerError = null
                        }
                    }
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlayerPlaying = playing
                    }
                    override fun onPlayerErrorChanged(error: androidx.media3.common.PlaybackException?) {
                        if (error != null && retryCount < 3) {
                            val delayMs = (1000L shl retryCount) // 1s, 2s, 4s
                            retryCount++
                            coroutineScope.launch {
                                kotlinx.coroutines.delay(delayMs)
                                player.prepare()
                                player.playWhenReady = true
                            }
                        } else if (error != null) {
                            playerError = error.message ?: "播放错误"
                        }
                    }
                }
                player.addListener(listener)
                onDispose { player.removeListener(listener) }
            }

            // 进度刷新 (1s)：仅在播放时轮询，暂停/挂起时停止，避免无意义重组
            LaunchedEffect(currentEp?.url, isPlayerPlaying) {
                playerPositionMs = player.currentPosition.coerceAtLeast(0L)
                playerDurationMs = player.duration.let { if (it > 0) it else 0L }
                while (isPlayerPlaying) {
                    kotlinx.coroutines.delay(1000)
                    playerPositionMs = player.currentPosition.coerceAtLeast(0L)
                    playerDurationMs = player.duration.let { if (it > 0) it else 0L }
                }
            }

            LaunchedEffect(currentEp?.url) {
                if (currentEp != null && currentEp.url.isNotBlank()) {
                    retryCount = 0
                    playerError = null
                    // 恢复播放进度
                    val ms2 = mergedSources.getOrNull(selectedLineIdx)
                    val realSk = ms2?.sourceKey ?: sourceKey
                    val realVid = ms2?.vodId ?: vodId
                    val savedPos = VodHistoryManager.getPosition(context, realSk, realVid, epIdx)
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
                    if (savedPos > 5000) player.seekTo(savedPos)
                    player.prepare()
                    player.playWhenReady = true
                }
            }

            // 重连中 / 错误覆盖层
            if (playerError != null || retryCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (playerError != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("播放失败", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp)
                            Text(playerError ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                            Spacer(Modifier.height(12.dp))
                            var retryFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .onFocusChanged { retryFocused = it.isFocused }
                                    .focusable()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (retryFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colors.surfaceContainerHigh)
                                    .border(if (retryFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                    .clickable {
                                        retryCount = 0
                                        playerError = null
                                        player.prepare()
                                        player.playWhenReady = true
                                    }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                            ) {
                                Text("重试", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                            }
                        }
                    } else {
                        Text("重连中... (第${retryCount}次)", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                    }
                }
            }

            var isPortraitMode by remember { mutableStateOf(false) }
            // 画面比例: 0=适应 1=裁剪 2=拉伸
            var resizeMode by remember { mutableIntStateOf(
                top.yogiczy.mytv.tv.ui.utils.Configs.vodPlayerResizeMode.coerceIn(0, 2)
            ) }
            val epListState = androidx.compose.foundation.lazy.rememberLazyListState(epIdx)
            val playerViewRef = remember { mutableStateOf<PlayerView?>(null) }

            // 同步画面比例到 PlayerView
            LaunchedEffect(resizeMode) {
                playerViewRef.value?.resizeMode = when (resizeMode) {
                    1 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    2 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                top.yogiczy.mytv.tv.ui.utils.Configs.vodPlayerResizeMode = resizeMode
            }

            // controllerVisible变化时同步PlayerView控制器
            LaunchedEffect(controllerVisible) {
                val pv = playerViewRef.value
                if (pv != null) {
                    if (controllerVisible) pv.showController()
                    else pv.hideController()
                }
            }
            // 自动隐藏: 选集列表打开时不隐藏; 每次交互(按键)后重新计时 10 秒
            LaunchedEffect(controllerVisible, showEpList, lastInteractionAt) {
                if (controllerVisible && !showEpList) {
                    kotlinx.coroutines.delay(10000)
                    controllerVisible = false
                }
            }
            // 换集后显示
            LaunchedEffect(currentEp?.url) {
                showEpList = false
                controllerVisible = true
                lastInteractionAt = System.currentTimeMillis()
            }

            Box(modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                // 任何按键都刷新交互时间, 重置自动隐藏倒计时
                .onPreviewKeyEvent { ev ->
                    if (ev.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                        ev.nativeKeyEvent.repeatCount == 0
                    ) {
                        lastInteractionAt = System.currentTimeMillis()
                    }
                    false
                }) {
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
                            this.resizeMode = when (resizeMode) {
                                1 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                2 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                                else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                            // 黑底: 避免留边时露出应用灰色背景
                            setShutterBackgroundColor(android.graphics.Color.BLACK)
                            setBackgroundColor(android.graphics.Color.BLACK)
                            // 关键: 禁止 PlayerView 抢焦点, 否则遥控方向键被内置控制器吞掉,
                            // Compose 覆盖层按钮永远无法获得焦点 (触屏不受影响)
                            isFocusable = false
                            isFocusableInTouchMode = false
                            descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
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
                    val btnH = if (isPortraitMode) 104.dp else 40.dp
                    val btnFs = if (isPortraitMode) 36.sp else 14.sp
                    val epOffset = if (isPortraitMode) 420.dp else 240.dp
                    val epFs = if (isPortraitMode) 32.sp else 13.sp
                    val epPadV = if (isPortraitMode) 20.dp else 6.dp
                    val epPadH = if (isPortraitMode) 28.dp else 10.dp

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
                                .background(if (backFocused) MaterialTheme.colors.surfaceContainerHigh.copy(alpha = 0.9f) else MaterialTheme.colors.surfaceContainer.copy(alpha = 0.8f))
                                .border(if (backFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                .clickable { isPlaying = false }
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("← 返回", color = MaterialTheme.colorScheme.onSurface, fontSize = btnFs)
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
                                    .background(if (rotFocused) MaterialTheme.colors.surfaceContainerHigh.copy(alpha = 0.9f) else MaterialTheme.colors.surfaceContainer.copy(alpha = 0.8f))
                                    .border(if (rotFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
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
                                    color = if (isPortraitMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontSize = btnFs,
                                )
                            }

                            // 画面比例切换: 适应 → 裁剪 → 拉伸
                            var ratFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .height(btnH)
                                    .onFocusChanged { ratFocused = it.isFocused }
                                    .focusable()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (ratFocused) MaterialTheme.colors.surfaceContainerHigh.copy(alpha = 0.9f) else MaterialTheme.colors.surfaceContainer.copy(alpha = 0.8f))
                                    .border(if (ratFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                    .clickable { resizeMode = (resizeMode + 1) % 3 }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    when (resizeMode) {
                                        1 -> "裁剪"
                                        2 -> "拉伸"
                                        else -> "适应"
                                    },
                                    color = if (resizeMode != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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
                                    .background(if (favFocused) MaterialTheme.colors.surfaceContainerHigh.copy(alpha = 0.9f) else MaterialTheme.colors.surfaceContainer.copy(alpha = 0.8f))
                                    .border(if (favFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                    .clickable {
                                        favState = VodFavoriteManager.toggle(context, VodFavoriteItem(
                                            sourceKey = sourceKey, vodId = vodId, vodName = vodName, vodPic = vodPic,
                                        ))
                                    }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (favState) "★已收藏" else "☆收藏", color = if (favState) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontSize = btnFs)
                            }

                            // 剧集列表
                            var infoFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .height(btnH)
                                    .onFocusChanged { infoFocused = it.isFocused }
                                    .focusable()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (infoFocused) MaterialTheme.colors.surfaceContainerHigh.copy(alpha = 0.9f) else MaterialTheme.colors.surfaceContainer.copy(alpha = 0.8f))
                                    .border(if (infoFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                    .clickable { showEpList = !showEpList }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (isPortraitMode) "剧集▾" else "${detail.vod_name} · ${currentEp?.name ?: ""} ▾",
                                    color = MaterialTheme.colorScheme.onSurface, fontSize = btnFs, fontWeight = FontWeight.Medium,
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
                            .background(if (prevFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colors.surfaceContainer.copy(alpha = 0.8f))
                            .clickable { if (epIdx > 0) epIdx-- }
                            .padding(horizontal = epPadH, vertical = epPadV),
                    ) { Text("◀上一集", color = if (epIdx > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = epFs) }

                    // 屏幕正中间偏右: 下一集
                    var nextFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = epOffset)
                            .onFocusChanged { nextFocused = it.isFocused }
                            .focusable()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (nextFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colors.surfaceContainer.copy(alpha = 0.8f))
                            .clickable { if (epIdx < epCount - 1) epIdx++ }
                            .padding(horizontal = epPadH, vertical = epPadV),
                    ) { Text("下一集▶", color = if (epIdx < epCount - 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = epFs) }

                    // 屏幕正中: 快退 | 播放暂停 | 快进 (遥控可操作)
                    val playFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
                    // 控件显示时自动聚焦到播放键, 保证遥控器立刻可用
                    LaunchedEffect(controllerVisible) {
                        if (controllerVisible) {
                            runCatching { playFocusRequester.requestFocus() }
                        }
                    }
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 快退10秒
                        var rwFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .onFocusChanged { rwFocused = it.isFocused }
                                .focusable()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (rwFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colors.surfaceContainer.copy(alpha = 0.8f))
                                .border(if (rwFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                .clickable {
                                    player.seekTo((player.currentPosition - 10000).coerceAtLeast(0L))
                                }
                                .padding(horizontal = epPadH, vertical = epPadV),
                        ) { Text("⏪10秒", color = MaterialTheme.colorScheme.onSurface, fontSize = epFs) }

                        // 播放 / 暂停
                        var ppFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .focusRequester(playFocusRequester)
                                .onFocusChanged { ppFocused = it.isFocused }
                                .focusable()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (ppFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colors.surfaceContainer.copy(alpha = 0.8f))
                                .border(if (ppFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                .clickable {
                                    if (player.isPlaying) player.pause() else player.play()
                                }
                                .padding(horizontal = epPadH, vertical = epPadV),
                        ) {
                            Text(
                                if (isPlayerPlaying) "⏸暂停" else "▶播放",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = epFs,
                            )
                        }

                        // 快进10秒
                        var fwFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .onFocusChanged { fwFocused = it.isFocused }
                                .focusable()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (fwFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colors.surfaceContainer.copy(alpha = 0.8f))
                                .border(if (fwFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                .clickable {
                                    val dur = player.duration
                                    val target = player.currentPosition + 10000
                                    player.seekTo(if (dur > 0) target.coerceAtMost(dur) else target)
                                }
                                .padding(horizontal = epPadH, vertical = epPadV),
                        ) { Text("10秒⏩", color = MaterialTheme.colorScheme.onSurface, fontSize = epFs) }
                    }

                    // 底部进度显示 (非焦点, 仅展示)
                    if (playerDurationMs > 0) {
                        Text(
                            "${fmtTime(playerPositionMs)} / ${fmtTime(playerDurationMs)}",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = if (isPortraitMode) 24.sp else 13.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = if (isPortraitMode) 40.dp else 20.dp),
                        )
                    }
                }

                // 右侧剧集列表
                if (showEpList && ms != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(if (isPortraitMode) 0.6f else 0.75f)
                            .width(if (isPortraitMode) 240.dp else 200.dp)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.88f)),
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
                                                epFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                                isCurrent -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .border(if (isCurrent) 1.dp else 0.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(6.dp))
                                        .clickable { epIdx = i; showEpList = false }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        ep.name,
                                        color = if (isCurrent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                        fontSize = if (isPortraitMode) 28.sp else 13.sp,
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
                    // 返回 + 收藏 (同一行)：统一使用 AppButton 组件，聚焦样式全 App 一致
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        AppButton(
                            modifier = Modifier.widthIn(max = 140.dp),
                            text = "← 返回",
                            compact = true,
                            fillMaxWidth = false,
                            onClick = { player.release(); onBack() },
                        )

                        // 收藏按钮
                        var favState by remember { mutableStateOf(VodFavoriteManager.isFavorite(context, sourceKey, vodId)) }
                        AppButton(
                            modifier = Modifier.widthIn(max = 160.dp),
                            text = if (favState) "★ 已收藏" else "☆ 收藏",
                            compact = true,
                            fillMaxWidth = false,
                            onClick = {
                                favState = VodFavoriteManager.toggle(context, VodFavoriteItem(
                                    sourceKey = sourceKey, vodId = vodId, vodName = vodName, vodPic = vodPic,
                                ))
                            },
                        )
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
                            Text(detail.vod_name.ifBlank { vodName }, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(2.dp))
                            Text("${detail.type_name}  ${detail.vod_year}  ${detail.vod_area}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
                            if (detail.vod_score.isNotBlank()) {
                                Text("评分: ${detail.vod_score}", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                            }
                            if (detail.vod_remarks.isNotBlank()) {
                                Text("备注: ${detail.vod_remarks}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            if (detail.vod_director.isNotBlank()) {
                                Text("导演: ${detail.vod_director}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (detail.vod_actor.isNotBlank()) {
                                Text("演员: ${detail.vod_actor}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                detail.vod_content.replace("<[^>]+>".toRegex(), "").trim(),
                                color = MaterialTheme.colorScheme.onSurface,
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
                        Text("换源 (${mergedSources.size}条线路)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(bottom = 2.dp))
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
                                            if (srcFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                            else if (i == selectedLineIdx) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                            else MaterialTheme.colors.surfaceContainerHigh
                                        )
                                        .border(if (srcFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                        .clickable { selectedLineIdx = i; epIdx = 0 }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                ) {
                                    Text(ms.label, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                    }

                    // 选集网格
                    val eps = mergedSources.getOrNull(selectedLineIdx)?.episodes ?: emptyList()
                    Text("选集 (${eps.size}集)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(bottom = 2.dp))
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
                                        if (epFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                        else if (i == epIdx) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                        else MaterialTheme.colors.surfaceContainerHigh
                                    )
                                    .border(if (epFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                    .clickable {
                                        epIdx = i
                                        isPlaying = true
                                    }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    eps[i].name.ifBlank { "第${i+1}集" },
                                    color = MaterialTheme.colorScheme.onSurface,
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
                Text("加载中...", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp)
            }
        } else {
            // 加载失败: 显示错误信息 + 重试按钮
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("加载失败", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp)
                if (!state.error.isNullOrBlank()) {
                    Text(state.error ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(12.dp))
                // 返回按钮
                var backFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .onFocusChanged { backFocused = it.isFocused }
                        .focusable()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (backFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.30f) else MaterialTheme.colors.surfaceContainerHigh)
                        .border(if (backFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        .clickable { onBack() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("← 返回", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                }
                Spacer(Modifier.height(8.dp))
                // 重试按钮
                var retryFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .onFocusChanged { retryFocused = it.isFocused }
                        .focusable()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (retryFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colors.surfaceContainerHigh)
                        .border(if (retryFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        .clickable { vm.loadDetail(sourceKey, vodId) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text("重试", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                }
            }
        }
    }
    }
}
