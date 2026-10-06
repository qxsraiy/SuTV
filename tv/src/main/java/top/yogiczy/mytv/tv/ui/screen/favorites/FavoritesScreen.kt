package top.yogiczy.mytv.tv.ui.screen.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelFavoriteList
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroup
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.tv.ui.screen.channels.components.ChannelsChannelGrid
import top.yogiczy.mytv.tv.ui.screen.channels.components.ChannelsChannelGroupList
import top.yogiczy.mytv.tv.ui.screen.components.AppScaffoldHeaderBtn
import top.yogiczy.mytv.tv.ui.screen.components.AppScreen
import top.yogiczy.mytv.tv.ui.screen.vod.VodFavoriteManager
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.BoxWithConstraints

@Composable
fun FavoritesScreen(
    modifier: Modifier = Modifier,
    channelFavoriteListProvider: () -> ChannelFavoriteList = { ChannelFavoriteList() },
    onChannelSelected: (Channel) -> Unit = {},
    onChannelFavoriteToggle: (Channel) -> Unit = {},
    onChannelFavoriteClear: () -> Unit = {},
    epgListProvider: () -> EpgList = { EpgList() },
    onPlayVod: (String, String, String, String, Int) -> Unit = { _, _, _, _, _ -> },
    onBackPressed: () -> Unit = {},
) {
    val context = LocalContext.current
    val favVersion by remember { VodFavoriteManager.version }
    val vodFavorites = remember(favVersion) { VodFavoriteManager.load(context) }

    AppScreen(
        modifier = modifier.padding(top = 10.dp),
        header = { Text("我的收藏") },
        headerExtra = {
            AppScaffoldHeaderBtn(
                title = "清空",
                imageVector = Icons.Outlined.DeleteOutline,
                onSelect = onChannelFavoriteClear,
            )
        },
        canBack = true,
        enableTopBarHidden = true,
        onBackPressed = onBackPressed,
    ) { updateTopBarVisibility ->
        Column(
            modifier = Modifier.padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ===== VOD 收藏 (自适应2行网格) =====
            if (vodFavorites.isNotEmpty()) {
                Text("点播收藏", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 48.dp))
                VodFavoriteGrid(
                    favorites = vodFavorites,
                    onPlayVod = onPlayVod,
                    onRemove = { srcKey, vodId ->
                        VodFavoriteManager.remove(context, srcKey, vodId)
                    },
                )
                Spacer(Modifier.height(8.dp))
            }

            // ===== 直播收藏 =====
            if (channelFavoriteListProvider().isNotEmpty()) {
                val channelFavoriteGroupList =
                    ChannelGroupList(channelFavoriteListProvider().let { channelFavoriteList ->
                        val groupAll = ChannelGroup(
                            name = "全部",
                            channelList = ChannelList(channelFavoriteList.map { it.channel.copy(index = -1) })
                        )
                        listOf(groupAll) + channelFavoriteList
                            .groupBy { it.iptvSourceName }
                            .map { (iptvSourceName, channelFavoriteList) ->
                                ChannelGroup(
                                    name = iptvSourceName,
                                    channelList = ChannelList(channelFavoriteList.map { it.channel.copy(index = -1) }),
                                )
                            }
                    })

                var currentChannelGroupIdx by rememberSaveable { mutableIntStateOf(0) }
                val currentChannelGroup = remember(currentChannelGroupIdx, channelFavoriteGroupList) {
                    channelFavoriteGroupList.getOrElse(currentChannelGroupIdx) { ChannelGroup() }
                }

                ChannelsChannelGroupList(
                    channelGroupListProvider = { channelFavoriteGroupList },
                    currentChannelGroupProvider = { currentChannelGroup },
                    onChannelGroupSelected = {
                        currentChannelGroupIdx = channelFavoriteGroupList.indexOf(it)
                    },
                )

                ChannelsChannelGrid(
                    channelListProvider = { currentChannelGroup.channelList },
                    onChannelSelected = onChannelSelected,
                    onChannelFavoriteToggle = onChannelFavoriteToggle,
                    epgListProvider = epgListProvider,
                    updateTopBarVisibility = updateTopBarVisibility,
                )
            }

            // 空状态
            if (vodFavorites.isEmpty() && channelFavoriteListProvider().isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("暂无收藏", color = Color(0xFF666666), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun VodFavoriteGrid(
    favorites: List<top.yogiczy.mytv.tv.ui.screen.vod.VodFavoriteItem>,
    onPlayVod: (String, String, String, String, Int) -> Unit,
    onRemove: (String, String) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
    ) {
        // 根据宽度自适应列数: 每个卡片约120dp宽
        val cardWidth = 120.dp
        val totalWidth = maxWidth
        val columns = maxOf(2, (totalWidth / (cardWidth + 12.dp)).toInt())

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(favorites) { fav ->
                VodFavoriteCard(
                    fav = fav,
                    onPlay = { onPlayVod(fav.sourceKey, fav.vodId, fav.vodName, fav.vodPic, 0) },
                    onRemove = { onRemove(fav.sourceKey, fav.vodId) },
                )
            }
        }
    }
}

@Composable
private fun VodFavoriteCard(
    fav: top.yogiczy.mytv.tv.ui.screen.vod.VodFavoriteItem,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(120.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onPlay() }
            .clip(RoundedCornerShape(8.dp))
            .border(if (isFocused) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f),
        ) {
            AsyncImage(
                model = fav.vodPic,
                contentDescription = fav.vodName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
            )
            // 删除按钮
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xCCFF0000))
                        .clickable { onRemove() }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text("✕", color = Color.White, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            fav.vodName,
            color = if (isFocused) MaterialTheme.colorScheme.primary else Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun FavoritesScreenPreview() {
    MyTvTheme {
        FavoritesScreen(
            channelFavoriteListProvider = { ChannelFavoriteList.EXAMPLE },
            epgListProvider = { EpgList.example(ChannelList.EXAMPLE) },
        )
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun FavoritesScreenEmptyPreview() {
    MyTvTheme {
        FavoritesScreen()
    }
}
