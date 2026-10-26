package top.yogiczy.mytv.tv.ui.screen.settings.categories

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.BuildConfig
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsCategoryScreen
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsListItem
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme

@Composable
fun SettingsUpdateScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = settingsVM,
    onCheckUpdate: () -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    SettingsCategoryScreen(
        modifier = modifier,
        header = { Text("设置 / 系统") },
        onBackPressed = onBackPressed,
    ) { firstItemFocusRequester ->
        // ===== 检查更新：交给上层统一处理（发现新版本直接进入更新界面）=====
        item {
            SettingsListItem(
                modifier = Modifier.focusRequester(firstItemFocusRequester),
                headlineContent = "检查更新",
                supportingContent = "当前版本 v${BuildConfig.VERSION_NAME}",
                trailingContent = "点击检查",
                onSelect = onCheckUpdate,
            )
        }

        // ===== 更新强提醒 =====
        item {
            val forceRemind = settingsViewModel.updateForceRemind

            SettingsListItem(
                headlineContent = "更新强提醒",
                supportingContent = if (forceRemind) "每次启动自动检查，有新版本时进入更新界面"
                else "启动时不提示，仅消息提示",
                trailingContent = {
                    Switch(forceRemind, null)
                },
                onSelect = {
                    settingsViewModel.updateForceRemind = !forceRemind
                },
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsUpdateScreenPreview() {
    MyTvTheme {
        SettingsUpdateScreen()
    }
}
