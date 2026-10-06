package top.yogiczy.mytv.tv.ui.screen.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.common.MediaItem
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

    // 加载详情
    LaunchedEffect(sourceKey, vodId) {
        vm.loadDetail(sourceKey, vodId)
    }

    val detail = state.detail

    // 优先选含 m3u8 URL 的线路 (详情变化时自动重选)
    var sourceIdx by remember(detail) {
        mutableIntStateOf(
            if (detail != null && detail.play_sources.isNotEmpty()) {
                val idx = detail.play_sources.indexOfFirst {
                    it.episodes.any { e -> e.url.contains("m3u8", ignoreCase = true) }
                }
                if (idx >= 0) idx else 0
            } else 0
        )
    }

    // 创建 ExoPlayer
    val player = remember {
        ExoPlayer.Builder(context).build()
    }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
    ) {
        if (isPlaying && detail != null) {
            // 全屏播放 — 强制选含 m3u8 URL 的线路
            val sources = detail.play_sources
            val effectiveSourceIdx = if (sources.getOrNull(sourceIdx)?.episodes?.any { it.url.contains("m3u8", ignoreCase = true) } == true) {
                sourceIdx
            } else {
                val m3u8Idx = sources.indexOfFirst { it.episodes.any { e -> e.url.contains("m3u8", ignoreCase = true) } }
                if (m3u8Idx >= 0) m3u8Idx else sourceIdx
            }
            val currentSource = sources.getOrNull(effectiveSourceIdx)
            val currentEp = currentSource?.episodes?.getOrNull(epIdx)

            LaunchedEffect(currentEp?.url) {
                if (currentEp != null && currentEp.url.isNotBlank()) {
                    if (currentEp.url.contains("m3u8", ignoreCase = true)) {
                        // HLS 流: 用 HlsMediaSource 确保走 HLS 解析
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

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = true
                        }
                    },
                )
                // 返回按钮
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x80000000))
                        .clickable { isPlaying = false }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("← 返回", color = Color.White, fontSize = 14.sp)
                }
            }
        } else if (detail != null) {
            // 详情页
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶部: 封面 + 信息
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                ) {
                    // 返回
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF333333))
                            .clickable {
                                player.release()
                                onBack()
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text("← 返回", color = Color.White, fontSize = 14.sp)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                ) {
                    AsyncImage(
                        model = detail.vod_pic.ifBlank { vodPic },
                        contentDescription = detail.vod_name,
                        modifier = Modifier
                            .width(160.dp)
                            .height(240.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(detail.vod_name.ifBlank { vodName }, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("${detail.type_name}  ${detail.vod_year}  ${detail.vod_area}", color = Color(0xFF888888), fontSize = 13.sp)
                        if (detail.vod_score.isNotBlank()) {
                            Text("评分: ${detail.vod_score}", color = Color(0xFFAAAA44), fontSize = 13.sp)
                        }
                        if (detail.vod_remarks.isNotBlank()) {
                            Text("备注: ${detail.vod_remarks}", color = Color(0xFF888888), fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        if (detail.vod_director.isNotBlank()) {
                            Text("导演: ${detail.vod_director}", color = Color(0xFFAAAAAA), fontSize = 12.sp, maxLines = 1)
                        }
                        if (detail.vod_actor.isNotBlank()) {
                            Text("演员: ${detail.vod_actor}", color = Color(0xFFAAAAAA), fontSize = 12.sp, maxLines = 2)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            detail.vod_content.replace("<[^>]+>".toRegex(), "").trim(),
                            color = Color(0xFFCCCCCC),
                            fontSize = 12.sp,
                            maxLines = 4,
                        )
                    }
                }

                // 线路切换
                if (detail.play_sources.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        detail.play_sources.forEachIndexed { i, src ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (i == sourceIdx) Color(0xFF5555AA) else Color(0xFF333333))
                                    .clickable { sourceIdx = i; epIdx = 0 }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(src.name, color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // 选集网格
                val eps = detail.play_sources.getOrNull(sourceIdx)?.episodes ?: emptyList()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(eps.size) { i ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (i == epIdx) Color(0xFF5555AA) else Color(0xFF333333))
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
