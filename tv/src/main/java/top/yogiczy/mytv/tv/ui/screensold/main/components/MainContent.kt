package top.yogiczy.mytv.tv.ui.screensold.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.core.data.entities.epg.EpgList.Companion.recentProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeReserveList
import top.yogiczy.mytv.core.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.core.data.repositories.epg.EpgRepository
import top.yogiczy.mytv.tv.ui.material.CircularProgressIndicator
import top.yogiczy.mytv.tv.ui.material.PopupContent
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.Visibility
import top.yogiczy.mytv.tv.ui.material.popupable
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsSubCategories
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.screensold.channel.ChannelNumberSelectScreen
import top.yogiczy.mytv.tv.ui.screensold.channel.ChannelTempScreen
import top.yogiczy.mytv.tv.ui.screensold.channel.rememberChannelNumberSelectState
import top.yogiczy.mytv.tv.ui.screensold.classicchannel.ClassicChannelScreen
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.VideoPlayerScreen
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.rememberVideoPlayerState
import top.yogiczy.mytv.tv.ui.utils.backHandler
import top.yogiczy.mytv.tv.ui.utils.handleDragGestures
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

@Composable
fun MainContent(
    modifier: Modifier = Modifier,
    filteredChannelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    favoriteChannelListProvider: () -> ChannelList = { ChannelList() },
    epgListProvider: () -> EpgList = { EpgList() },
    settingsViewModel: SettingsViewModel = settingsVM,
    onChannelFavoriteToggle: (Channel) -> Unit = {},
    toSettingsScreen: (SettingsSubCategories?) -> Unit = {},
    toDashboardScreen: () -> Unit = {},
    onReloadChannelList: () -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    val ctx = LocalContext.current

    val videoPlayerState =
        rememberVideoPlayerState(defaultDisplayModeProvider = { settingsViewModel.videoPlayerDisplayMode })
    val mainContentState = rememberMainContentState(
        videoPlayerState = videoPlayerState,
        channelGroupListProvider = filteredChannelGroupListProvider,
        favoriteChannelListProvider = favoriteChannelListProvider,
        onReloadChannelList = onReloadChannelList,
    )
    val channelNumberSelectState = rememberChannelNumberSelectState {
        val idx = it.toInt() - 1
        filteredChannelGroupListProvider().channelList.getOrNull(idx)?.let { channel ->
            mainContentState.changeCurrentChannel(channel)
        }
    }

    Box(
        modifier = modifier
            .popupable()
            .backHandler { onBackPressed() }
            .handleKeyEvents(
                onUp = {
                    if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentChannelToNext()
                    else mainContentState.changeCurrentChannelToPrev()
                },
                onDown = {
                    if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentChannelToPrev()
                    else mainContentState.changeCurrentChannelToNext()
                },
                onLeft = {
                    if (mainContentState.currentChannel.lineList.size > 1) {
                        mainContentState.changeCurrentChannel(
                            mainContentState.currentChannel,
                            mainContentState.currentChannelLineIdx - 1,
                        )
                    }
                },
                onRight = {
                    if (mainContentState.currentChannel.lineList.size > 1) {
                        mainContentState.changeCurrentChannel(
                            mainContentState.currentChannel,
                            mainContentState.currentChannelLineIdx + 1,
                        )
                    }
                },
                onSelect = { mainContentState.isChannelScreenVisible = true },
                onLongSelect = { mainContentState.isChannelScreenVisible = true },
                onSettings = { toSettingsScreen(null) },
                onDoubleClick = { mainContentState.isChannelScreenVisible = true },
                onLongLeft = { mainContentState.isChannelScreenVisible = true },
                onLongRight = { mainContentState.isChannelScreenVisible = true },
                onLongDown = { mainContentState.isChannelScreenVisible = true },
                onNumber = { channelNumberSelectState.input(it) },
            )
            .handleDragGestures(
                onSwipeDown = {
                    if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentChannelToNext()
                    else mainContentState.changeCurrentChannelToPrev()
                },
                onSwipeUp = {
                    if (settingsViewModel.iptvChannelChangeFlip) mainContentState.changeCurrentChannelToPrev()
                    else mainContentState.changeCurrentChannelToNext()
                },
                onSwipeRight = {
                    if (mainContentState.currentChannel.lineList.size > 1) {
                        mainContentState.changeCurrentChannel(
                            mainContentState.currentChannel,
                            mainContentState.currentChannelLineIdx - 1,
                        )
                    }
                },
                onSwipeLeft = {
                    if (mainContentState.currentChannel.lineList.size > 1) {
                        mainContentState.changeCurrentChannel(
                            mainContentState.currentChannel,
                            mainContentState.currentChannelLineIdx + 1,
                        )
                    }
                },
            ),
    ) {
        VideoPlayerScreen(
            state = videoPlayerState,
            showMetadataProvider = { settingsViewModel.debugShowVideoPlayerMetadata },
        )
    }

    // ==================== 换源遮罩 / 网络故障蓝屏 ====================

    // 换源中或蓝屏时，任意按键都视为「用户有反应」
    val overlayActive = mainContentState.isSourceSwitching || mainContentState.isNetworkError
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (overlayActive) {
                    Modifier
                        .focusable()
                        .onPreviewKeyEvent { ev ->
                            if (ev.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                ev.nativeKeyEvent.repeatCount == 0
                            ) {
                                if (mainContentState.isNetworkError) {
                                    mainContentState.dismissNetworkError()
                                } else {
                                    mainContentState.cancelSourceRecovery()
                                }
                                true
                            } else false
                        }
                } else Modifier
            )
    ) {
        if (mainContentState.isSourceSwitching) {
            // 半透明遮罩 + 转圈 + 提示
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xAA000000)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        mainContentState.sourceSwitchMessage,
                        color = Color.White,
                        fontSize = 20.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "按 OK / 返回 回到频道列表",
                        color = Color(0xFFAAAAAA),
                        fontSize = 14.sp,
                    )
                }
            }
        }

        if (mainContentState.isNetworkError) {
            NetworkErrorScreen(
                onTimeout = {
                    val act = findActivity(ctx)
                    act?.finishAffinity()
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(0)
                },
            )
        }
    }

    ChannelNumberSelectScreen(channelNumberProvider = { channelNumberSelectState.channelNumber })

    Visibility({
        mainContentState.isTempChannelScreenVisible
                && !mainContentState.isChannelScreenVisible
                && channelNumberSelectState.channelNumber.isEmpty()
    }) {
        ChannelTempScreen(
            channelProvider = { mainContentState.currentChannel },
            channelLineIdxProvider = { mainContentState.currentChannelLineIdx },
            recentEpgProgrammeProvider = {
                epgListProvider().recentProgramme(mainContentState.currentChannel)
            },
            currentPlaybackEpgProgrammeProvider = { mainContentState.currentPlaybackEpgProgramme },
            playerMetadataProvider = { videoPlayerState.metadata },
        )
    }

    // 经典频道列表（唯一的弹窗）
    PopupContent(
        visibleProvider = { mainContentState.isChannelScreenVisible },
        onDismissRequest = { mainContentState.isChannelScreenVisible = false },
    ) {
        ClassicChannelScreen(
            channelGroupListProvider = filteredChannelGroupListProvider,
            favoriteChannelListProvider = favoriteChannelListProvider,
            currentChannelProvider = { mainContentState.currentChannel },
            currentChannelLineIdxProvider = { mainContentState.currentChannelLineIdx },
            showChannelLogoProvider = { settingsViewModel.uiShowChannelLogo },
            onChannelSelected = {
                mainContentState.isChannelScreenVisible = false
                mainContentState.changeCurrentChannel(it)
            },
            onChannelFavoriteToggle = onChannelFavoriteToggle,
            epgListProvider = epgListProvider,
            epgProgrammeReserveListProvider = {
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList)
            },
            showEpgProgrammeProgressProvider = { settingsViewModel.uiShowEpgProgrammeProgress },
            supportPlaybackProvider = { mainContentState.supportPlayback(it, null) },
            currentPlaybackEpgProgrammeProvider = { mainContentState.currentPlaybackEpgProgramme },
            onEpgProgrammePlayback = { channel, programme ->
                mainContentState.isChannelScreenVisible = false
                mainContentState.changeCurrentChannel(channel, null, programme)
            },
            onEpgProgrammeReserve = { channel, programme ->
                mainContentState.reverseEpgProgrammeOrNot(channel, programme)
            },
            videoPlayerMetadataProvider = { videoPlayerState.metadata },
            channelFavoriteEnabledProvider = { settingsViewModel.iptvChannelFavoriteEnable },
            channelFavoriteListVisibleProvider = { settingsViewModel.iptvChannelFavoriteListVisible },
            onChannelFavoriteListVisibleChange = {
                settingsViewModel.iptvChannelFavoriteListVisible = it
            },
            onClose = { mainContentState.isChannelScreenVisible = false },
        )
    }
}

