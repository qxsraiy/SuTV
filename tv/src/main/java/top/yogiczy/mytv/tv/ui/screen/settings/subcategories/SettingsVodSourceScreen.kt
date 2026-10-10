package top.yogiczy.mytv.tv.ui.screen.settings.subcategories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                    onDelete = {
                        Configs.vodSourceList = VodSourceList(Configs.vodSourceList - source)
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
    onDelete: (() -> Unit)? = null,
) {
    var isFocused by remember { mutableStateOf(false) }

    SettingsListItem(
        headlineContent = source.name,
        supportingContent = source.api,
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (source.default) {
                    Text("默认", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
                Text(
                    if (isBuiltin) "内置" else "自定义",
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                )
                if (onDelete != null) {
                    AppScaffoldHeaderBtn(
                        title = "删除",
                        imageVector = Icons.Default.Delete,
                        onSelect = onDelete,
                    )
                }
            }
        },
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .handleKeyEvents(),
    )
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsVodSourceScreenPreview() {
    MyTvTheme {
        SettingsVodSourceScreen()
    }
}
