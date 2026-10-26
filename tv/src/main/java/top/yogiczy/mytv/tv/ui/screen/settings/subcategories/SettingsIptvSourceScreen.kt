package top.yogiczy.mytv.tv.ui.screen.settings.subcategories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList
import top.yogiczy.mytv.core.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.tv.ui.material.CircularProgressIndicator
import top.yogiczy.mytv.tv.ui.material.Drawer
import top.yogiczy.mytv.tv.ui.material.DrawerPosition
import top.yogiczy.mytv.tv.ui.material.LocalPopupManager
import top.yogiczy.mytv.tv.ui.material.SimplePopup
import top.yogiczy.mytv.tv.ui.material.Tag
import top.yogiczy.mytv.tv.ui.material.TagDefaults
import top.yogiczy.mytv.tv.ui.rememberChildPadding
import top.yogiczy.mytv.tv.ui.screen.components.AppScaffoldHeaderBtn
import top.yogiczy.mytv.tv.ui.screen.components.AppScreen
import top.yogiczy.mytv.tv.ui.screen.push.PushContent
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunched
import top.yogiczy.mytv.tv.ui.utils.gridColumns
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import top.yogiczy.mytv.tv.ui.utils.ifElse

@Composable
fun SettingsIptvSourceScreen(
    modifier: Modifier = Modifier,
    currentIptvSourceProvider: () -> IptvSource = { IptvSource() },
    iptvSourceListProvider: () -> IptvSourceList = { IptvSourceList() },
    defaultUrlProvider: () -> String = { "" },
    backupUrlsProvider: () -> Set<String> = { emptySet() },
    onSetCurrent: (IptvSource) -> Unit = {},
    onSetDefault: (IptvSource) -> Unit = {},
    onSetBackup: (IptvSource, Boolean) -> Unit = { _, _ -> },
    onDelete: (IptvSource) -> Unit = {},
    onClearCache: (IptvSource) -> Unit = {},
    onError: (String) -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    val iptvSourceList = IptvSourceList(Constants.IPTV_SOURCE_LIST + iptvSourceListProvider())

    val coroutineScope = rememberCoroutineScope()
    val iptvSourceDetails = remember { mutableStateMapOf<Int, IptvSourceDetail>() }

    suspend fun refreshAll() {
        if (iptvSourceDetails.values.any { it == IptvSourceDetail.Loading }) return

        iptvSourceDetails.clear()
        iptvSourceList.forEach { source ->
            iptvSourceDetails[source.hashCode()] = IptvSourceDetail.Loading
        }

        iptvSourceList.forEach { iptvSource ->
            try {
                val channelGroupList = IptvRepository(iptvSource).getChannelGroupList(0)
                iptvSourceDetails[iptvSource.hashCode()] = IptvSourceDetail.Ready(
                    channelGroupCount = channelGroupList.size,
                    channelCount = channelGroupList.channelList.size,
                    lineCount = channelGroupList.channelList.sumOf { it.lineList.size },
                )
            } catch (_: Exception) {
                iptvSourceDetails[iptvSource.hashCode()] = IptvSourceDetail.Error
            }
        }
    }

    AppScreen(
        modifier = modifier,
        header = { Text("设置 / 直播源 / 自定义直播源") },
        headerExtra = {
            AppScaffoldHeaderBtn(
                title = "刷新全部",
                imageVector = Icons.Default.Refresh,
                onSelect = {
                    coroutineScope.launch {
                        refreshAll()
                    }
                },
            )
        },
        canBack = true,
        onBackPressed = onBackPressed,
    ) {
        SettingsIptvSourceContent(
            currentIptvSourceProvider = currentIptvSourceProvider,
            iptvSourceListProvider = { iptvSourceList },
            iptvSourceDetailsProvider = { iptvSourceDetails },
            defaultUrlProvider = defaultUrlProvider,
            backupUrlsProvider = backupUrlsProvider,
            onSetCurrent = onSetCurrent,
            onSetDefault = onSetDefault,
            onSetBackup = onSetBackup,
            onDelete = onDelete,
            onClearCache = onClearCache,
            onError = onError,
        )
    }
}

