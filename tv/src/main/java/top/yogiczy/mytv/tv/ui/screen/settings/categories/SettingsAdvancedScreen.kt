package top.yogiczy.mytv.tv.ui.screen.settings.categories

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsCategoryScreen
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsListItem
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.root.RootActions
import top.yogiczy.mytv.tv.root.RootManager

@Composable
fun SettingsAdvancedScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = settingsVM,
    onBackPressed: () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()

    var isRooted by remember { mutableStateOf(false) }
    var isGranted by remember { mutableStateOf(false) }
    var oomEnabled by remember { mutableStateOf(false) }
    var bootAutoStart by remember { mutableStateOf(false) }
    var isSystemApp by remember { mutableStateOf(false) }
    var isDefaultLauncher by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isRooted = RootManager.isRooted()
        isGranted = RootManager.isGranted
        oomEnabled = settingsViewModel.rootOomProtectionEnabled
        bootAutoStart = settingsViewModel.appBootLaunch
        if (isGranted) {
            isSystemApp = RootActions.isSystemApp()
            isDefaultLauncher = RootActions.isDefaultLauncher()
        }
    }

    SettingsCategoryScreen(
        modifier = modifier,
        header = { Text("设置 / 高级设置") },
        onBackPressed = onBackPressed,
    ) { firstItemFocusRequester ->
        item {
            SettingsListItem(
                modifier = Modifier.focusRequester(firstItemFocusRequester),
                headlineContent = "获取 Root 权限",
                supportingContent = if (isRooted) {
                    if (isGranted) "已授权（su 会话已建立）" else "检测到 root，点击授权"
                } else {
                    "未检测到 root 环境"
                },
                onSelect = {
                    if (!isRooted) {
                        Snackbar.show("设备未 root")
                        return@SettingsListItem
                    }
                    if (isGranted) {
                        Snackbar.show("已授权，释放中...")
                        coroutineScope.launch {
                            withContext(Dispatchers.IO) { RootManager.release() }
                            isGranted = false
                            Snackbar.show("已释放 root 权限")
                        }
                        return@SettingsListItem
                    }
                    busy = true
                    Snackbar.show("正在请求 root...")
                    coroutineScope.launch {
                        val ok = withContext(Dispatchers.IO) { RootManager.requestRoot() }
                        isGranted = ok
                        busy = false
                        if (ok) {
                            Snackbar.show("Root 授权成功")
                            withContext(Dispatchers.IO) {
                                isSystemApp = RootActions.isSystemApp()
                                isDefaultLauncher = RootActions.isDefaultLauncher()
                            }
                        } else {
                            Snackbar.show("Root 授权失败")
                        }
                    }
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "安装为系统应用",
                supportingContent = if (isSystemApp) "已安装（重启后生效，防卸载）" else "复制到 /system/priv-app，需重启",
                onSelect = {
                    if (!isGranted) { Snackbar.show("请先获取 root 权限"); return@SettingsListItem }
                    busy = true
                    Snackbar.show("正在安装...")
                    coroutineScope.launch {
                        val msg = withContext(Dispatchers.IO) { RootActions.installAsSystemApp() }
                        busy = false
                        isSystemApp = RootActions.isSystemApp()
                        Snackbar.show(msg)
                    }
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "移除系统应用",
                supportingContent = "从 /system/priv-app 删除，重启后恢复普通应用",
                onSelect = {
                    if (!isGranted) { Snackbar.show("请先获取 root 权限"); return@SettingsListItem }
                    busy = true
                    Snackbar.show("正在移除...")
                    coroutineScope.launch {
                        val msg = withContext(Dispatchers.IO) { RootActions.removeFromSystem() }
                        busy = false
                        isSystemApp = RootActions.isSystemApp()
                        Snackbar.show(msg)
                    }
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "开机自启",
                supportingContent = "开机后自动启动应用",
                trailingContent = { Switch(bootAutoStart, null) },
                onSelect = {
                    bootAutoStart = !bootAutoStart
                    settingsViewModel.appBootLaunch = bootAutoStart
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "进程守护",
                supportingContent = if (oomEnabled) "已开启（oom_adj=-17，防系统杀）" else "防止系统内存不足时杀掉本应用",
                trailingContent = { Switch(oomEnabled, null) },
                onSelect = {
                    if (!isGranted) { Snackbar.show("请先获取 root 权限"); return@SettingsListItem }
                    val newVal = !oomEnabled
                    busy = true
                    coroutineScope.launch {
                        val msg = withContext(Dispatchers.IO) { RootActions.setOomPriority(newVal) }
                        busy = false
                        oomEnabled = newVal
                        settingsViewModel.rootOomProtectionEnabled = newVal
                        Snackbar.show(msg)
                    }
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "锁定为桌面",
                supportingContent = if (isDefaultLauncher) "已是默认桌面" else "禁用其他桌面，设为默认 Launcher",
                onSelect = {
                    if (!isGranted) { Snackbar.show("请先获取 root 权限"); return@SettingsListItem }
                    busy = true
                    Snackbar.show("正在设置...")
                    coroutineScope.launch {
                        val msg = withContext(Dispatchers.IO) { RootActions.setAsDefaultLauncher() }
                        busy = false
                        isDefaultLauncher = RootActions.isDefaultLauncher()
                        Snackbar.show(msg)
                    }
                },
            )
        }

        item {
            SettingsListItem(
                headlineContent = "恢复系统桌面",
                supportingContent = "重新启用被禁用的桌面应用",
                onSelect = {
                    if (!isGranted) { Snackbar.show("请先获取 root 权限"); return@SettingsListItem }
                    busy = true
                    Snackbar.show("正在恢复...")
                    coroutineScope.launch {
                        val msg = withContext(Dispatchers.IO) { RootActions.restoreDefaultLauncher() }
                        busy = false
                        isDefaultLauncher = RootActions.isDefaultLauncher()
                        Snackbar.show(msg)
                    }
                },
            )
        }
    }
}

@Preview
@Composable
private fun SettingsAdvancedScreenPreview() {
    MyTvTheme { SettingsAdvancedScreen() }
}
