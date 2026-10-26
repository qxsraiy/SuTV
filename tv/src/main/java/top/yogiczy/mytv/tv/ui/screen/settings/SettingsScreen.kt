package top.yogiczy.mytv.tv.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSourceList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList
import top.yogiczy.mytv.core.data.repositories.epg.EpgRepository
import top.yogiczy.mytv.core.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.screen.components.AppScreen
import top.yogiczy.mytv.tv.ui.screen.push.PushScreen
import top.yogiczy.mytv.tv.ui.screen.settings.categories.SettingsAppScreen
import top.yogiczy.mytv.tv.ui.screen.settings.categories.SettingsAdvancedScreen
import top.yogiczy.mytv.tv.ui.screen.settings.categories.SettingsIptvScreen
import top.yogiczy.mytv.tv.ui.screen.settings.categories.SettingsPermissionsScreen
import top.yogiczy.mytv.tv.ui.screen.settings.categories.SettingsThemeScreen
import top.yogiczy.mytv.tv.ui.screen.settings.categories.SettingsUpdateScreen
import top.yogiczy.mytv.tv.ui.screen.settings.categories.SettingsVodScreen
import top.yogiczy.mytv.tv.ui.screen.settings.subcategories.SettingsChannelGroupVisibilityScreen
import top.yogiczy.mytv.tv.ui.screen.settings.subcategories.SettingsChannelVisibilityScreen
import top.yogiczy.mytv.tv.ui.screen.settings.subcategories.SettingsIptvSourceScreen
import top.yogiczy.mytv.tv.ui.screen.settings.subcategories.SettingsVodSourceScreen
import top.yogiczy.mytv.tv.ui.utils.navigateSingleTop