@Composable
private fun SettingsIptvSourceContent(
    modifier: Modifier = Modifier,
    currentIptvSourceProvider: () -> IptvSource = { IptvSource() },
    iptvSourceListProvider: () -> IptvSourceList = { IptvSourceList() },
    iptvSourceDetailsProvider: () -> Map<Int, IptvSourceDetail> = { emptyMap() },
    defaultUrlProvider: () -> String = { "" },
    backupUrlsProvider: () -> Set<String> = { emptySet() },
    onSetCurrent: (IptvSource) -> Unit = {},
    onSetDefault: (IptvSource) -> Unit = {},
    onSetBackup: (IptvSource, Boolean) -> Unit = { _, _ -> },
    onDelete: (IptvSource) -> Unit = {},
    onClearCache: (IptvSource) -> Unit = {},
    onError: (String) -> Unit = {},
) {
    val iptvSourceList = iptvSourceListProvider()

    // 页面内部维护默认/备用状态：操作后立即刷新显示，不依赖外部 refresh 时机
    var defaultUrl by remember { mutableStateOf(defaultUrlProvider()) }
    var backupUrls by remember { mutableStateOf(backupUrlsProvider()) }

    // 生效的默认源（未显式设置时回退内置咪咕），保证列表里始终有一行标记为「默认」
    val effectiveDefaultUrl = remember(defaultUrl) {
        if (defaultUrl.isNotBlank()) defaultUrl
        else top.yogiczy.mytv.tv.ui.utils.IptvSourcePriority.builtinMigu().url
    }

    val childPadding = rememberChildPadding()

    LazyColumn(
        modifier = modifier.padding(top = 10.dp),
        contentPadding = childPadding.copy(top = 10.dp).paddingValues,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(iptvSourceList) { iptvSource ->
            val role = when {
                iptvSource.url == effectiveDefaultUrl -> IptvRole.Default
                backupUrls.contains(iptvSource.url) -> IptvRole.Backup
                else -> IptvRole.Disabled
            }

            IptvSourceItem(
                iptvSourceProvider = { iptvSource },
                iptvSourceDetailProvider = {
                    iptvSourceDetailsProvider()[iptvSource.hashCode()] ?: IptvSourceDetail.None
                },
                roleProvider = { role },
                isSelectedProvider = { currentIptvSourceProvider() == iptvSource },
                onSetCurrent = { onSetCurrent(iptvSource) },
                onSetDefault = {
                    // 只有用户「显式设置过」别的默认源时才拦截；
                    // 内置咪咕只是隐式回退，不应阻止把其它源设为默认
                    val otherDefault =
                        if (defaultUrl.isNotBlank() && defaultUrl != iptvSource.url) {
                            iptvSourceList.firstOrNull { it.url == defaultUrl }
                        } else null
                    if (otherDefault != null) {
                        onError("已有默认源「${otherDefault.name}」，请先将它改为停用或备用")
                    } else {
                        defaultUrl = iptvSource.url
                        backupUrls = backupUrls - iptvSource.url
                        onSetDefault(iptvSource)
                    }
                },
                onToggleBackup = {
                    if (role == IptvRole.Backup) {
                        backupUrls = backupUrls - iptvSource.url
                        onSetBackup(iptvSource, false)
                    } else if (backupUrls.size >= 3) {
                        onError("备用源已满 3 个，请先取消一个备用源")
                    } else {
                        backupUrls = backupUrls + iptvSource.url
                        onSetBackup(iptvSource, true)
                    }
                },
                onDelete = { onDelete(iptvSource) },
                onClearCache = { onClearCache(iptvSource) },
            )
        }

        item {
            var visible by remember { mutableStateOf(false) }

            ListItem(
                modifier = Modifier.handleKeyEvents(onSelect = { visible = true }),
                headlineContent = { Text("添加其他直播源") },
                selected = false,
                // 触控支持
                onClick = { visible = true },
            )

            SimplePopup(
                visibleProvider = { visible },
                onDismissRequest = { visible = false },
            ) {
                PushContent()
            }
        }
    }
}

