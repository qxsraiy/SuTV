package top.yogiczy.mytv.tv.ui.screen.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList
import top.yogiczy.mytv.tv.ui.material.Drawer
import top.yogiczy.mytv.tv.ui.material.DrawerPosition
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.theme.colors
import top.yogiczy.mytv.tv.ui.utils.IptvSourcePriority
import top.yogiczy.mytv.tv.ui.utils.clickableNoIndication
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

private enum class SourceRole { DISABLED, DEFAULT, BACKUP }

/**
 * 直播源优先级面板（「自定义直播源」弹框内容）
 *
 * 样式与 App 其余弹框一致：Drawer + Card（同 SelectDialog）
 *
 * 规则：
 * - 已有默认源时，其余源只能在「停用 ⇄ 备用」之间切换
 * - 备用已满 3 个且无默认源时，其余源只能在「默认 ⇄ 停用」之间切换
 * - 默认源与 3 个备用源都满时，点击「停用」的源弹框报错
 * - 默认源：备用未满 → 备用；备用已满 → 停用
 * - 备用源 → 停用
 */
@Composable
fun IptvSourcePriorityPanel(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel,
    onError: (String) -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    var refreshKey by remember { mutableIntStateOf(0) }

    val sources = remember(refreshKey) { IptvSourcePriority.allSources() }
    val explicitDefaultUrl = remember(refreshKey) { settingsViewModel.iptvSourceDefaultUrl }
    val backupUrls = remember(refreshKey) { settingsViewModel.iptvSourceBackupUrls }
    val displayDefault = remember(explicitDefaultUrl, sources) {
        sources.firstOrNull { it.url == explicitDefaultUrl }
    }
    val hasDefault = displayDefault != null
    val backupCount = backupUrls.size

    Drawer(
        modifier = modifier,
        position = DrawerPosition.Bottom,
        onDismissRequest = onDismiss,
        header = { Text("自定义直播源") },
    ) {
        Text(
            if (hasDefault) "默认源：${displayDefault!!.name}　备用源 $backupCount/3"
            else "默认源：未设置（播放时使用内置咪咕）　备用源 $backupCount/3",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Text(
            "已有默认源时其余源只能切「备用」；备用满 3 个时只能切「默认」；两者都满时需先停用",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(sources) { source ->
                val role = when {
                    displayDefault?.url == source.url -> SourceRole.DEFAULT
                    backupUrls.contains(source.url) -> SourceRole.BACKUP
                    else -> SourceRole.DISABLED
                }
                val canDelete = source.url !in IptvSourcePriority.builtinUrls()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SourceCard(
                        modifier = Modifier.weight(1f),
                        source = source,
                        role = role,
                        onSelected = {
                            when (role) {
                                SourceRole.DEFAULT -> {
                                    settingsViewModel.iptvSourceDefaultUrl = ""
                                    if (backupCount < 3) {
                                        settingsViewModel.iptvSourceBackupUrls =
                                            backupUrls + source.url
                                    }
                                }

                                SourceRole.BACKUP -> {
                                    settingsViewModel.iptvSourceBackupUrls =
                                        backupUrls - source.url
                                }

                                SourceRole.DISABLED -> {
                                    if (hasDefault && backupCount >= 3) {
                                        onError(
                                            "默认源与 3 个备用源都已设置。\n" +
                                                "请先把现有的默认源或备用源改为停用，再添加新的源。"
                                        )
                                    } else if (hasDefault) {
                                        settingsViewModel.iptvSourceBackupUrls =
                                            backupUrls + source.url
                                    } else {
                                        settingsViewModel.iptvSourceDefaultUrl = source.url
                                    }
                                }
                            }
                            refreshKey++
                        },
                    )

                    if (canDelete) {
                        DialogCard(
                            text = "删除",
                            onSelected = {
                                settingsViewModel.iptvSourceList = IptvSourceList(
                                    settingsViewModel.iptvSourceList.filterNot {
                                        it.url == source.url
                                    }
                                )
                                if (settingsViewModel.iptvSourceDefaultUrl == source.url) {
                                    settingsViewModel.iptvSourceDefaultUrl = ""
                                }
                                settingsViewModel.iptvSourceBackupUrls =
                                    backupUrls - source.url
                                refreshKey++
                            },
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    DialogCard(text = "关闭", onSelected = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun SourceCard(
    modifier: Modifier = Modifier,
    source: IptvSource,
    role: SourceRole,
    onSelected: () -> Unit,
) {
    Card(
        onClick = { },
        modifier = modifier
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    source.name.ifBlank { source.url },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    source.url,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                when (role) {
                    SourceRole.DEFAULT -> "默认"
                    SourceRole.BACKUP -> "备用"
                    SourceRole.DISABLED -> "停用"
                },
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun DialogCard(
    text: String,
    onSelected: () -> Unit,
) {
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
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 20.dp),
        )
    }
}
