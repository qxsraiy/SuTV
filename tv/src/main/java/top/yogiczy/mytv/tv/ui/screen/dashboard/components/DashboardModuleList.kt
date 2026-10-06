package top.yogiczy.mytv.tv.ui.screen.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.tv.R
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import java.io.File

@Composable
fun DashboardModuleList(
    modifier: Modifier = Modifier,
    imageWidth: Dp = 120.dp,
    imageHeight: Dp = 200.dp,
    textHeight: Dp = 36.dp,
    gap: Dp = 24.dp,
    toLiveScreen: () -> Unit = {},
    toVodScreen: () -> Unit = {},
    toFavoritesScreen: () -> Unit = {},
) {
    val context = LocalContext.current

    var vodPosterFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(Unit) {
        vodPosterFile = withContext(Dispatchers.IO) {
            DashboardPosterManager.getCurrentPoster(context)
        }
        launch(Dispatchers.IO) {
            DashboardPosterManager.fetchNewPoster(context)
        }
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        // 直播
        DashboardCard(
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            textHeight = textHeight,
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

        // 点播
        DashboardCard(
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            textHeight = textHeight,
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

        // 收藏 (空接口占位)
        DashboardCard(
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            textHeight = textHeight,
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
    imageWidth: Dp,
    imageHeight: Dp,
    textHeight: Dp,
    imageContent: @Composable () -> Unit,
    title: String,
    onSelected: () -> Unit = {},
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(imageWidth)
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            .focusable()
            .handleKeyEvents(onSelect = onSelected),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 图片: 固定宽高 (3:5比例由外部计算)
        Box(
            modifier = Modifier
                .width(imageWidth)
                .height(imageHeight)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = if (isFocused) 3.dp else 0.dp,
                    color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                ),
        ) {
            imageContent()
        }

        // 文字 (图片下方, 不遮挡)
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            color = if (isFocused) MaterialTheme.colorScheme.primary else Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}