/** 从 Context 里找 Activity */
private fun findActivity(context: android.content.Context): android.app.Activity? {
    var ctx = context
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * 网络故障蓝屏。
 * - 30 秒无任何按键 → 自动退出 App
 * - 若本 App 被设为桌面（锁定为桌面），不自动退出，避免被系统立刻拉起形成循环
 */
@Composable
private fun NetworkErrorScreen(onTimeout: () -> Unit) {
    val context = LocalContext.current
    var remaining by remember {
        mutableIntStateOf(
            (top.yogiczy.mytv.core.data.utils.Constants.NETWORK_ERROR_EXIT_DELAY / 1000).toInt()
        )
    }

    LaunchedEffect(Unit) {
        if (isSelfDefaultLauncher(context)) return@LaunchedEffect
        while (remaining > 0) {
            kotlinx.coroutines.delay(1000)
            remaining -= 1
        }
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B2A5B)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "网络故障",
                color = Color.White,
                fontSize = 34.sp,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "请检查网络后重试，或重启 App / 设备",
                color = Color(0xFFCCD9F0),
                fontSize = 18.sp,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                if (isSelfDefaultLauncher(context)) "按任意键返回频道列表"
                else "${remaining} 秒后自动退出，按任意键返回频道列表",
                color = Color(0xFF8FA8CC),
                fontSize = 15.sp,
            )
        }
    }
}

/** 本 App 是否为系统桌面 */
private fun isSelfDefaultLauncher(context: android.content.Context): Boolean {
    return try {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_HOME)
        val res = context.packageManager.resolveActivity(
            intent,
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        )
        res?.activityInfo?.packageName == context.packageName
    } catch (e: Throwable) {
        false
    }
}
