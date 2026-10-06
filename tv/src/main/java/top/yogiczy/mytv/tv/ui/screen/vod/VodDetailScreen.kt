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
import androidx.compose.foundation.layout.height
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
    DisposableEffect(Unit) { onDispose { player.release() } }

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
            val playerViewRef = remember { mutableStateOf<PlayerView?>(null) }

            // 换集后显示控制器
            LaunchedEffect(currentEp?.url) {
                kotlinx.coroutines.delay(300)
                playerViewRef.value?.showController()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                val pv = playerViewRef.value
                                if (pv != null) {
                                    if (controllerVisible) pv.hideController()
                                    else pv.showController()
                                }
                            }
                        )
                    }
            ) {

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = true
                            setControllerAutoShow(true)
                            setControllerShowTimeoutMs(5000)
                            setShowPreviousButton(true)
                            setShowNextButton(true)
                            setControllerVisibilityListener(
                                object : PlayerView.ControllerVisibilityListener {
                                    override fun onVisibilityChanged(visibility: Int) {
                                        controllerVisible = visibility == android.view.View.VISIBLE
                                    }
                                }
                            )
                            playerViewRef.value = this
                        }
                    },
                    update = { view ->
                        // 接管上一集/下一集按钮
                        val prevId = view.resources.getIdentifier("exo_prev", "id", "androidx.media3.ui")
                        val nextId = view.resources.getIdentifier("exo_next", "id", "androidx.media3.ui")
                        view.findViewById<android.view.View>(prevId)?.setOnClickListener {
                            if (epIdx > 0) epIdx--
                        }
                        view.findViewById<android.view.View>(nextId)?.setOnClickListener {
                            if (epIdx < epCount - 1) epIdx++
                        }
                    },
                )
                // 顶部覆盖层: 跟随控制器
                if (controllerVisible) {
                    // 左上: 返回
                    var playBackFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .onFocusChanged { playBackFocused = it.isFocused }
                            .focusable()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (playBackFocused) Color(0xAA000000) else Color(0x80000000))
                            .border(if (playBackFocused) 2.dp else 0.dp, Color(0xFF8888FF), RoundedCornerShape(8.dp))
                            .clickable { isPlaying = false }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text("← 返回", color = Color.White, fontSize = 14.sp)
                    }
                    // 右上: 剧名 · 集数 + 源名
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x80000000))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "${detail.vod_name} · ${currentEp?.name ?: ""}",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                ms?.label ?: "",
                                color = Color(0xFFAAAACC),
                                fontSize = 11.sp,
                            )
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
                    // 返回
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
