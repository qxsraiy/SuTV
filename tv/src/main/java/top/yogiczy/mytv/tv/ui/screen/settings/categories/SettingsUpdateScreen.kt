package top.yogiczy.mytv.tv.ui.screen.settings.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.tv.BuildConfig
import top.yogiczy.mytv.tv.ui.material.SimplePopup
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsCategoryScreen
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsListItem
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.screen.update.UpdateViewModel
import top.yogiczy.mytv.tv.ui.screen.update.updateVM
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

@Composable
fun SettingsUpdateScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = settingsVM,
    updateViewModel: UpdateViewModel = updateVM,
    toUpdateChannelScreen: () -> Unit = {},
    toUpdateScreen: () -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()

    var isChecking by remember { mutableStateOf(false) }
    var showUpdatePopup by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
                        errorMessage = null
                        updateViewModel.resetForCheck()
                        withContext(Dispatchers.IO) {
                            updateViewModel.checkUpdate(
                                BuildConfig.VERSION_NAME,
                                settingsViewModel.updateChannel,
                            )
                        }
                        isChecking = false

                        if (updateViewModel.isUpdateAvailable) {
                            showUpdatePopup = true
                        } else {
                            val desc = updateViewModel.latestRelease.description
                            if (desc.contains("失败") || desc.contains("未配置") ||
                                desc.contains("403") || desc.contains("404") ||
                                desc.contains("Unable")
                            ) {
                                errorMessage = desc
                                Snackbar.show("检查更新失败：$desc")
                            } else {
                                Snackbar.show("当前已是最新版本 v${BuildConfig.VERSION_NAME}")
                            }
                        }
                    }
                },
            )
        }

        // ===== 更新通道 =====
        item {
            val channel = settingsViewModel.updateChannel

            SettingsListItem(
                headlineContent = "更新通道",
                trailingContent = when (channel) {
                    "stable" -> "稳定版"
                    "beta" -> "测试版"
                    "dev" -> "开发版"
                    else -> channel
                },
                onSelect = toUpdateChannelScreen,
                link = true,
            )
        }

        // ===== 更新强提醒 =====
        item {
            val forceRemind = settingsViewModel.updateForceRemind

            SettingsListItem(
                headlineContent = "更新强提醒",
                supportingContent = if (forceRemind) "检测到新版本时全屏提示"
                else "检测到新版本时仅消息提示",
                trailingContent = {
                    Switch(forceRemind, null)
                },
                onSelect = {
                    settingsViewModel.updateForceRemind = !forceRemind
                },
            )
        }
    }

    // ===== 发现新版本弹窗（不自动更新，需用户点「立即更新」）=====
    SimplePopup(
        visibleProvider = { showUpdatePopup },
        onDismissRequest = { showUpdatePopup = false },
    ) {
        UpdateAvailableDialog(
            version = updateViewModel.latestRelease.version,
            description = updateViewModel.latestRelease.description,
            onLater = { showUpdatePopup = false },
            onUpdate = {
                showUpdatePopup = false
                toUpdateScreen()
            },
        )
    }
}

@Composable
private fun UpdateAvailableDialog(
    version: String,
    description: String,
    onLater: () -> Unit,
    onUpdate: () -> Unit,
) {
    val firstBtnFocus = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .width(760.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xF0101418))
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(0.6f), RoundedCornerShape(16.dp))
            .padding(28.dp),
    ) {
        Column {
            Text(
                "发现新版本 v$version",
                fontSize = 26.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "更新内容",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                description.trim().ifBlank { "（无更新说明）" }.take(2000),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.8f),
                maxLines = 12,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .height(220.dp)
                    .verticalScroll(rememberScrollState()),
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DialogBtn(
                    text = "立即更新",
                    primary = true,
                    focusRequester = firstBtnFocus,
                    onClick = onUpdate,
                )
                DialogBtn(text = "稍后", primary = false, onClick = onLater)
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { firstBtnFocus.requestFocus() }
    }
}

@Composable
private fun DialogBtn(
    text: String,
    primary: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val base = if (primary) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurface.copy(0.12f)

    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .handleKeyEvents(onSelect = onClick)
            .clickable { onClick() }
            .clip(RoundedCornerShape(50))
            .background(if (focused) MaterialTheme.colorScheme.onSurface else base)
            .border(
                if (focused) 2.dp else 0.dp,
                MaterialTheme.colorScheme.primary,
                RoundedCornerShape(50),
            )
            .padding(horizontal = 28.dp, vertical = 12.dp),
    ) {
        Text(
            text,
            fontSize = 16.sp,
            color = if (focused) MaterialTheme.colorScheme.surface
            else if (primary) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsUpdateScreenPreview() {
    MyTvTheme {
        SettingsUpdateScreen()
    }
}
