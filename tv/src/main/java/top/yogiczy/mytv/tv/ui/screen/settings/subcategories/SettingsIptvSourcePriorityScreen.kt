package top.yogiczy.mytv.tv.ui.screen.settings.subcategories

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.tv.ui.material.SimplePopup
import top.yogiczy.mytv.tv.ui.rememberChildPadding
import top.yogiczy.mytv.tv.ui.screen.components.AppScreen
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.Configs
import top.yogiczy.mytv.tv.ui.utils.IptvSourcePriority
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

/**
 * 直播源优先级：默认源 1 个 + 备用源最多 3 个。
 *
 * 每行按 OK 循环切换角色：停用 → 默认 → 备用 → 停用
 * - 设置新的默认源时，旧默认源自动变为停用
 * - 备用源最多 3 个
 */
@Composable
fun SettingsIptvSourcePriorityScreen(
    modifier: Modifier = Modifier,
    sourcesProvider: () -> List<IptvSource> = { IptvSourcePriority.allSources() },
    defaultUrlProvider: () -> String = { IptvSourcePriority.effectiveDefault().url },
    backupUrlsProvider: () -> Set<String> = {
        IptvSourcePriority.effectiveBackups().map { it.url }.toSet()
    },
    onDefaultUrlChange: (String) -> Unit = {},
    onBackupUrlsChange: (Set<String>) -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    val childPadding = rememberChildPadding()
    var refreshKey by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val sources = remember(refreshKey) { sourcesProvider() }
    // 仅按「用户显式设置」判断，未设置时不算有默认源
    val explicitDefaultUrl = remember(refreshKey) { Configs.iptvSourceDefaultUrl }
    val backupUrls = remember(refreshKey) { backupUrlsProvider() }

    val displayDefault = remember(explicitDefaultUrl, sources) {
        sources.firstOrNull { it.url == explicitDefaultUrl }
    }

    AppScreen(
        modifier = modifier,
        header = { Text("设置 / 直播源 / 源优先级") },
        canBack = true,
        onBackPressed = onBackPressed,
    ) {
        LazyColumn(
            modifier = Modifier.padding(top = 10.dp),
            contentPadding = childPadding.copy(top = 10.dp).paddingValues,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    if (displayDefault != null) "默认源：${displayDefault.name}"
                    else "默认源：未设置（播放时使用内置咪咕）",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            item {
                Text(
                    "备用源 ${backupUrls.size}/3（默认源失败时并发探测，取最快）\n" +
                        "· 已设默认源时，其他源只能设为备用或停用\n" +
                        "· 备用已满 3 个时，默认源只能改为停用\n" +
                        "· 想换默认源：先把当前默认源改为停用/备用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            items(sources) { source ->
                val role = when {
                    displayDefault?.url == source.url -> SourceRole.DEFAULT
                    backupUrls.contains(source.url) -> SourceRole.BACKUP
                    else -> SourceRole.DISABLED
                }
                IptvSourceRoleItem(
                    source = source,
                    role = role,
                    onCycle = {
                        when (role) {
                            // 停用：只能设默认；若已有默认源则报错
                            SourceRole.DISABLED -> {
                                if (displayDefault != null) {
                                    errorMessage = "已有默认源「${displayDefault.name}」\n" +
                                        "请先将它改为停用或备用，再设置新的默认源"
                                } else {
                                    onDefaultUrlChange(source.url)
                                    onBackupUrlsChange(backupUrls - source.url)
                                }
                            }

                            // 默认：备用有空位 → 备用；已满 → 只能停用
                            SourceRole.DEFAULT -> {
                                onDefaultUrlChange("")
                                if (backupUrls.size < 3) {
                                    onBackupUrlsChange(backupUrls + source.url)
                                }
                            }

                            // 备用：只能停用
                            SourceRole.BACKUP -> {
                                onBackupUrlsChange(backupUrls - source.url)
                            }
                        }
                        refreshKey++
                    },
                )
            }
        }
    }

    // 操作不被允许时的提示弹窗
    SimplePopup(
        visibleProvider = { errorMessage != null },
        onDismissRequest = { errorMessage = null },
    ) {
        ErrorHintDialog(
            message = errorMessage ?: "",
            onConfirm = { errorMessage = null },
        )
    }
}

@Composable
private fun ErrorHintDialog(message: String, onConfirm: () -> Unit) {
    val btnFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(620.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(16.dp))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "无法设置",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(22.dp))
        Box(
            modifier = Modifier
                .focusRequester(btnFocus)
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .handleKeyEvents(onSelect = onConfirm)
                .clickable { onConfirm() }
                .clip(RoundedCornerShape(50))
                .background(
                    if (focused) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.error.copy(0.85f)
                )
                .padding(horizontal = 32.dp, vertical = 12.dp),
        ) {
            Text(
                "知道了",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.surface,
            )
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { btnFocus.requestFocus() }
    }
}

private enum class SourceRole { DISABLED, DEFAULT, BACKUP }

@Composable
private fun IptvSourceRoleItem(
    source: IptvSource,
    role: SourceRole,
    onCycle: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            .focusable()
            .handleKeyEvents(onSelect = onCycle)
            // 触控：整行可点，点一次切换一次角色
            .clickable { onCycle() }
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.onSurface.copy(0.14f)
                else MaterialTheme.colorScheme.onSurface.copy(0.05f)
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                source.name.ifBlank { source.url },
                style = MaterialTheme.typography.titleMedium,
                color = if (role == SourceRole.DISABLED)
                    MaterialTheme.colorScheme.onSurface.copy(0.55f)
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                source.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(
                    when (role) {
                        SourceRole.DEFAULT -> MaterialTheme.colorScheme.primary
                        SourceRole.BACKUP -> MaterialTheme.colorScheme.primary.copy(0.25f)
                        SourceRole.DISABLED -> MaterialTheme.colorScheme.onSurface.copy(0.10f)
                    }
                )
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                when (role) {
                    SourceRole.DEFAULT -> "默认"
                    SourceRole.BACKUP -> "备用"
                    SourceRole.DISABLED -> "停用"
                },
                style = MaterialTheme.typography.labelLarge,
                color = when (role) {
                    SourceRole.DEFAULT -> MaterialTheme.colorScheme.surface
                    SourceRole.BACKUP -> MaterialTheme.colorScheme.primary
                    SourceRole.DISABLED -> MaterialTheme.colorScheme.onSurface.copy(0.5f)
                },
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsIptvSourcePriorityScreenPreview() {
    MyTvTheme {
        SettingsIptvSourcePriorityScreen()
    }
}
