package top.yogiczy.mytv.tv.ui.screen.settings.subcategories

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.tv.ui.rememberChildPadding
import top.yogiczy.mytv.tv.ui.screen.components.AppScreen
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

/**
 * 频道管理：按分组浏览并屏蔽具体频道。
 *
 * - 顶部一行分组标签，遥控左右切换
 * - 下方频道网格，确认键切换 显示/屏蔽
 * - 被屏蔽的频道显示为半透明 + 划线提示，可再次确认恢复
 */
@Composable
fun SettingsChannelVisibilityScreen(
    modifier: Modifier = Modifier,
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    channelNameHiddenListProvider: () -> List<String> = { listOf() },
    onChannelNameHiddenListChange: (List<String>) -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    val childPadding = rememberChildPadding()
    val groupList = channelGroupListProvider()
    val hiddenList = channelNameHiddenListProvider()

    var selectedGroupIdx by remember(groupList.size) { mutableIntStateOf(0) }
    val safeIdx = selectedGroupIdx.coerceIn(0, (groupList.size - 1).coerceAtLeast(0))
    val currentGroup = groupList.getOrNull(safeIdx)

    AppScreen(
        modifier = modifier,
        header = { Text("设置 / 直播源 / 频道管理") },
        canBack = true,
        onBackPressed = onBackPressed,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 分组标签
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = childPadding.paddingValues,
            ) {
                itemsIndexed(groupList) { i, group ->
                    ChannelGroupTab(
                        name = group.name,
                        channelCount = group.channelList.size,
                        hiddenCount = group.channelList.count { it.name in hiddenList },
                        selected = i == safeIdx,
                        onSelect = { selectedGroupIdx = i },
                    )
                }
            }

            // 当前分组的频道网格
            val channels = currentGroup?.channelList ?: emptyList()
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                contentPadding = childPadding.copy(top = 6.dp).paddingValues,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(channels) { channel ->
                    val visible = channel.name !in hiddenList
                    ChannelVisibilityItem(
                        channelProvider = { channel },
                        visibilityProvider = { visible },
                        onVisibilityToggle = {
                            if (visible) {
                                onChannelNameHiddenListChange(hiddenList + channel.name)
                            } else {
                                onChannelNameHiddenListChange(hiddenList - channel.name)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelGroupTab(
    name: String,
    channelCount: Int,
    hiddenCount: Int,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .handleKeyEvents(onSelect = onSelect)
            .scale(if (isFocused) 1.05f else 1f)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isFocused -> MaterialTheme.colorScheme.primary
                    selected -> MaterialTheme.colorScheme.onSurface.copy(0.25f)
                    else -> MaterialTheme.colorScheme.onSurface.copy(0.08f)
                }
            )
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            if (hiddenCount > 0) "$name ($hiddenCount/$channelCount)" else "$name ($channelCount)",
            maxLines = 1,
            color = if (isFocused) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ChannelVisibilityItem(
    modifier: Modifier = Modifier,
    channelProvider: () -> Channel = { Channel.EMPTY },
    visibilityProvider: () -> Boolean = { true },
    onVisibilityToggle: () -> Unit = {},
) {
    val channel = channelProvider()
    val visible = visibilityProvider()
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            .handleKeyEvents(onSelect = onVisibilityToggle)
            .focusable()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(if (visible) 0.10f else 0.03f))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            channel.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (visible) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(0.4f),
            modifier = Modifier
                .weight(1f)
                .then(if (isFocused) Modifier.basicMarquee() else Modifier),
        )

        if (!visible) {
            Icon(
                Icons.Default.VisibilityOff,
                contentDescription = "已屏蔽",
                tint = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsChannelVisibilityScreenPreview() {
    MyTvTheme {
        SettingsChannelVisibilityScreen(
            channelGroupListProvider = { ChannelGroupList.EXAMPLE },
            channelNameHiddenListProvider = { listOf(ChannelGroupList.EXAMPLE.first().channelList.first().name) },
        )
    }
}
