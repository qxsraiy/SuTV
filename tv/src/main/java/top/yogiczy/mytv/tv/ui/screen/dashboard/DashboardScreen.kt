package top.yogiczy.mytv.tv.ui.screen.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.tv.ui.screen.components.AppThemeWrapper
import top.yogiczy.mytv.tv.ui.screen.dashboard.components.DashboardModuleList
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    currentIptvSourceProvider: () -> IptvSource = { IptvSource() },
    toLiveScreen: () -> Unit = {},
    toVodScreen: () -> Unit = {},
    toFavoritesScreen: () -> Unit = {},
    toSettingsScreen: () -> Unit = {},
    toSettingsIptvSourceScreen: () -> Unit = {},
    onReload: () -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    // 全屏 BoxWithConstraints: 自适应计算卡片尺寸和留白
    AppThemeWrapper {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize(),
    ) {
        val screenW = maxWidth
        val screenH = maxHeight

        // 上下留白: 屏幕高度的 3% (上下一致)
        val verticalPadding = screenH * 0.03f

        // 可用高度
        val availableH = screenH - verticalPadding * 2

        // 文字标签高度: ~36dp (titleLarge + spacer)
        val textH = 36.dp

        // 图片高度 = 可用高度 - 文字高度
        val imageH = availableH - textH

        // 图片宽高比 3:5 → 宽 = 高 * 3/5
        val imageW = imageH * 0.6f

        // 3张图片总宽度
        val totalImageW = imageW * 3

        // 剩余宽度用于留白: 左 + 中间2个 + 右
        // 右侧多留一些给设置图标(约1.5倍)
        val remainingW = screenW - totalImageW
        val gap = (remainingW / 5.5f).coerceAtLeast(16.dp) // 4份基础 + 0.5份给右侧额外

        // 设置图标浮动在右侧留白区
        var settingsFocused by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = gap * 0.5f)
                .onFocusChanged { settingsFocused = it.isFocused || it.hasFocus }
                .focusable()
                .handleKeyEvents(onSelect = toSettingsScreen),
        ) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = "设置",
                modifier = Modifier.size(if (settingsFocused) 30.dp else 26.dp),
                tint = if (settingsFocused) MaterialTheme.colorScheme.primary else Color.White,
            )
        }

        // 三卡片居中
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
        ) {
            // 卡片占位由 DashboardModuleList 处理
        }

        // 实际用 Column 包裹 DashboardModuleList, 固定卡片尺寸
        DashboardModuleList(
            modifier = Modifier
                .align(Alignment.Center)
                .width(totalImageW + gap * 2),
            imageWidth = imageW,
            imageHeight = imageH,
            textHeight = textH,
            gap = gap,
            toLiveScreen = toLiveScreen,
            toVodScreen = toVodScreen,
            toFavoritesScreen = toFavoritesScreen,
        )
    }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun DashboardScreenPreview() {
    MyTvTheme {
        DashboardScreen(
            currentIptvSourceProvider = { IptvSource(name = "默认直播源1") },
        )
    }
}

@Composable
fun DashboardScreeIptvSource(
    modifier: Modifier = Modifier,
    currentIptvSourceProvider: () -> IptvSource = { IptvSource() },
    toSettingsIptvSourceScreen: () -> Unit = {},
    clearCurrentIptvSourceCache: () -> Unit = {},
) {
    val currentIptvSource = currentIptvSourceProvider()
    var isFocused by remember { mutableStateOf(false) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            while (true) {
                alpha.animateTo(0.2f, tween(durationMillis = 1000))
                alpha.animateTo(1f, tween(durationMillis = 1000))
            }
        } else {
            alpha.animateTo(1f)
        }
    }

    Box(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            .focusable()
            .handleKeyEvents(
                onSelect = toSettingsIptvSourceScreen,
                onLongSelect = clearCurrentIptvSourceCache,
            )
            .alpha(alpha.value),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(currentIptvSource.name)
            if (isFocused) Icon(Icons.Default.SyncAlt, contentDescription = null)
        }
    }
}