@Composable
private fun IptvSourceItem(
    modifier: Modifier = Modifier,
    iptvSourceProvider: () -> IptvSource = { IptvSource() },
    iptvSourceDetailProvider: () -> IptvSourceDetail = { IptvSourceDetail.Loading },
    roleProvider: () -> IptvRole = { IptvRole.Disabled },
    isSelectedProvider: () -> Boolean = { false },
    onSetCurrent: () -> Unit = {},
    onSetDefault: () -> Unit = {},
    onToggleBackup: () -> Unit = {},
    onDelete: () -> Unit = {},
    onClearCache: () -> Unit = {},
) {
    val iptvSource = iptvSourceProvider()
    val iptvSourceDetail = iptvSourceDetailProvider()
    val role = roleProvider()
    val isSelected = isSelectedProvider()

    val popupManager = LocalPopupManager.current
    val focusRequester = remember { FocusRequester() }

    var actionsVisible by remember { mutableStateOf(false) }

    ListItem(
        modifier = modifier
            .focusRequester(focusRequester)
            .handleKeyEvents(
                onSelect = {
                    popupManager.push(focusRequester, true)
                    actionsVisible = true
                },
                onLongSelect = {
                    popupManager.push(focusRequester, true)
                    actionsVisible = true
                },
            ),
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(iptvSource.name)

                Tag(
                    if (iptvSource.isLocal) "本地" else "远程",
                    colors = TagDefaults.colors(
                        containerColor = LocalContentColor.current.copy(0.1f)
                    ),
                )

                if (!iptvSource.transformJs.isNullOrEmpty()) {
                    Tag(
                        "转换JS",
                        colors = TagDefaults.colors(
                            containerColor = LocalContentColor.current.copy(0.1f)
                        ),
                    )
                }
            }
        },
        supportingContent = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(iptvSource.url)

                if (iptvSourceDetail is IptvSourceDetail.Ready) {
                    Text(
                        listOf(
                            "共${iptvSourceDetail.channelGroupCount}个分组",
                            "${iptvSourceDetail.channelCount}个频道",
                            "${iptvSourceDetail.lineCount}条线路"
                        ).joinToString("，")
                    )
                }
            }
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Tag(
                    when (role) {
                        IptvRole.Default -> "默认"
                        IptvRole.Backup -> "备用"
                        IptvRole.Disabled -> "停用"
                    },
                    colors = TagDefaults.colors(
                        containerColor = if (role == IptvRole.Default)
                            MaterialTheme.colorScheme.primary.copy(0.25f)
                        else LocalContentColor.current.copy(0.1f)
                    ),
                )

                if (isSelected) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                }

                when (iptvSourceDetail) {
                    is IptvSourceDetail.Loading -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 3.dp,
                        color = LocalContentColor.current,
                        trackColor = MaterialTheme.colorScheme.surface.copy(0.1f),
                    )

                    is IptvSourceDetail.Error -> Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )

                    else -> Unit
                }
            }
        },
        selected = false,
        // 触控支持：手指点击等同遥控确认（打开操作菜单）
        onClick = {
            popupManager.push(focusRequester, true)
            actionsVisible = true
        },
    )

    SimplePopup(
        visibleProvider = { actionsVisible },
        onDismissRequest = { actionsVisible = false },
    ) {
        SettingsIptvSourceActions(
            iptvSourceProvider = { iptvSource },
            roleProvider = { role },
            onDismissRequest = { actionsVisible = false },
            onSetDefault = {
                onSetDefault()
                actionsVisible = false
            },
            onToggleBackup = {
                onToggleBackup()
                actionsVisible = false
            },
            onDelete = {
                onDelete()
                actionsVisible = false
            },
            onClearCache = {
                onClearCache()
                actionsVisible = false
            },
        )
    }
}

