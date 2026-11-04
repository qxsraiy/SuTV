package top.yogiczy.mytv.tv.ui.screen.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import coil.compose.AsyncImage
import top.yogiczy.mytv.tv.ui.screen.components.AppScaffoldHeaderBtn
import top.yogiczy.mytv.tv.ui.screen.components.AppScreen
import top.yogiczy.mytv.tv.ui.screen.components.EmptyState
import top.yogiczy.mytv.tv.ui.screen.components.UiText
import top.yogiczy.mytv.tv.ui.screen.vod.VodFavoriteItem
import top.yogiczy.mytv.tv.ui.screen.vod.VodFavoriteManager
import top.yogiczy.mytv.tv.ui.screen.vod.VodHistoryItem
import top.yogiczy.mytv.tv.ui.screen.vod.VodHistoryManager
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.theme.colors
import top.yogiczy.mytv.tv.ui.utils.clickableNoIndication
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

/**
 * 收藏记录
 *
 * 左右两列：
 *  - 左：历史记录（点播观看历史）
 *  - 右：收藏列表（点播收藏）
 *
 * 控件样式与「设置」里的列表项一致（tv-material3 ListItem + onSurface 10% 容器色）。
 */
@Composable
fun FavoritesScreen(
    modifier: Modifier = Modifier,
    onPlayVod: (String, String, String, String, Int) -> Unit = { _, _, _, _, _ -> },
    onBackPressed: () -> Unit = {},
) {
    val context = LocalContext.current

    val favVersion by VodFavoriteManager.version
    val favorites = remember(favVersion) { VodFavoriteManager.load(context) }

    val hisVersion by VodHistoryManager.version
    val history = remember(hisVersion) { VodHistoryManager.load(context) }

    AppScreen(
        modifier = modifier.padding(top = 10.dp),
        header = { Text("收藏记录") },
        headerExtra = {
            if (favorites.isNotEmpty()) {
                AppScaffoldHeaderBtn(
                    title = "清空收藏",
                    imageVector = Icons.Outlined.DeleteOutline,
                    onSelect = {
                        favorites.forEach {
                            VodFavoriteManager.remove(context, it.sourceKey, it.vodId)
                        }
                    },
                )
            }
        },
        canBack = true,
        onBackPressed = onBackPressed,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ===== 左列：历史记录 =====
            RecordColumn(
                modifier = Modifier.weight(1f),
                title = "历史记录",
                count = history.size,
            ) {
                if (history.isEmpty()) {
                    item {
                        EmptyState(
                            fillMaxSize = false,
                            title = UiText.EMPTY_HISTORY,
                            hint = "播放过的点播会出现在这里",
                        )
                    }
                } else {
                    items(history, key = { "${it.sourceKey}_${it.vodId}" }) { h ->
                        HistoryRow(
                            item = h,
                            onClick = {
                                onPlayVod(h.sourceKey, h.vodId, h.vodName, h.vodPic, h.epIdx)
                            },
                            onDelete = {
                                VodHistoryManager.remove(context, h.sourceKey, h.vodId)
                            },
                        )
                    }
                }
            }

            // ===== 右列：收藏列表 =====
            RecordColumn(
                modifier = Modifier.weight(1f),
                title = "收藏列表",
                count = favorites.size,
            ) {
                if (favorites.isEmpty()) {
                    item {
                        EmptyState(
                            fillMaxSize = false,
                            title = UiText.EMPTY_FAVORITE,
                            hint = "在点播详情页点「☆ 收藏」即可加入",
                        )
                    }
                } else {
                    items(favorites, key = { "${it.sourceKey}_${it.vodId}" }) { f ->
                        FavoriteRow(
                            item = f,
                            onClick = {
                                onPlayVod(f.sourceKey, f.vodId, f.vodName, f.vodPic, 0)
                            },
                            onDelete = {
                                VodFavoriteManager.remove(context, f.sourceKey, f.vodId)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordColumn(
    modifier: Modifier = Modifier,
    title: String,
    count: Int,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = if (count > 0) "$title（$count）" else title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content,
        )
    }
}

/** 历史记录行：与设置里的 ListItem 同款样式 */
@Composable
private fun HistoryRow(
    item: VodHistoryItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    RecordListItem(
        title = item.vodName,
        supporting = "第${item.epIdx + 1}集" + progressText(item.positionMs),
        posterUrl = item.vodPic,
        onClick = onClick,
        onDelete = onDelete,
    )
}

/** 收藏行：与设置里的 ListItem 同款样式 */
@Composable
private fun FavoriteRow(
    item: VodFavoriteItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    RecordListItem(
        title = item.vodName,
        supporting = "收藏",
        posterUrl = item.vodPic,
        onClick = onClick,
        onDelete = onDelete,
    )
}

@Composable
private fun RecordListItem(
    title: String,
    supporting: String,
    posterUrl: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDelete by remember { mutableStateOf(false) }

    ListItem(
        // 触控 + 遥控统一：onClick 与 handleKeyEvents 指向同一动作
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .handleKeyEvents(onSelect = onClick, onLongSelect = onDelete),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colors.surfaceContainer.copy(alpha = 0.72f),
        ),
        selected = false,
        leadingContent = {
            AsyncImage(
                model = posterUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(38.dp, 52.dp)
                    .clip(RoundedCornerShape(6.dp)),
            )
        },
        headlineContent = {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(supporting, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingContent = {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickableNoIndication {
                        showDelete = false
                        onDelete()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = "删除",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

private fun progressText(positionMs: Long): String {
    if (positionMs <= 0) return ""
    val totalSec = positionMs / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "　已看 %02d:%02d".format(m, s)
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun FavoritesScreenPreview() {
    MyTvTheme {
        FavoritesScreen()
    }
}
