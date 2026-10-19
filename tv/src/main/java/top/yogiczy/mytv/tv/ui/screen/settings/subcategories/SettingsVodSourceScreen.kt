package top.yogiczy.mytv.tv.ui.screen.settings.subcategories

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.data.entities.vod.VodSource
import top.yogiczy.mytv.core.data.entities.vod.VodSourceList
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.tv.ui.rememberChildPadding
import top.yogiczy.mytv.tv.ui.screen.components.AppScreen
import top.yogiczy.mytv.tv.ui.screen.components.AppScaffoldHeaderBtn
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsListItem
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.Configs
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check

@Composable
fun SettingsVodSourceScreen(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {},
) {
    val childPadding = rememberChildPadding()
    var refreshKey by remember { mutableStateOf(0) }

    val allSources = remember(refreshKey) {
        Constants.VOD_SOURCE_LIST + Configs.vodSourceList
    }
    // 当前生效的默认源 key
    val defaultKey = remember(refreshKey) {
        Configs.vodDefaultSourceKey.ifBlank {
            Constants.VOD_SOURCE_LIST.find { it.default }?.key ?: ""
        }
    }

    AppScreen(
        modifier = modifier,
        header = { Text("设置 / 点播源 / 点播源管理") },
        canBack = true,
        onBackPressed = onBackPressed,
    ) {
        LazyColumn(
            modifier = Modifier.padding(top = 10.dp),
            contentPadding = childPadding.copy(top = 10.dp).paddingValues,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 当前默认源提示
            item {
                val defName = allSources.find { it.key == defaultKey }?.name ?: "未设置"
                Text(
                    "点播默认源: $defName (打开点播页时使用)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            // 内置源
            item {
                Text(
                    "内置源 (${Constants.VOD_SOURCE_LIST.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            items(Constants.VOD_SOURCE_LIST) { source ->
                VodSourceItem(
                    source = source,
                    isBuiltin = true,
                    isDefault = source.key == defaultKey,
                    onSetDefault = {
                        Configs.vodDefaultSourceKey = source.key
                        refreshKey++
                    },
                )
            }

            // 自定义源
            item {
                Spacer(Modifier.height(10.dp))
                Text(
                    "自定义源 (${Configs.vodSourceList.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            if (Configs.vodSourceList.isEmpty()) {
                item {
                    Text(
                        "暂无自定义源，可通过推送页面添加",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            items(Configs.vodSourceList) { source ->
                VodSourceItem(
                    source = source,
                    isBuiltin = false,
                    isDefault = source.key == defaultKey,
                    onSetDefault = {
                        Configs.vodDefaultSourceKey = source.key
                        refreshKey++
                    },
                    onDelete = {
                        Configs.vodSourceList = VodSourceList(Configs.vodSourceList - source)
                        // 删除的是默认源时重置默认
                        if (Configs.vodDefaultSourceKey == source.key) Configs.vodDefaultSourceKey = ""
                        refreshKey++
                    },
                )
            }
        }
    }
}

@Composable
private fun VodSourceItem(
    source: VodSource,
    isBuiltin: Boolean,
    isDefault: Boolean,
    onSetDefault: () -> Unit = {},
    onDelete: (() -> Unit)? = null,
) {
    var setFocused by remember { mutableStateOf(false) }
    var delFocused by remember { mutableStateOf(false) }
    val highlighted = setFocused || delFocused

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (highlighted) MaterialTheme.colorScheme.onSurface.copy(0.12f)
                else MaterialTheme.colorScheme.onSurface.copy(0.04f)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 源信息 (不聚焦, 纯展示)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                source.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                source.api,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
            )
        }

        // 标签: 内置 / 自定义
        Text(
            if (isBuiltin) "内置" else "自定义",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
        )

        // 设为默认 / 已是默认 (均为独立焦点, 支持左右切换)
        Box(
            modifier = Modifier
                .onFocusChanged { setFocused = it.isFocused }
                .focusable()
                .handleKeyEvents(onSelect = { if (!isDefault) onSetDefault() })
                .clip(RoundedCornerShape(50))
                .background(
                    when {
                        isDefault -> MaterialTheme.colorScheme.primary.copy(0.25f)
                        setFocused -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface.copy(0.10f)
                    }
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                if (isDefault) "✓ 默认" else "设为默认",
                style = MaterialTheme.typography.labelLarge,
                color = if (setFocused && !isDefault) MaterialTheme.colorScheme.surface
                else if (isDefault) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        }

        // 删除 (仅自定义源)
        if (onDelete != null) {
            Box(
                modifier = Modifier
                    .onFocusChanged { delFocused = it.isFocused }
                    .focusable()
                    .handleKeyEvents(onSelect = onDelete)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (delFocused) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface.copy(0.10f)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    "删除",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (delFocused) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsVodSourceScreenPreview() {
    MyTvTheme {
        SettingsVodSourceScreen()
    }
}
