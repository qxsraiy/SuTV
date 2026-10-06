package top.yogiczy.mytv.tv.ui.screen.dashboard.components

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.tv.R
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import java.io.File

@Composable
fun DashboardModuleList(
    modifier: Modifier = Modifier,
    toLiveScreen: () -> Unit = {},
    toVodScreen: () -> Unit = {},
    toFavoritesScreen: () -> Unit = {},
) {
    val context = LocalContext.current

    // 点播海报状态
    var vodPosterFile by remember { mutableStateOf<File?>(null) }

    // 打开时：轮换海报 + 后台抓新的
    LaunchedEffect(Unit) {
        vodPosterFile = withContext(Dispatchers.IO) {
            DashboardPosterManager.getCurrentPoster(context)
        }
        // 后台抓下一张
        launch(Dispatchers.IO) {
            DashboardPosterManager.fetchNewPoster(context)
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // 直播卡片
        DashboardCard(
            modifier = Modifier.weight(1f),
            imageContent = {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(R.drawable.downloaded_image)
                        .crossfade(true)
                        .build(),
                    contentDescription = "直播",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            },
            title = "直播",
            onSelected = toLiveScreen,
        )

        // 点播卡片
        DashboardCard(
            modifier = Modifier.weight(1f),
            imageContent = {
                val posterFile = vodPosterFile
                if (posterFile != null && posterFile.exists()) {
                    AsyncImage(
                        model = posterFile,
                        contentDescription = "点播",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // 无缓存时用渐变背景占位
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1a1a2e)),
                    )
                }
            },
            title = "点播",
            onSelected = toVodScreen,
        )

        // 收藏卡片（空接口占位）
        DashboardCard(
            modifier = Modifier.weight(1f),
            imageContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF16213e)),
                )
            },
            title = "收藏",
            onSelected = toFavoritesScreen,
        )
    }
}

@Composable
private fun DashboardCard(
    modifier: Modifier = Modifier,
    imageContent: @Composable () -> Unit,
    title: String,
    onSelected: () -> Unit = {},
) {
    var isFocused by remember { mutableStateOf(false) }
    val firstItemFocusRequester = remember { FocusRequester() }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            .focusRequester(firstItemFocusRequester)
            .focusable()
            .handleKeyEvents(onSelect = onSelected)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
            ),
    ) {
        // 图片区域（占大部分）
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            imageContent()
        }

        // 文字标签
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(0.8f))
                .height(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = title,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
