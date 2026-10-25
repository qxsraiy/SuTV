package top.yogiczy.mytv.tv.ui.screen.settings.categories

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.tv.BuildConfig
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsCategoryScreen
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsListItem
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.screen.update.UpdateViewModel
import top.yogiczy.mytv.tv.ui.screen.update.updateVM
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme

@Composable
fun SettingsUpdateScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = settingsVM,
    updateViewModel: UpdateViewModel = updateVM,
    onBackPressed: () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(false) }

    SettingsCategoryScreen(
        modifier = modifier,
        header = { Text("设置 / 系统") },
        onBackPressed = onBackPressed,
    ) { firstItemFocusRequester ->
        // ===== 检查更新 =====
        item {
            SettingsListItem(
                modifier = Modifier.focusRequester(firstItemFocusRequester),
                headlineContent = "检查更新",
                supportingContent = "当前版本 v${BuildConfig.VERSION_NAME}",
                trailingContent = if (isChecking) "检查中..." else "点击检查",
                onSelect = {
                    if (isChecking) return@SettingsListItem
                    coroutineScope.launch {
                        isChecking = true
                        updateViewModel.resetForCheck()
                        updateViewModel.currentVersion = BuildConfig.VERSION_NAME
                        withContext(Dispatchers.IO) {
                            updateViewModel.checkUpdate(
                                BuildConfig.VERSION_NAME,
                                settingsViewModel.updateChannel,
                            )
                        }
                        isChecking = false

                        if (updateViewModel.isUpdateAvailable) {
                            updateViewModel.showUpdatePopup()
                        } else {
                            Snackbar.show("当前已是最新版本 v${BuildConfig.VERSION_NAME}")
                        }
                    }
                },
            )
        }

        // ===== 更新强提醒 =====
        item {
            val forceRemind = settingsViewModel.updateForceRemind

            SettingsListItem(
                headlineContent = "更新强提醒",
                supportingContent = if (forceRemind) "每次启动自动检查，有新版本时弹窗提示"
                else "启动时不弹窗，仅消息提示",
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
