package top.yogiczy.mytv.tv.ui.screen.main

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelFavorite
import top.yogiczy.mytv.core.data.entities.channel.ChannelFavoriteList
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.chanelGroup
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.tv.BuildConfig
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.rememberDoubleBackPressedExitState
import top.yogiczy.mytv.tv.ui.screen.Screens
import top.yogiczy.mytv.tv.ui.screen.agreement.AgreementScreen
import top.yogiczy.mytv.tv.ui.screen.dashboard.DashboardScreen
import top.yogiczy.mytv.tv.ui.screen.vod.VodScreen
import top.yogiczy.mytv.tv.ui.screen.vod.VodDetailScreen
import top.yogiczy.mytv.tv.ui.screen.vod.VodViewModel
import top.yogiczy.mytv.tv.ui.screen.favorites.FavoritesScreen
import top.yogiczy.mytv.tv.ui.screen.loading.LoadingScreen

import top.yogiczy.mytv.tv.ui.screen.settings.SettingsScreen
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.screen.update.UpdateScreen
import top.yogiczy.mytv.tv.ui.screen.update.UpdateViewModel
import top.yogiczy.mytv.tv.ui.screen.update.updateVM
import top.yogiczy.mytv.tv.ui.utils.navigateSingleTop

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = settingsVM,
    updateViewModel: UpdateViewModel = updateVM,
    mainViewModel: MainViewModel = mainVM,
    onBackPressed: () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    val uiState by mainViewModel.uiState.collectAsState()

    // 点播
    val vodViewModel = remember { VodViewModel() }
    var vodPlayData by remember { mutableStateOf(VodPlayData("", "", "", "", 0)) }

    mainViewModel.needRefresh = { settingsViewModel.refresh() }

    val channelGroupListProvider = {
        (uiState as? MainUiState.Ready)?.channelGroupList ?: ChannelGroupList()
    }
    val filteredChannelGroupListProvider = {
        (uiState as? MainUiState.Ready)?.filteredChannelGroupList ?: ChannelGroupList()
    }
    val epgListProvider = { (uiState as? MainUiState.Ready)?.epgList ?: EpgList() }
    val favoriteChannelListProvider = {
        ChannelList(settingsViewModel.iptvChannelFavoriteList.map { it.channel.copy(index = -1) })
    }

    val navController = rememberNavController()

    fun onChannelSelected(channel: Channel) {
        settingsViewModel.iptvChannelLastPlay = channel
        navController.navigateSingleTop(Screens.Live())
    }

    fun onChannelFavoriteToggle(channel: Channel) {
        if (!settingsViewModel.iptvChannelFavoriteEnable) return


        if (settingsViewModel.iptvChannelFavoriteList.any { it.channel == channel }) {
            settingsViewModel.iptvChannelFavoriteList =
                ChannelFavoriteList(settingsViewModel.iptvChannelFavoriteList.filter {
                    it.channel != channel
                })

            Snackbar.show("取消收藏：${channel.name}")
        } else {
            val favoriteChannel = ChannelFavorite(
                channel = channel,
                iptvSourceName = settingsViewModel.iptvSourceCurrent.name,
                groupName = filteredChannelGroupListProvider().chanelGroup(channel).name,
            )

            settingsViewModel.iptvChannelFavoriteList =
                ChannelFavoriteList(settingsViewModel.iptvChannelFavoriteList + favoriteChannel)

            Snackbar.show("已收藏：${channel.name}")
        }
    }

    fun onChannelFavoriteClear() {
        if (!settingsViewModel.iptvChannelFavoriteEnable) return

        settingsViewModel.iptvChannelFavoriteList = ChannelFavoriteList()
        Snackbar.show("已清空所有收藏")
    }

    fun checkUpdate(quiet: Boolean = true) {
        coroutineScope.launch {
            if (!quiet) Snackbar.show("正在检查更新...", leadingLoading = true, duration = 5000)
            updateViewModel.currentVersion = BuildConfig.VERSION_NAME
            updateViewModel.checkUpdate(BuildConfig.VERSION_NAME, settingsViewModel.updateChannel)

            if (updateViewModel.isUpdateAvailable) {
                val latest = updateViewModel.latestRelease.version
                when {
                    // 手动检查：直接进入更新界面（全屏主题页面）
                    !quiet -> navController.navigateSingleTop(Screens.Update())
                    // 启动自动检查：强提醒开启且该版本未提示过 → 进入更新界面
                    settingsViewModel.updateForceRemind &&
                        settingsViewModel.appLastLatestVersion != latest -> {
                        settingsViewModel.appLastLatestVersion = latest
                        navController.navigateSingleTop(Screens.Update())
                    }
                    else -> Snackbar.show("发现新版本: v$latest")
                }
            } else if (!quiet) {
                Snackbar.show("当前已是最新版本")
            }
        }
    }

    fun reload() {
        settingsViewModel.refresh()
        mainViewModel.init()
        navController.navigateUp()
        navController.navigateSingleTop(Screens.Loading())
    }

    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = if (settingsViewModel.appAgreementAgreed) Screens.Loading() else Screens.Agreement(),
        builder = {
            composable(Screens.Agreement()) {
                AgreementScreen(
                    onAgree = {
                        settingsViewModel.appAgreementAgreed = true
                        navController.navigateSingleTop(Screens.Loading())
                    },
                    onDisagree = onBackPressed,
                    onTouchTested = { },
                )
            }

            composable(Screens.Loading()) {
                LoadingScreen(
                    mainUiState = uiState,
                    toDashboardScreen = {
                        navController.navigateUp()
                        navController.navigateSingleTop(settingsViewModel.appStartupScreen)
                        checkUpdate()
                    },
                    toSettingsScreen = { navController.navigateSingleTop(Screens.Settings()) },
                    onBackPressed = onBackPressed,
                )
            }

            composable(
                route = Screens.Dashboard(),
                exitTransition = { fadeOut(tween(300)) },
                enterTransition = { fadeIn(tween(300)) },
            ) {
                DashboardScreen(
                    toLiveScreen = { navController.navigateSingleTop(Screens.Live()) },
                    toVodScreen = { navController.navigateSingleTop(Screens.Vod()) },
                    toFavoritesScreen = { navController.navigateSingleTop(Screens.Favorites()) },
                    toSettingsScreen = { navController.navigateSingleTop(Screens.Settings()) },
                    onBackPressed = onBackPressed,
                )
            }

            composable(
                route = Screens.Live(),
                exitTransition = { fadeOut(tween(300)) },
                enterTransition = { fadeIn(tween(300)) },
            ) {
                val doubleBackPressedExitState = rememberDoubleBackPressedExitState()

                key(settingsViewModel.videoPlayerCore) {
                    top.yogiczy.mytv.tv.ui.screensold.main.components.MainContent(
                        filteredChannelGroupListProvider = filteredChannelGroupListProvider,
                        favoriteChannelListProvider = favoriteChannelListProvider,
                        epgListProvider = epgListProvider,
                        onChannelFavoriteToggle = { onChannelFavoriteToggle(it) },
                        toSettingsScreen = {
                            if (it != null) {
                                navController.navigateSingleTop(Screens.Settings.withArgs(it))
                            } else {
                                navController.navigateSingleTop(Screens.Settings())
                            }
                        },
                        toDashboardScreen  ={
                            navController.navigateSingleTop(Screens.Dashboard())
                        },
                        onReloadChannelList = { mainViewModel.needRefresh() },
                        onBackPressed = {
                            if (settingsViewModel.appStartupScreen == Screens.Live.name) {
                                navController.navigate(Screens.Dashboard()) {
                                    popUpTo(Screens.Live.name) { inclusive = true }
                                    launchSingleTop = true
                                }
                            } else {
                                if (doubleBackPressedExitState.allowExit) {
                                    navController.navigateUp()
                                } else {
                                    doubleBackPressedExitState.backPress()
                                    Snackbar.show("再按一次退出直播")
                                }
                            }
                        },
                    )
                }
            }

            composable(Screens.Favorites()) {
                FavoritesScreen(
                    channelFavoriteListProvider = { settingsViewModel.iptvChannelFavoriteList },
                    onChannelSelected = { onChannelSelected(it) },
                    onChannelFavoriteToggle = { onChannelFavoriteToggle(it) },
                    onChannelFavoriteClear = { onChannelFavoriteClear() },
                    epgListProvider = epgListProvider,
                    onPlayVod = { srcKey, id, name, pic, epIdx ->
                        vodPlayData = VodPlayData(srcKey, id, name, pic, epIdx)
                        navController.navigateSingleTop(Screens.VodDetail())
                    },
                    onBackPressed = { navController.navigateUp() },
                )
            }

            composable(
                Screens.Settings(),
                arguments = listOf(
                    navArgument(SettingsScreen.START_DESTINATION) { type = NavType.StringType }
                ),
                enterTransition = { fadeIn(tween(300)) },
                exitTransition = { fadeOut(tween(300)) },
                popEnterTransition = { fadeIn(tween(300)) },
                popExitTransition = { fadeOut(tween(300)) },
            ) { backStackEntry ->
                SettingsScreen(
                    startDestinationProvider = {
                        val startDestination =
                            backStackEntry.arguments?.getString(SettingsScreen.START_DESTINATION)

                        if (startDestination == "{${SettingsScreen.START_DESTINATION}}") null
                        else startDestination
                    },
                    channelGroupListProvider = channelGroupListProvider,
                    onCheckUpdate = { checkUpdate(false) },
                    toUpdateScreen = { navController.navigateSingleTop(Screens.Update()) },
                    onReload = { reload() },
                    onBackPressed = { navController.navigateUp() },
                )
            }

            composable(Screens.Update()) {
                UpdateScreen(
                    onBackPressed = { navController.navigateUp() },
                )
            }

            composable(
                route = Screens.Vod(),
                enterTransition = { fadeIn(tween(300)) },
                exitTransition = { fadeOut(tween(300)) },
            ) {
                VodScreen(
                    onPlayVod = { srcKey, id, name, pic, epIdx ->
                        vodPlayData = VodPlayData(srcKey, id, name, pic, epIdx)
                        navController.navigateSingleTop(Screens.VodDetail())
                    },
                    onBack = {
                        navController.navigateSingleTop(Screens.Dashboard())
                    },
                )
            }

            composable(
                route = Screens.VodDetail(),
                enterTransition = { fadeIn(tween(300)) },
                exitTransition = { fadeOut(tween(300)) },
            ) {
                VodDetailScreen(
                    vm = vodViewModel,
                    sourceKey = vodPlayData.sourceKey,
                    vodId = vodPlayData.vodId,
                    vodName = vodPlayData.vodName,
                    vodPic = vodPlayData.vodPic,
                    initialEpIdx = vodPlayData.epIdx,
                    onBack = {
                        navController.navigateSingleTop(Screens.Vod())
                    },
                )
            }
        },
    )
}

private data class VodPlayData(
    val sourceKey: String,
    val vodId: String,
    val vodName: String,
    val vodPic: String,
    val epIdx: Int,
)