@Composable
private fun SettingsIptvSourceActions(
    modifier: Modifier = Modifier,
    iptvSourceProvider: () -> IptvSource = { IptvSource() },
    roleProvider: () -> IptvRole = { IptvRole.Disabled },
    onDismissRequest: () -> Unit = {},
    onSetDefault: () -> Unit = {},
    onToggleBackup: () -> Unit = {},
    onDelete: () -> Unit = {},
    onClearCache: () -> Unit = {},
) {
    val iptvSource = iptvSourceProvider()
    val role = roleProvider()

    Drawer(
        modifier = modifier.width(5.gridColumns()),
        onDismissRequest = onDismissRequest,
        position = DrawerPosition.Center,
        header = {
            Text(
                iptvSource.name,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ) {
        // 单列纵向排列，宽度一致、文字左对齐，视觉更整齐
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SettingsIptvSourceActionItem(
                    title = if (role == IptvRole.Default) "已是默认源" else "设为默认源",
                    imageVector = Icons.Outlined.Star,
                    onSelected = onSetDefault,
                    disabled = role == IptvRole.Default,
                    modifier = Modifier.focusOnLaunched(),
                )
            }

            item {
                SettingsIptvSourceActionItem(
                    title = if (role == IptvRole.Backup) "取消备用源" else "设为备用源",
                    imageVector = Icons.Outlined.SwapHoriz,
                    onSelected = onToggleBackup,
                )
            }

            item {
                SettingsIptvSourceActionItem(
                    title = "删除",
                    imageVector = Icons.Outlined.DeleteOutline,
                    onSelected = onDelete,
                )
            }

            item {
                SettingsIptvSourceActionItem(
                    title = "清除缓存",
                    imageVector = Icons.Outlined.ClearAll,
                    onSelected = onClearCache,
                )
            }

            item {
                SettingsIptvSourceActionItem(
                    title = "返回",
                    imageVector = Icons.Outlined.ArrowBackIosNew,
                    onSelected = onDismissRequest,
                )
            }
        }
    }
}

@Composable
private fun SettingsIptvSourceActionItem(
    modifier: Modifier = Modifier,
    title: String,
    imageVector: ImageVector,
    onSelected: () -> Unit = {},
    disabled: Boolean = false,
) {
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .ifElse(
                !disabled,
                Modifier.handleKeyEvents(onSelect = onSelected),
            ),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.onSurface.copy(0.1f),
        ),
        selected = false,
        // 触控支持：手指点击等同遥控确认
        onClick = { if (!disabled) onSelected() },
        leadingContent = { Icon(imageVector, contentDescription = null) },
        headlineContent = { Text(title) },
        enabled = !disabled,
    )
}

enum class IptvRole { Default, Backup, Disabled }

private sealed interface IptvSourceDetail {
    data object None : IptvSourceDetail
    data object Loading : IptvSourceDetail
    data object Error : IptvSourceDetail
    data class Ready(
        val channelGroupCount: Int,
        val channelCount: Int,
        val lineCount: Int,
    ) : IptvSourceDetail
}

@Preview
@Composable
private fun SettingsIptvSourceItemPreview() {
    MyTvTheme {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            IptvSourceItem(
                iptvSourceProvider = { IptvSource.EXAMPLE },
            )

            IptvSourceItem(
                iptvSourceProvider = { IptvSource.EXAMPLE },
                iptvSourceDetailProvider = { IptvSourceDetail.Ready(10, 100, lineCount = 1000) },
            )

            IptvSourceItem(
                iptvSourceProvider = { IptvSource.EXAMPLE },
                iptvSourceDetailProvider = { IptvSourceDetail.Error },
            )

            IptvSourceItem(
                iptvSourceProvider = { IptvSource.EXAMPLE },
                isSelectedProvider = { true },
            )

            IptvSourceItem(
                modifier = Modifier.focusOnLaunched(),
                iptvSourceProvider = { IptvSource.EXAMPLE },
                iptvSourceDetailProvider = { IptvSourceDetail.Error },
                isSelectedProvider = { true },
            )
        }
    }
}

@Preview
@Composable
private fun SettingsIptvSourceActionsPreview() {
    MyTvTheme {
        SettingsIptvSourceActions()
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsIptvSourceScreenPreview() {
    MyTvTheme {
        SettingsIptvSourceScreen(
            currentIptvSourceProvider = { IptvSourceList.EXAMPLE.first() },
            iptvSourceListProvider = { IptvSourceList.EXAMPLE },
            onSetCurrent = {},
        )
    }
}