object SettingsScreen {
    const val START_DESTINATION = "startDestination"
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    startDestinationProvider: () -> String? = { null },
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    settingsViewModel: SettingsViewModel = settingsVM,
    onCheckUpdate: () -> Unit = {},
    onSwitchSource: (top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource) -> Unit = {},
    toUpdateScreen: () -> Unit = {},
    onReload: () -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while (true) {
            settingsViewModel.refresh()
            delay(1000)
        }
    }

    val navController = rememberNavController()

    AppScreen(modifier = modifier, onBackPressed = onBackPressed) {
        NavHost(
            navController = navController,
            startDestination = startDestinationProvider() ?: "categories",
            builder = {
                composable(route = "categories") {
                    SettingsCategoriesScreen(
                        toSettingsCategoryScreen = { navController.navigateSingleTop(it.name) },
                        onBackPressed = onBackPressed,
                    )
                }

                composable(SettingsCategories.APP.name) {
                    SettingsAppScreen(
                        toPushScreen = { navController.navigateSingleTop(SettingsSubCategories.PUSH.name) },
                        onReload = onReload,
                        onBackPressed = { navController.navigateUp() },
                    )
                }

                composable(SettingsCategories.IPTV.name) {
                    SettingsIptvScreen(
                        channelGroupListProvider = channelGroupListProvider,
                        toIptvSourceScreen = {
                            navController.navigateSingleTop(SettingsSubCategories.IPTV_SOURCE.name)
                        },
                        toChannelGroupVisibilityScreen = {
                            navController.navigateSingleTop(SettingsSubCategories.CHANNEL_GROUP_VISIBILITY.name)
                        },
                        toChannelVisibilityScreen = {
                            navController.navigateSingleTop(SettingsSubCategories.CHANNEL_VISIBILITY.name)
                        },
                        onBackPressed = { navController.navigateUp() },
                    )
                }

                composable(SettingsCategories.VOD.name) {
                    SettingsVodScreen(
                        toVodSourceScreen = {
                            navController.navigateSingleTop(SettingsSubCategories.VOD_SOURCE.name)
                        },
                        onBackPressed = { navController.navigateUp() },
                    )
                }

                composable(SettingsCategories.UPDATE.name) {
                    SettingsUpdateScreen(
                        onBackPressed = { navController.navigateUp() },
                    )
                }

                composable(SettingsCategories.THEME.name) {
                    SettingsThemeScreen(
                        onBackPressed = { navController.navigateUp() },
                    )
                }

                composable(SettingsCategories.PERMISSIONS.name) {
                    SettingsPermissionsScreen(
                        onBackPressed = { navController.navigateUp() },
                    )
                }

                composable(SettingsCategories.ADVANCED.name) {
                    SettingsAdvancedScreen(
                        onBackPressed = { navController.navigateUp() },
                    )
                }

                composable(SettingsSubCategories.IPTV_SOURCE.name) {
                    SettingsIptvSourceScreen(
                        currentIptvSourceProvider = { settingsViewModel.iptvSourceCurrent },
                        iptvSourceListProvider = { settingsViewModel.iptvSourceList },
                        defaultUrlProvider = { settingsViewModel.iptvSourceDefaultUrl },
                        backupUrlsProvider = { settingsViewModel.iptvSourceBackupUrls },
                        onSetCurrent = {
                            settingsViewModel.iptvSourceCurrent = it
                            settingsViewModel.iptvChannelGroupHiddenList = emptySet()
                            settingsViewModel.iptvChannelLastPlay = Channel.EMPTY
                            onReload()
                        },
                        onSetDefault = {
                            settingsViewModel.iptvSourceDefaultUrl = it.url
                            settingsViewModel.iptvSourceBackupUrls =
                                settingsViewModel.iptvSourceBackupUrls - it.url
                            // 后台切换并重新加载，不跳转页面（避免闪回）
                            onSwitchSource(it)
                            Snackbar.show("已设为默认源：" + it.name)
                        },
                        onSetBackup = { source, enable ->
                            settingsViewModel.iptvSourceBackupUrls =
                                if (enable) settingsViewModel.iptvSourceBackupUrls + source.url
                                else settingsViewModel.iptvSourceBackupUrls - source.url
                            Snackbar.show(
                                if (enable) "已设为备用源：" + source.name
                                else "已取消备用源：" + source.name
                            )
                        },
                        onDelete = {
                            settingsViewModel.iptvSourceList =
                                IptvSourceList(settingsViewModel.iptvSourceList - it)
                        },
                        onClearCache = {
                            coroutineScope.launch {
                                IptvRepository(it).clearCache()
                                Snackbar.show("缓存已清除")
                            }
                        },
                        onError = { msg -> Snackbar.show(msg) },
                        onBackPressed = {
                            if (!navController.navigateUp()) onBackPressed()
                        },
                    )
                }

                composable(SettingsSubCategories.CHANNEL_GROUP_VISIBILITY.name) {
                    var hasChanged by remember { mutableStateOf(false) }
                    SettingsChannelGroupVisibilityScreen(
                        channelGroupListProvider = channelGroupListProvider,
                        channelGroupNameHiddenListProvider = { settingsViewModel.iptvChannelGroupHiddenList.toList() },
                        onChannelGroupNameHiddenListChange = {
                            settingsViewModel.iptvChannelGroupHiddenList = it.toSet()
                            hasChanged = true
                        },
                        onBackPressed = {
                            if (hasChanged) onReload()
                            else navController.navigateUp()
                        },
                    )
                }

                composable(SettingsSubCategories.CHANNEL_VISIBILITY.name) {
                    var hasChanged by remember { mutableStateOf(false) }
                    SettingsChannelVisibilityScreen(
                        channelGroupListProvider = channelGroupListProvider,
                        channelNameHiddenListProvider = { settingsViewModel.iptvChannelHiddenList.toList() },
                        onChannelNameHiddenListChange = {
                            settingsViewModel.iptvChannelHiddenList = it.toSet()
                            hasChanged = true
                        },
                        onBackPressed = {
                            if (hasChanged) onReload()
                            else navController.navigateUp()
                        },
                    )
                }

                composable(SettingsSubCategories.VOD_SOURCE.name) {
                    SettingsVodSourceScreen(
                        onBackPressed = { navController.navigateUp() },
                    )
                }

                composable(SettingsSubCategories.PUSH.name) {
                    PushScreen(
                        onBackPressed = { navController.navigateUp() },
                    )
                }
            }
        )
    }
}
