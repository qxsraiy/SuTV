package top.yogiczy.mytv.tv.ui.screen.settings.categories

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsCategoryScreen
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsListItem
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.IptvSourcePriority

@Composable
fun SettingsIptvScreen(
    modifier: Modifier = Modifier,
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    settingsViewModel: SettingsViewModel = settingsVM,
    toIptvSourceScreen: () -> Unit = {},
    toChannelGroupVisibilityScreen: () -> Unit = {},
    toChannelVisibilityScreen: () -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    SettingsCategoryScreen(
        modifier = modifier,
        header = { Text("设置 / 直播源") },
        onBackPressed = onBackPressed,
    ) { firstItemFocusRequester ->
        // ===== 自定义直播源（全屏管理页：源列表 + 默认/备用 + 添加源）=====
        item {
            val def = IptvSourcePriority.effectiveDefault()
            val backups = IptvSourcePriority.effectiveBackups()

            SettingsListItem(
                modifier = Modifier.focusRequester(firstItemFocusRequester),
                headlineContent = "自定义直播源",
                supportingContent = "管理直播源，设置默认源与备用源",
                trailingContent = {
                    Text("默认：${def.name} / 备用${backups.size}个")
                },
                onSelect = toIptvSourceScreen,
                link = true,
            )
        }

        // ===== 频道分组管理 =====
        item {
            val allCount = channelGroupListProvider().size
            val hiddenCount = settingsViewModel.iptvChannelGroupHiddenList.size

            SettingsListItem(
                headlineContent = "频道分组管理",
                trailingContent = {
                    if (hiddenCount == 0) {
                        Text("共${allCount}个分组")
                    } else {
                        Text("共${allCount}个分组，已隐藏${hiddenCount}个分组")
                    }
                },
                onSelect = toChannelGroupVisibilityScreen,
                link = true,
            )
        }

        // ===== 频道管理 =====
        item {
            val allChannelCount = channelGroupListProvider().sumOf { it.channelList.size }
            val hiddenChannelCount = settingsViewModel.iptvChannelHiddenList.size

            SettingsListItem(
                headlineContent = "频道管理",
                supportingContent = "屏蔽不想看的频道",
                trailingContent = {
                    if (hiddenChannelCount == 0) {
                        Text("共${allChannelCount}个频道")
                    } else {
                        Text("共${allChannelCount}个频道，已屏蔽${hiddenChannelCount}个")
                    }
                },
                onSelect = toChannelVisibilityScreen,
                link = true,
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsIptvScreenPreview() {
    MyTvTheme {
        SettingsIptvScreen()
    }
}
