package top.yogiczy.mytv.tv.ui.screen.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import top.yogiczy.mytv.core.data.entities.vod.VodItem
import top.yogiczy.mytv.core.data.entities.vod.VodSource

@Composable
fun VodScreen(
    onPlayVod: (sourceKey: String, vodId: String, vodName: String, vodPic: String, epIdx: Int) -> Unit,
    onBack: () -> Unit,
) {
    val vm = remember { VodViewModel() }
    val state by vm.state

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部栏: 返回 + 源选择 + 搜索
            VodTopBar(
                vm = vm,
                state = state,
                onBack = onBack,
                onPlayVod = onPlayVod,
            )

            // 分类导航
            if (state.categories.isNotEmpty()) {
                CategoryTabs(
                    categories = state.categories,
                    sourceKey = state.currentSource.key,
                    onSelect = { cat ->
                        vm.loadList(state.currentSource.key, cat.type_id.toString(), 1)
                    },
                )
            }

            // 影片网格
            if (state.loading && state.items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("加载中...", color = Color.White, fontSize = 18.sp)
                }
            } else if (state.items.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.items) { item ->
                        VodCard(
                            item = item,
                            onClick = {
                                onPlayVod(state.currentSource.key, item.vod_id, item.vod_name, item.vod_pic, 0)
                            },
                        )
                    }
                    // 加载更多
                    if (state.page < state.pageCount) {
                        item {
                            LaunchedEffect(Unit) {
                                vm.loadList(state.currentSource.key, "", state.page + 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VodTopBar(
    vm: VodViewModel,
    state: VodUiState,
    onBack: () -> Unit,
    onPlayVod: (String, String, String, String, Int) -> Unit,
) {
    var searchText by remember { mutableStateOf("") }
    var showSources by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 返回
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF333333))
                .clickable { onBack() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("← 返回", color = Color.White, fontSize = 16.sp)
        }
        Spacer(Modifier.width(12.dp))

        // 采集源选择
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF333333))
                .clickable { showSources = !showSources }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("源: ${state.currentSource.name} ▾", color = Color.White, fontSize = 14.sp)
        }
        Spacer(Modifier.width(12.dp))

        // 搜索框
        BasicTextField(
            value = searchText,
            onValueChange = { searchText = it },
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF333333))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            singleLine = true,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF5555AA))
                .clickable {
                    if (searchText.isNotEmpty()) vm.search(searchText, state.currentSource.key)
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("搜索", color = Color.White, fontSize = 14.sp)
        }
    }

    // 源下拉列表
    if (showSources) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF222222))
                .padding(8.dp),
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.sources) { source ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (source.key == state.currentSource.key) Color(0xFF5555AA)
                                else Color(0xFF333333)
                            )
                            .clickable {
                                vm.selectSource(source)
                                showSources = false
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(source.name, color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // 搜索结果覆盖
    if (state.searchResults.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A1A)),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.searchResults) { item ->
                    VodCard(
                        item = item,
                        onClick = {
                            onPlayVod(state.currentSource.key, item.vod_id, item.vod_name, item.vod_pic, 0)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryTabs(
    categories: List<top.yogiczy.mytv.core.data.entities.vod.VodCategory>,
    sourceKey: String,
    onSelect: (top.yogiczy.mytv.core.data.entities.vod.VodCategory) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories) { cat ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF333333))
                    .clickable { onSelect(cat) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(cat.type_name, color = Color.White, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun VodCard(
    item: VodItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF222222))
            .clickable { onClick() }
            .padding(4.dp),
    ) {
        AsyncImage(
            model = item.vod_pic,
            contentDescription = item.vod_name,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            item.vod_name,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            fontWeight = FontWeight.Medium,
        )
        if (item.vod_remarks.isNotEmpty()) {
            Text(item.vod_remarks, color = Color(0xFF888888), fontSize = 10.sp, maxLines = 1)
        }
    }
}
