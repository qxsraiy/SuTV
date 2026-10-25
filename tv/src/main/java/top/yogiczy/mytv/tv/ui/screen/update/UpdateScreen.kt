package top.yogiczy.mytv.tv.ui.screen.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.entities.git.GitRelease
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.util.utils.ApkInstaller
import top.yogiczy.mytv.tv.BuildConfig
import top.yogiczy.mytv.tv.ui.screen.components.AppScreen
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.theme.SAFE_AREA_HORIZONTAL_PADDING
import top.yogiczy.mytv.tv.ui.tooling.PreviewWithLayoutGrids
import top.yogiczy.mytv.tv.ui.utils.clickableNoIndication
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunched
import top.yogiczy.mytv.tv.ui.utils.gridColumns
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import top.yogiczy.mytv.tv.ui.utils.rememberCanRequestPackageInstallsPermission
import java.io.File

/**
 * 更新界面（与 App 其余界面同一套 tv-material3 样式）
 *
 * - 左侧：版本号 + 更新说明
 * - 右侧：立即更新 / 忽略
 */
@Composable
fun UpdateScreen(
    modifier: Modifier = Modifier,
    updateViewModel: UpdateViewModel = updateVM,
    onBackPressed: () -> Unit = {},
) {
    val latestFile by lazy { File(Globals.cacheDir, "latest.apk") }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val latestRelease = updateViewModel.latestRelease

    val (hasPermission, requestPermission) = rememberCanRequestPackageInstallsPermission()

    // 下载完成后才安装（避免拿旧的 latest.apk 误装）
    LaunchedEffect(updateViewModel.updateDownloaded, hasPermission) {
        if (!updateViewModel.updateDownloaded) return@LaunchedEffect
        if (hasPermission) ApkInstaller.installApk(context, latestFile.path)
        else requestPermission()
    }

    AppScreen(
        modifier = modifier,
        onBackPressed = onBackPressed,
        canBack = true,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(SAFE_AREA_HORIZONTAL_PADDING.dp),
        ) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(2.gridColumns()),
                verticalAlignment = Alignment.Top,
            ) {
                // ===== 左：版本与说明 =====
                Column(
                    modifier = Modifier.width(5.gridColumns()),
                ) {
                    Text(
                        "发现新版本",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "最新版本 v${latestRelease.version}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "当前版本 v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                    )
                    Spacer(Modifier.height(20.dp))

                    val description = latestRelease.description.trim()
                    if (description.isNotEmpty()) {
                        Text(
                            "更新内容",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(6.dp))
                        LazyColumn {
                            item {
                                Text(
                                    description,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(0.85f),
                                )
                            }
                        }
                    }
                }

                // ===== 右：操作按钮 =====
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (updateViewModel.isUpdateAvailable) {
                        if (updateViewModel.isUpdating) {
                            UpdateActionButton(
                                modifier = Modifier.focusOnLaunched(),
                                title = "更新中，请勿关闭页面",
                                enabled = false,
                                onSelected = { },
                            )
                        } else {
                            UpdateActionButton(
                                modifier = Modifier.focusOnLaunched(),
                                title = "立即更新",
                                onSelected = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        updateViewModel.downloadAndUpdate(latestFile)
                                    }
                                },
                            )
                        }

                        UpdateActionButton(
                            title = "忽略",
                            onSelected = onBackPressed,
                        )
                    } else {
                        UpdateActionButton(
                            modifier = Modifier.focusOnLaunched(),
                            title = "当前为最新版本",
                            onSelected = onBackPressed,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateActionButton(
    modifier: Modifier = Modifier,
    title: String,
    enabled: Boolean = true,
    onSelected: () -> Unit = {},
) {
    val btnColors = ButtonDefaults.colors(
        containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    )

    Button(
        modifier = modifier
            .width(4.gridColumns())
            .handleKeyEvents(onSelect = { if (enabled) onSelected() })
            .clickableNoIndication { if (enabled) onSelected() },
        colors = btnColors,
        onClick = { },
    ) {
        Text(
            title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(0.5f),
        )
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun UpdateScreenPreview() {
    MyTvTheme {
        UpdateScreen(
            updateViewModel = UpdateViewModel(
                debugLatestRelease = GitRelease(
                    version = "9.0.0",
                    description = "示例更新说明\r\n".repeat(10),
                )
            )
        )

        PreviewWithLayoutGrids { }
    }
}
