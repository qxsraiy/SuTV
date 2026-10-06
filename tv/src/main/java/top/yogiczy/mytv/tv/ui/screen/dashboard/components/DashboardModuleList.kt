package top.yogiczy.mytv.tv.ui.screen.dashboard.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import top.yogiczy.mytv.tv.ui.material.LazyRow
import top.yogiczy.mytv.tv.ui.rememberChildPadding
import top.yogiczy.mytv.tv.ui.screen.components.AppScreen
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

@Composable
fun DashboardModuleList(
    modifier: Modifier = Modifier,
    toLiveScreen: () -> Unit = {},
    toVodScreen: () -> Unit = {},
    toFavoritesScreen: () -> Unit = {},
    toSettingsScreen: () -> Unit = {},
) {
    val childPadding = rememberChildPadding()

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(start = childPadding.start, end = childPadding.end),
    ) { runtime ->
        item {
            DashboardModuleItem(
                modifier = Modifier
                    .focusRequester(runtime.firstItemFocusRequester)
                    .handleKeyEvents(onLeft = { runtime.scrollToLast() }),
                imageVector = Icons.Outlined.Tv,
                title = "直播",
                onSelected = toLiveScreen,
            )
        }

        item {
            DashboardModuleItem(
                imageVector = Icons.Outlined.VideoLibrary,
                title = "点播",
                onSelected = toVodScreen,
            )
        }

        item {
            DashboardModuleItem(
                imageVector = Icons.Outlined.FavoriteBorder,
                title = "收藏",
                onSelected = toFavoritesScreen,
            )
        }

        item {
            DashboardModuleItem(
                modifier = Modifier
                    .focusRequester(runtime.lastItemFocusRequester)
                    .handleKeyEvents(onRight = { runtime.scrollToFirst() }),
                imageVector = Icons.Outlined.Settings,
                title = "设置",
                onSelected = toSettingsScreen,
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun DashboardModuleListPreview() {
    MyTvTheme {
        AppScreen {
            DashboardModuleList(
                modifier = Modifier.padding(vertical = 20.dp),
            )
        }
        // PreviewWithLayoutGrids { }
    }
}