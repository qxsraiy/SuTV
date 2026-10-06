package top.yogiczy.mytv.tv.ui.screen.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelFavoriteList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.tv.ui.rememberChildPadding
import top.yogiczy.mytv.tv.ui.screen.components.AppScreen
import top.yogiczy.mytv.tv.ui.screen.dashboard.components.DashboardModuleList
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.Configs
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunched
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
    val childPadding = rememberChildPadding()
    val coroutineScope = rememberCoroutineScope()

    AppScreen(
        modifier = modifier,
        header = {
            DashboardScreeIptvSource(
                currentIptvSourceProvider = currentIptvSourceProvider,
                toSettingsIptvSourceScreen = toSettingsIptvSourceScreen,
                clearCurrentIptvSourceCache = {
                    coroutineScope.launch {
                        IptvRepository(Configs.iptvSourceCurrent).clearCache()
                        onReload()
                    }
                },
            )
        },
        headerExtra = {
            var settingsFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .onFocusChanged { settingsFocused = it.isFocused || it.hasFocus }
                    .focusable()
                    .handleKeyEvents(onSelect = toSettingsScreen),
            ) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = "设置",
                    modifier = Modifier.size(if (settingsFocused) 30.dp else 26.dp),
                    tint = if (settingsFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        onBackPressed = onBackPressed,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            DashboardModuleList(
                modifier = Modifier
                    .fillMaxSize()
                    .focusOnLaunched(),
                toLiveScreen = toLiveScreen,
                toVodScreen = toVodScreen,
                toFavoritesScreen = toFavoritesScreen,
            )
        }
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
            .alpha(alpha.value)
            .background(Color.Transparent,RectangleShape),
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

@Preview(device = "id:Android TV (720p)")
@Composable
private fun DashboardScreenScreen() {
    MyTvTheme {
        DashboardScreen(
            currentIptvSourceProvider = { IptvSource(name = "默认直播源1") },
        )
        // PreviewWithLayoutGrids { }
    }
}