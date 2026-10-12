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
import top.yogiczy.mytv.tv.ui.screen.settings.subcategories.SettingsIptvHybridModeScreen
import top.yogiczy.mytv.tv.ui.screen.settings.subcategories.SettingsIptvSourceCacheTimeScreen
import top.yogiczy.mytv.tv.ui.screen.settings.subcategories.SettingsIptvSourceScreen
import top.yogiczy.mytv.tv.ui.screen.settings.subcategories.SettingsUpdateChannelScreen
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
                        toIptvSourceScreen = { navController.navigateSingleTop(SettingsSubCategories.IPTV_SOURCE.name) },
                        toIptvSourceCacheTimeScreen = {
                            navController.navigateSingleTop(SettingsSubCategories.IPTV_SOURCE_CACHE_TIME.name)
                        },
                        toChannelGroupVisibilityScreen = {
                            navController.navigateSingleTop(SettingsSubCategories.CHANNEL_GROUP_VISIBILITY.name)
                        },
                        toIptvHybridModeScreen = {
                            navController.navigateSingleTop(SettingsSubCategories.IPTV_HYBRID_MODE.name)
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
                        toUpdateChannelScreen = {
                            navController.navigateSingleTop(SettingsSubCategories.UPDATE_CHANNEL.name)
                        },
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
                        onSetCurrent = {
                            settingsViewModel.iptvSourceCurrent = it
                            settingsViewModel.iptvChannelGroupHiddenList = emptySet()
                            settingsViewModel.iptvChannelLastPlay = Channel.EMPTY
                            onReload()
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
                        onBackPressed = {
                            if (!navController.navigateUp()) onBackPressed()
                        },
                    )
                }

                composable(SettingsSubCategories.IPTV_SOURCE_CACHE_TIME.name) {
                    SettingsIptvSourceCacheTimeScreen(
                        cacheTimeProvider = { settingsViewModel.iptvSourceCacheTime },
                        onCacheTimeChanged = {
                            settingsViewModel.iptvSourceCacheTime = it
                            navController.navigateUp()
                        },
                        onBackPressed = { navController.navigateUp() },
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

                composable(SettingsSubCategories.IPTV_HYBRID_MODE.name) {
                    SettingsIptvHybridModeScreen(
                        hybridModeProvider = { settingsViewModel.iptvHybridMode },
                        onHybridModeChanged = {
                            settingsViewModel.iptvHybridMode = it
                            navController.navigateUp()
                        },
                        onBackPressed = { navController.navigateUp() },
                    )
                }

                composable(SettingsSubCategories.UPDATE_CHANNEL.name) {
                    SettingsUpdateChannelScreen(
                        updateChannelProvider = { settingsViewModel.updateChannel },
                        onUpdateChannelChanged = {
                            settingsViewModel.updateChannel = it
                            navController.navigateUp()
                            onCheckUpdate()
                        },
                        onBackPressed = { navController.navigateUp() },
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
