package top.yogiczy.mytv.tv.ui.screen.settings.categories

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsCategoryScreen
import top.yogiczy.mytv.tv.ui.screen.settings.components.SettingsListItem
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.utils.Configs

@Composable
fun SettingsVodScreen(
    modifier: Modifier = Modifier,
    toVodSourceScreen: () -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    SettingsCategoryScreen(
        modifier = modifier,
        header = { Text("设置 / 点播源") },
        onBackPressed = onBackPressed,
    ) { firstItemFocusRequester ->
        // 搜索模式: 保守=每次5个源轮换(快); 激进=全部源并发探测(全)
        item {
            var aggressive by remember { mutableStateOf(Configs.vodSearchAggressive) }

            SettingsListItem(
                modifier = Modifier.focusRequester(firstItemFocusRequester),
                headlineContent = "搜索模式",
                supportingContent = if (aggressive) "激进：同时探测全部点播源（结果最全，略慢）"
                else "保守：每次轮换 5 个源（快，可点刷新换下一批）",
                trailingContent = if (aggressive) "激进" else "保守",
                onSelect = {
                    aggressive = !aggressive
                    Configs.vodSearchAggressive = aggressive
                },
            )
        }

        item {
            val sourceCount = Constants.VOD_SOURCE_LIST.size + Configs.vodSourceList.size

            SettingsListItem(
                headlineContent = "点播源管理",
                supportingContent = "内置${Constants.VOD_SOURCE_LIST.size}个 + 自定义${Configs.vodSourceList.size}个 (共${sourceCount}个)",
                trailingContent = "${sourceCount}个",
                onSelect = toVodSourceScreen,
                link = true,
            )
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun SettingsVodScreenPreview() {
    MyTvTheme {
        SettingsVodScreen()
    }
}
