package top.yogiczy.mytv.tv.ui.screen.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import top.yogiczy.mytv.tv.ui.utils.clickableNoIndication
import top.yogiczy.mytv.tv.ui.screen.components.appControlContainer
import top.yogiczy.mytv.tv.ui.screen.components.AppPill
import top.yogiczy.mytv.tv.ui.screen.components.appControlContent
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
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
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
    fun touchControls() { lastInteractionAt = System.currentTimeMillis() }
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

    // ===== 播放控件配色（深色半透明，亮画面上也可读）=====
    val ctrlBg = Color.Black.copy(alpha = 0.55f)          // 常态底
    val ctrlBgFocused = MaterialTheme.colorScheme.primary // 获焦底
    val ctrlBgDisabled = Color.Black.copy(alpha = 0.25f)  // 禁用底
    val ctrlText = Color.White                            // 常态文字
    val ctrlTextDisabled = Color.White.copy(alpha = 0.4f) // 禁用文字

    // 创建 ExoPlayer（大缓冲 LoadControl：快进/拖拽不易掏空缓冲）
    val player = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15000,
                /* maxBufferMs = */ 60000,
                /* bufferForPlaybackMs = */ 2500,
                /* bufferForPlaybackAfterRebufferMs = */ 5000,
            )
            .build()
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build()
    }
    // 缓存数据源工厂：点播流统一走 SimpleCache（LRU 1GB + 定时过期清理）
    val cacheDsFactory = remember {
        VodVideoCache.cleanExpired()
        CacheDataSource.Factory()
            .setCache(VodVideoCache.get(context))
            .setUpstreamDataSourceFactory(
                DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0")
                    .setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(20000)
            )
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
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
            VodVideoCache.release()
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

    // 退出播放（返回详情页）时：立即暂停 + 保存进度 + 恢复横屏
    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            player.pause()
            if (player.currentPosition > 5000) {
                val ms = mergedSources.getOrNull(selectedLineIdx)
                VodHistoryManager.updatePosition(
                    context, ms?.sourceKey ?: sourceKey, ms?.vodId ?: vodId,
                    epIdx, player.currentPosition,
                )
            }
            activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
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
            var isBuffering by remember { mutableStateOf(false) }
            var playerPositionMs by remember { mutableLongStateOf(0L) }
            var playerDurationMs by remember { mutableLongStateOf(0L) }
            var playerBufferedMs by remember { mutableLongStateOf(0L) }
            val coroutineScope = rememberCoroutineScope()
            DisposableEffect(player) {
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        isBuffering = state == Player.STATE_BUFFERING
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

            // 倍速状态：放在全屏播放层级，控制层 5 秒自动隐藏不会重置倍速
            val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)
            var speedIdx by remember { mutableIntStateOf(2) }
            // 应用倍速
            LaunchedEffect(speedIdx) {
                runCatching { player.setPlaybackSpeed(speedOptions[speedIdx]) }
            }
            // 换集恢复 1.0x
            LaunchedEffect(currentEp?.url) {
                speedIdx = 2
            }

            // 进度刷新 (500ms)：播放/暂停均轮询（缓存条实时刷新）；拖拽中不覆盖预览位置
            var scrubPreviewMs by remember { mutableStateOf<Long?>(null) }
            LaunchedEffect(currentEp?.url, scrubPreviewMs) {
                while (true) {
                    if (scrubPreviewMs == null) {
                        playerPositionMs = player.currentPosition.coerceAtLeast(0L)
                    }
                    playerDurationMs = player.duration.let { if (it > 0) it else 0L }
                    playerBufferedMs = player.bufferedPosition.coerceAtLeast(0L)
                    delay(500)
                }
            }

            var preparedUrl by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(currentEp?.url) {
                if (currentEp != null && currentEp.url.isNotBlank()) {
                    // 同一集退出又返回：直接续播，保留已加载缓存与播放位置
                    if (preparedUrl == currentEp.url) {
                        player.playWhenReady = true
                        return@LaunchedEffect
                    }
                    retryCount = 0
                    playerError = null
                    // 恢复播放进度
                    val ms2 = mergedSources.getOrNull(selectedLineIdx)
                    val realSk = ms2?.sourceKey ?: sourceKey
                    val realVid = ms2?.vodId ?: vodId
                    val savedPos = VodHistoryManager.getPosition(context, realSk, realVid, epIdx)
                    if (currentEp.url.contains("m3u8", ignoreCase = true)) {
                        val hlsSource = HlsMediaSource.Factory(cacheDsFactory)
                            .createMediaSource(MediaItem.fromUri(currentEp.url))
                        player.setMediaSource(hlsSource)
                    } else {
                        val source = DefaultMediaSourceFactory(cacheDsFactory)
                            .createMediaSource(MediaItem.fromUri(currentEp.url))
                        player.setMediaSource(source)
                    }
                    if (savedPos > 5000) player.seekTo(savedPos)
                    player.prepare()
                    player.playWhenReady = true
                    preparedUrl = currentEp.url
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
                                    .background(appControlContainer(retryFocused))
                                    
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

            // 缓冲中覆盖层（seek 到未缓存区间 / 起播 / 卡顿时提示，深色半透明）
            if (isBuffering && playerError == null && retryCount == 0) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        top.yogiczy.mytv.tv.ui.material.CircularProgressIndicator()
                        Spacer(Modifier.height(10.dp))
                        Text("缓冲中...", color = Color.White, fontSize = 14.sp)
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
            // 自动隐藏: 5 秒无操作即隐藏；剧集列表打开时不隐藏
            LaunchedEffect(controllerVisible, showEpList, lastInteractionAt) {
                if (controllerVisible && !showEpList) {
                    kotlinx.coroutines.delay(5000)
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
                            // 关掉原生控制器：它会吞掉触摸事件，导致我们的遮罩收不到点击；
                            // 且原生控制条与自写控件重叠。
                            useController = false
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
                            // 关键：PlayerView 是原生 View，会先于 Compose 消费触摸，
                            // 导致我们的遮罩层/按钮收不到点击。这里让它不处理触摸，
                            // 所有手势交给上层 Compose。
                            setOnTouchListener { _, _ -> false }
                            playerViewRef.value = this
                        }
                    },
                )

                // ===== 常驻全透明遮罩层 =====
                // 范围：上/左/右到屏幕边缘，**底部留空**（不遮挡播放器底部那条栏）。
                // 作用：
                //   · 控件显示中 → 点控件外面（落在遮罩上）→ 立即隐藏
                //   · 控件已隐藏 → 点遮罩 → 唤出控件并重置 5 秒计时
                // 这一层始终存在（不随 controllerVisible 移除），避免点击层被重新创建导致收不到触摸。
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.84f)
                        .clickableNoIndication {
                            if (controllerVisible) {
                                showEpList = false
                                controllerVisible = false
                            } else {
                                touchControls()
                                controllerVisible = true
                            }
                        }
                )

                // 覆盖层
                if (controllerVisible) {
                    // 竖屏适配: 尺寸和间距随方向变化
                    val btnH = if (isPortraitMode) 104.dp else 40.dp
                    val btnFs = if (isPortraitMode) 36.sp else 14.sp
                    val epOffset = if (isPortraitMode) 420.dp else 240.dp
                    val epFs = if (isPortraitMode) 32.sp else 13.sp
                    val epPadV = if (isPortraitMode) 20.dp else 6.dp
                    val epPadH = if (isPortraitMode) 28.dp else 10.dp

                    // 顶部 / 底部渐变暗色 scrim：保证控件在亮画面上可读
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(if (isPortraitMode) 160.dp else 90.dp)
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(if (isPortraitMode) 220.dp else 130.dp)
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                )
                            )
                    )

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
                                .background(if (backFocused) ctrlBgFocused else ctrlBg)
                                
                                .clickable { touchControls(); isPlaying = false }
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
                                    .background(if (rotFocused) ctrlBgFocused else ctrlBg)
                                    
                                    .clickable {
                                        touchControls()
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
                                    color = Color.White,
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
                                    .background(if (ratFocused) ctrlBgFocused else ctrlBg)
                                    
                                    .clickable { touchControls(); resizeMode = (resizeMode + 1) % 3 }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    when (resizeMode) {
                                        1 -> "裁剪"
                                        2 -> "拉伸"
                                        else -> "适应"
                                    },
                                    color = Color.White,
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
                                    .background(if (favFocused) ctrlBgFocused else ctrlBg)
                                    
                                    .clickable {
                                        touchControls()
                                        favState = VodFavoriteManager.toggle(context, VodFavoriteItem(
                                            sourceKey = sourceKey, vodId = vodId, vodName = vodName, vodPic = vodPic,
                                        ))
                                    }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (favState) "已收藏" else "收藏", color = Color.White, fontSize = btnFs)
                            }

                            // 剧集列表
                            var infoFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .height(btnH)
                                    .onFocusChanged { infoFocused = it.isFocused }
                                    .focusable()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (infoFocused) ctrlBgFocused else ctrlBg)
                                    
                                    .clickable { touchControls(); showEpList = !showEpList }
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

                    // ===== 居中控制面板 =====
                    // 原生控制器已关闭，这里不再需要不透明底框，按钮直接浮在画面上
                    val playFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
                    LaunchedEffect(controllerVisible) {
                        if (controllerVisible) {
                            runCatching { playFocusRequester.requestFocus() }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.86f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerChip(
                            text = "◀ 上一集",
                            enabled = epIdx > 0,
                            container = ctrlBg, containerFocused = ctrlBgFocused,
                            containerDisabled = ctrlBgDisabled,
                            content = ctrlText, contentDisabled = ctrlTextDisabled,
                            onClick = { touchControls(); epIdx-- },
                        )

                        PlayerChip(
                            text = "⏪ 10秒",
                            container = ctrlBg, containerFocused = ctrlBgFocused,
                            content = ctrlText,
                            onClick = {
                                touchControls()
                                player.seekTo((player.currentPosition - 10000).coerceAtLeast(0L))
                            },
                        )

                        PlayerChip(
                            modifier = Modifier.width(132.dp),
                            container = ctrlBg, containerFocused = ctrlBgFocused,
                            content = ctrlText,
                            text = if (isPlayerPlaying) "⏸ 暂停" else "▶ 播放",
                            focusRequester = playFocusRequester,
                            onClick = {
                                touchControls()
                                if (player.isPlaying) player.pause() else player.play()
                            },
                        )

                        PlayerChip(
                            text = "10秒 ⏩",
                            container = ctrlBg, containerFocused = ctrlBgFocused,
                            content = ctrlText,
                            onClick = {
                                touchControls()
                                val dur = player.duration
                                val target = player.currentPosition + 10000
                                player.seekTo(if (dur > 0) target.coerceAtMost(dur) else target)
                            },
                        )

                        PlayerChip(
                            text = "下一集 ▶",
                            enabled = epIdx < epCount - 1,
                            container = ctrlBg, containerFocused = ctrlBgFocused,
                            containerDisabled = ctrlBgDisabled,
                            content = ctrlText, contentDisabled = ctrlTextDisabled,
                            onClick = { touchControls(); epIdx++ },
                        )
                    }
                    // ===== 右下角：倍速调节（触摸点按循环 / 遥控 OK 循环，左右键微调）=====
                    val speedText = if (speedOptions[speedIdx] == speedOptions[speedIdx].toInt().toFloat()) {
                        "倍速 ${speedOptions[speedIdx].toInt()}.0x"
                    } else {
                        "倍速 ${speedOptions[speedIdx]}x"
                    }
                    PlayerSpeedChip(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                end = if (isPortraitMode) 40.dp else 24.dp,
                                bottom = if (isPortraitMode) 120.dp else 76.dp,
                            ),
                        text = speedText,
                        container = ctrlBg, containerFocused = ctrlBgFocused,
                        content = ctrlText,
                        onCycle = {
                            touchControls()
                            speedIdx = (speedIdx + 1) % speedOptions.size
                        },
                        onStep = { delta ->
                            touchControls()
                            speedIdx = (speedIdx + delta).coerceIn(0, speedOptions.lastIndex)
                        },
                    )

                    // ===== 底部：可拖拽进度条（触屏拖拽/点按 + 遥控左右步进长按加速）=====
                    if (playerDurationMs > 0) {
                        VodSeekBar(
                            positionMs = scrubPreviewMs ?: playerPositionMs,
                            bufferedMs = playerBufferedMs,
                            durationMs = playerDurationMs,
                            isPortrait = isPortraitMode,
                            onScrubStart = {
                                touchControls()
                                scrubPreviewMs = playerPositionMs
                            },
                            onScrub = { target ->
                                touchControls()
                                scrubPreviewMs = target.coerceIn(0L, playerDurationMs)
                            },
                            onScrubEnd = { target ->
                                touchControls()
                                scrubPreviewMs = null
                                player.seekTo(target)
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(
                                    start = if (isPortraitMode) 40.dp else 24.dp,
                                    end = if (isPortraitMode) 40.dp else 24.dp,
                                    bottom = if (isPortraitMode) 40.dp else 20.dp,
                                ),
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
                            .background(Color.Black.copy(alpha = 0.75f)),
                    ) {
                        androidx.compose.foundation.lazy.LazyColumn(
                            state = epListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(ms.episodes.size) { i ->
                                val ep = ms.episodes[i]
                                val isCurrent = i == epIdx
                                // 与设置页同款行：常态 onSurface 10%、聚焦反色、无描边
                                EpRow(
                                    text = ep.name,
                                    selected = isCurrent,
                                    fontSize = if (isPortraitMode) 24.sp else 15.sp,
                                    containerFocused = ctrlBgFocused,
                                    content = Color.White,
                                    onClick = { touchControls(); epIdx = i; showEpList = false },
                                )
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
                    // 返回 + 收藏：与设置页同款胶囊按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppPill(
                            text = "← 返回",
                            onClick = { player.release(); onBack() },
                        )

                        var favState by remember { mutableStateOf(VodFavoriteManager.isFavorite(context, sourceKey, vodId)) }
                        AppPill(
                            text = if (favState) "已收藏" else "收藏",
                            selected = favState,
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
                                            if (srcFocused) MaterialTheme.colorScheme.onSurface else if (i == selectedLineIdx) MaterialTheme.colorScheme.primary.copy(alpha = 0.30f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                                        )
                                        
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
                                        if (epFocused) MaterialTheme.colorScheme.onSurface else if (i == epIdx) MaterialTheme.colorScheme.primary.copy(alpha = 0.30f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                                    )
                                    
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
                        .background(appControlContainer(backFocused))
                        
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
                        .background(appControlContainer(retryFocused))
                        
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

/**
 * 播放控制按钮：深色半透明常态底 + 主题色获焦底，亮画面上可读。
 */
@Composable
private fun PlayerChip(
    modifier: Modifier = Modifier,
    text: String,
    enabled: Boolean = true,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    container: Color = Color.Black.copy(alpha = 0.55f),
    containerFocused: Color = MaterialTheme.colorScheme.primary,
    containerDisabled: Color = Color.Black.copy(alpha = 0.25f),
    content: Color = Color.White,
    contentDisabled: Color = Color.White.copy(alpha = 0.4f),
    onClick: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }

    val containerColor = when {
        !enabled -> containerDisabled
        focused -> containerFocused
        else -> container
    }
    val contentColor = when {
        !enabled -> contentDisabled
        else -> content
    }

    Box(
        modifier = modifier
            .height(46.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .focusable()
            .clip(MaterialTheme.shapes.small)
            .background(containerColor)
            .clickable(enabled = enabled) { onClick() }
            .handleKeyEvents(onSelect = if (enabled) onClick else null)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = contentColor, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** 剧集行：与设置页 ListItem 一致的观感（常态 onSurface 10%、聚焦反色、无描边） */
@Composable
private fun EpRow(
    text: String,
    selected: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit,
    containerFocused: Color = MaterialTheme.colorScheme.primary,
    content: Color = Color.White,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val container = when {
        focused -> containerFocused
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        else -> Color.White.copy(alpha = 0.10f)
    }
    val contentColor = when {
        selected && !focused -> MaterialTheme.colorScheme.primary
        else -> content
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .focusable()
            .clip(MaterialTheme.shapes.small)
            .background(container)
            .clickable { onClick() }
            .handleKeyEvents(onSelect = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(
            text,
            color = contentColor,
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 倍速控件：触摸点按循环切换；遥控 OK 循环、左右键微调。
 * 与其它自写控件同款观感（常态 onSurface 12%、聚焦反色、无描边）。
 */
@Composable
private fun PlayerSpeedChip(
    modifier: Modifier = Modifier,
    text: String,
    container: Color = Color.Black.copy(alpha = 0.55f),
    containerFocused: Color = MaterialTheme.colorScheme.primary,
    content: Color = Color.White,
    onCycle: () -> Unit,
    onStep: (Int) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .focusable()
            .clip(MaterialTheme.shapes.small)
            .background(if (focused) containerFocused else container)
            .clickable { onCycle() }
            .handleKeyEvents(
                onSelect = onCycle,
                onLeft = { onStep(-1) },
                onRight = { onStep(1) },
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = content,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}


/**
 * 点播可拖拽进度条。
 * - 触屏：点按跳转 / 按住拖拽，拖动中只更新预览（由调用方持有的 scrub 状态），松手才 seek；
 * - 遥控：左/右键 ±10s，长按持续步进并逐步加速（10s→30s→60s）；
 * - 展示：轨道（白25%）/ 已缓冲（白45%）/ 已播放（主题色）/ 滑块圆点 + 时间文本。
 */
@Composable
private fun VodSeekBar(
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    isPortrait: Boolean,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (durationMs <= 0) return
    var focused by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    // 当前拖拽/步进中的预览位置（内部镜像，回调同步给外层）
    var previewMs by remember { mutableLongStateOf(positionMs) }
    // 非拖拽时直接展示实时进度，避免组合期写状态
    val shownMs = if (scrubbing) previewMs else positionMs

    val scope = rememberCoroutineScope()
    var stepJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun clamp(v: Long) = v.coerceIn(0L, durationMs)

    fun commitSeek() {
        val target = clamp(previewMs)
        scrubbing = false
        onScrubEnd(target)
    }

    fun stepOnce(dir: Int) {
        if (!scrubbing) {
            scrubbing = true
            previewMs = positionMs
            onScrubStart()
        }
        previewMs = clamp(previewMs + dir * 10_000L)
        onScrub(previewMs)
    }

    fun startHold(dir: Int) {
        stepOnce(dir)
        stepJob?.cancel()
        stepJob = scope.launch {
            delay(400) // 长按时延
            var interval = 220L
            var step = 10_000L
            var ticks = 0
            while (true) {
                ticks++
                // 逐步加速：10s → 30s → 60s
                if (ticks == 10) { step = 30_000L; interval = 200L }
                if (ticks == 25) { step = 60_000L; interval = 180L }
                previewMs = clamp(previewMs + dir * step)
                onScrub(previewMs)
                if (previewMs == 0L || previewMs == durationMs) break
                delay(interval)
            }
        }
    }

    fun stopHold() {
        stepJob?.cancel()
        stepJob = null
        if (scrubbing) commitSeek()
    }

    val trackH = if (isPortrait) 10.dp else 5.dp
    val thumbD = if (focused || scrubbing) (if (isPortrait) 26.dp else 14.dp) else (if (isPortrait) 16.dp else 9.dp)
    val fs = if (isPortrait) 24.sp else 13.sp
    val progress = (shownMs.toFloat() / durationMs).coerceIn(0f, 1f)
    val buffered = (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f)

    Column(modifier = modifier) {
        // 时间文本：拖拽中显示预览位置
        Text(
            "${fmtTime(shownMs)} / ${fmtTime(durationMs)}",
            color = Color.White,
            fontSize = fs,
            modifier = Modifier.padding(bottom = if (isPortrait) 10.dp else 5.dp),
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isPortrait) 48.dp else 28.dp)
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .pointerInput(durationMs) {
                    detectTapGestures(
                        onPress = { offset ->
                            val frac = (offset.x / size.width).coerceIn(0f, 1f)
                            previewMs = (frac * durationMs).toLong()
                            scrubbing = true
                            onScrubStart()
                            onScrub(previewMs)
                            val released = runCatching { tryAwaitRelease() }.getOrDefault(true)
                            if (released) commitSeek() else { scrubbing = false }
                        },
                    )
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            scrubbing = true
                            previewMs = positionMs
                            onScrubStart()
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val w = size.width
                            if (w > 0) {
                                previewMs = clamp(previewMs + (dragAmount / w * durationMs).toLong())
                                onScrub(previewMs)
                            }
                        },
                        onDragEnd = { commitSeek() },
                        onDragCancel = { scrubbing = false },
                    )
                }
                .onPreviewKeyEvent { ev ->
                    val native = ev.nativeKeyEvent
                    when (native.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            val dir = if (native.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                            when (native.action) {
                                android.view.KeyEvent.ACTION_DOWN -> {
                                    if (native.repeatCount == 0) startHold(dir)
                                    true
                                }
                                android.view.KeyEvent.ACTION_UP -> { stopHold(); true }
                                else -> true
                            }
                        }
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                        android.view.KeyEvent.KEYCODE_ENTER -> {
                            if (native.action == android.view.KeyEvent.ACTION_UP && scrubbing) {
                                stopHold()
                            }
                            true
                        }
                        else -> false
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val trackW = maxWidth
            // 轨道
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(trackH)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.25f))
            )
            // 已缓冲（明显灰色，与轨道白25%区分）
            Box(
                Modifier
                    .width(trackW * buffered)
                    .height(trackH)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF9E9E9E).copy(alpha = 0.9f))
            )
            // 已播放
            Box(
                Modifier
                    .width(trackW * progress)
                    .height(trackH)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary)
            )
            // 滑块
            Box(
                Modifier
                    .offset(x = trackW * progress - thumbD / 2)
                    .width(thumbD)
                    .height(thumbD)
                    .clip(RoundedCornerShape(50))
                    .background(if (focused || scrubbing) MaterialTheme.colorScheme.primary else Color.White)
            )
        }
    }
}
