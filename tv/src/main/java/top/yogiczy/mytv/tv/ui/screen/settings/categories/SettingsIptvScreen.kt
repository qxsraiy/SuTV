package top.yogiczy.mytv.tv.ui.screen.settings.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.tv.ui.material.Drawer
import top.yogiczy.mytv.tv.ui.material.DrawerPosition
import top.yogiczy.mytv.tv.ui.material.SimplePopup
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.components.IptvSourcePriorityPanel
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsCategoryScreen
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsListItem
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.theme.colors
import top.yogiczy.mytv.tv.ui.utils.IptvSourcePriority
import top.yogiczy.mytv.tv.ui.utils.clickableNoIndication
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

@Composable
fun SettingsIptvScreen(
    modifier: Modifier = Modifier,
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    settingsViewModel: SettingsViewModel = settingsVM,
    toChannelGroupVisibilityScreen: () -> Unit = {},
    toChannelVisibilityScreen: () -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    var showSourcePopup by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    SettingsCategoryScreen(
        modifier = modifier,
        header = { Text("设置 / 直播源") },
        onBackPressed = onBackPressed,
    ) { firstItemFocusRequester ->
        // ===== 自定义直播源（点击弹出优先级面板）=====
        item {
            val def = IptvSourcePriority.effectiveDefault()
            val backups = IptvSourcePriority.effectiveBackups()

            SettingsListItem(
                modifier = Modifier.focusRequester(firstItemFocusRequester),
                headlineContent = "自定义直播源",
                supportingContent = "设置默认源与备用源",
                trailingContent = {
                    Text("默认：${def.name} / 备用${backups.size}个")
                },
                onSelect = { showSourcePopup = true },
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

    SimplePopup(
        visibleProvider = { showSourcePopup },
        onDismissRequest = { showSourcePopup = false },
    ) {
        IptvSourcePriorityPanel(
            modifier = Modifier,
            settingsViewModel = settingsViewModel,
            onError = { errorMessage = it },
            onDismiss = { showSourcePopup = false },
        )
    }

    // 操作不被允许时的提示弹框（与 App 其余弹框同一套 Drawer + Card 样式，居中）
    SimplePopup(
        visibleProvider = { errorMessage != null },
        onDismissRequest = { errorMessage = null },
    ) {
        Drawer(
            position = DrawerPosition.Center,
            onDismissRequest = { errorMessage = null },
            header = { Text("无法设置") },
        ) {
            Column(
                modifier = Modifier.width(420.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    errorMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Spacer(Modifier.height(16.dp))
                HintCard(text = "知道了", onSelected = { errorMessage = null })
            }
        }
    }
}

@Composable
private fun HintCard(text: String, onSelected: () -> Unit) {
    Card(
        onClick = { },
        modifier = Modifier
            .handleKeyEvents(onSelect = onSelected)
            .clickableNoIndication { onSelected() },
        colors = CardDefaults.colors(
            containerColor = MaterialTheme.colors.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.onSurface),
            ),
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 28.dp),
        )
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsIptvScreenPreview() {
    MyTvTheme {
        SettingsIptvScreen()
    }
}
