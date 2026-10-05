package top.yogiczy.mytv.tv.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelFavoriteList
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeReserveList
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSource
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSourceList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList
import top.yogiczy.mytv.tv.sync.CloudSyncProvider
import top.yogiczy.mytv.tv.ui.screen.Screens
import top.yogiczy.mytv.tv.ui.screen.components.AppThemeDef
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.VideoPlayerDisplayMode
import top.yogiczy.mytv.tv.ui.utils.Configs

class SettingsViewModel : ViewModel() {
    private var _appBootLaunch by mutableStateOf(false)
    var appBootLaunch: Boolean
        get() = _appBootLaunch
        set(value) {
            _appBootLaunch = value
            Configs.appBootLaunch = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _appPipEnable by mutableStateOf(false)
    var appPipEnable: Boolean
        get() = _appPipEnable
        set(value) {
            _appPipEnable = value
            Configs.appPipEnable = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _appLastLatestVersion by mutableStateOf("")
    var appLastLatestVersion: String
        get() = _appLastLatestVersion
        set(value) {
            _appLastLatestVersion = value
            Configs.appLastLatestVersion = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _appAgreementAgreed by mutableStateOf(false)
    var appAgreementAgreed: Boolean
        get() = _appAgreementAgreed
        set(value) {
            _appAgreementAgreed = value
            Configs.appAgreementAgreed = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _appStartupScreen by mutableStateOf(Screens.Dashboard.name)
    var appStartupScreen: String
        get() = _appStartupScreen
        set(value) {
            _appStartupScreen = value
            Configs.appStartupScreen = value
            afterSetWhenCloudSyncAutoPull()
        }

    // ========== 调试：自动按构建类型切换 ==========
    val debugDeveloperMode: Boolean get() = Configs.debugDeveloperMode
    val debugShowFps: Boolean get() = Configs.debugShowFps
    val debugShowVideoPlayerMetadata: Boolean get() = Configs.debugShowVideoPlayerMetadata
    val debugShowLayoutGrids: Boolean get() = Configs.debugShowLayoutGrids

    private var _iptvSourceCacheTime by mutableLongStateOf(0)
    var iptvSourceCacheTime: Long
        get() = _iptvSourceCacheTime
        set(value) {
            _iptvSourceCacheTime = value
            Configs.iptvSourceCacheTime = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _iptvSourceCurrent by mutableStateOf(IptvSource())
    var iptvSourceCurrent: IptvSource
        get() = _iptvSourceCurrent
        set(value) {
            _iptvSourceCurrent = value
            Configs.iptvSourceCurrent = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _iptvSourceList by mutableStateOf(IptvSourceList())
    var iptvSourceList: IptvSourceList
        get() = _iptvSourceList
        set(value) {
            _iptvSourceList = value
            Configs.iptvSourceList = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _iptvChannelGroupHiddenList by mutableStateOf(emptySet<String>())
    var iptvChannelGroupHiddenList: Set<String>
        get() = _iptvChannelGroupHiddenList
        set(value) {
            _iptvChannelGroupHiddenList = value
            Configs.iptvChannelGroupHiddenList = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _iptvHybridMode by mutableStateOf(Configs.IptvHybridMode.DISABLE)
    var iptvHybridMode: Configs.IptvHybridMode
        get() = _iptvHybridMode
        set(value) {
            _iptvHybridMode = value
            Configs.iptvHybridMode = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _iptvSimilarChannelMerge by mutableStateOf(false)
    var iptvSimilarChannelMerge: Boolean
        get() = _iptvSimilarChannelMerge
        set(value) {
            _iptvSimilarChannelMerge = value
            Configs.iptvSimilarChannelMerge = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _iptvChannelLogoProvider by mutableStateOf("")
    var iptvChannelLogoProvider: String
        get() = _iptvChannelLogoProvider
        set(value) {
            _iptvChannelLogoProvider = value
            Configs.iptvChannelLogoProvider = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _iptvChannelLogoOverride by mutableStateOf(false)
    var iptvChannelLogoOverride: Boolean
        get() = _iptvChannelLogoOverride
        set(value) {
            _iptvChannelLogoOverride = value
            Configs.iptvChannelLogoOverride = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _iptvChannelFavoriteEnable by mutableStateOf(false)
    var iptvChannelFavoriteEnable: Boolean
        get() = _iptvChannelFavoriteEnable
        set(value) {
            _iptvChannelFavoriteEnable = value
            Configs.iptvChannelFavoriteEnable = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _iptvChannelFavoriteListVisible by mutableStateOf(false)
    var iptvChannelFavoriteListVisible: Boolean
        get() = _iptvChannelFavoriteListVisible
        set(value) {
            _iptvChannelFavoriteListVisible = value
            Configs.iptvChannelFavoriteListVisible = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _iptvChannelFavoriteList by mutableStateOf(ChannelFavoriteList())
    var iptvChannelFavoriteList: ChannelFavoriteList
        get() = _iptvChannelFavoriteList
        set(value) {
            _iptvChannelFavoriteList = value
            Configs.iptvChannelFavoriteList = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _iptvChannelLastPlay by mutableStateOf(Channel.EMPTY)
    var iptvChannelLastPlay: Channel
        get() = _iptvChannelLastPlay
        set(value) {
            _iptvChannelLastPlay = value
            Configs.iptvChannelLastPlay = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _iptvChannelLinePlayableHostList by mutableStateOf(emptySet<String>())
    var iptvChannelLinePlayableHostList: Set<String>
        get() = _iptvChannelLinePlayableHostList
        set(value) {
            _iptvChannelLinePlayableHostList = value
            Configs.iptvChannelLinePlayableHostList = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _iptvChannelLinePlayableUrlList by mutableStateOf(emptySet<String>())
    var iptvChannelLinePlayableUrlList: Set<String>
        get() = _iptvChannelLinePlayableUrlList
        set(value) {
            _iptvChannelLinePlayableUrlList = value
            Configs.iptvChannelLinePlayableUrlList = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _iptvChannelChangeFlip by mutableStateOf(false)
    var iptvChannelChangeFlip: Boolean
        get() = _iptvChannelChangeFlip
        set(value) {
            _iptvChannelChangeFlip = value
            Configs.iptvChannelChangeFlip = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _iptvChannelNoSelectEnable by mutableStateOf(false)
    var iptvChannelNoSelectEnable: Boolean
        get() = _iptvChannelNoSelectEnable
        set(value) {
            _iptvChannelNoSelectEnable = value
            Configs.iptvChannelNoSelectEnable = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _iptvChannelChangeListLoop by mutableStateOf(false)
    var iptvChannelChangeListLoop: Boolean
        get() = _iptvChannelChangeListLoop
        set(value) {
            _iptvChannelChangeListLoop = value
            Configs.iptvChannelChangeListLoop = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _epgEnable by mutableStateOf(false)
    var epgEnable: Boolean
        get() = _epgEnable
        set(value) {
            _epgEnable = value
            Configs.epgEnable = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _epgSourceCurrent by mutableStateOf(EpgSource())
    var epgSourceCurrent: EpgSource
        get() = _epgSourceCurrent
        set(value) {
            _epgSourceCurrent = value
            Configs.epgSourceCurrent = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _epgSourceList by mutableStateOf(EpgSourceList())
    var epgSourceList: EpgSourceList
        get() = _epgSourceList
        set(value) {
            _epgSourceList = value
            Configs.epgSourceList = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _epgRefreshTimeThreshold by mutableIntStateOf(0)
    var epgRefreshTimeThreshold: Int
        get() = _epgRefreshTimeThreshold
        set(value) {
            _epgRefreshTimeThreshold = value
            Configs.epgRefreshTimeThreshold = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _epgSourceFollowIptv by mutableStateOf(false)
    var epgSourceFollowIptv: Boolean
        get() = _epgSourceFollowIptv
        set(value) {
            _epgSourceFollowIptv = value
            Configs.epgSourceFollowIptv = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _epgChannelReserveList by mutableStateOf(EpgProgrammeReserveList())
    var epgChannelReserveList: EpgProgrammeReserveList
        get() = _epgChannelReserveList
        set(value) {
            _epgChannelReserveList = value
            Configs.epgChannelReserveList = value
            afterSetWhenCloudSyncAutoPull()
        }

    // ========== 以下设置全部写死，不允许手动修改 ==========
    val uiShowEpgProgrammeProgress: Boolean get() = Configs.uiShowEpgProgrammeProgress
    val uiShowEpgProgrammePermanentProgress: Boolean get() = Configs.uiShowEpgProgrammePermanentProgress
    val uiShowChannelLogo: Boolean get() = Configs.uiShowChannelLogo
    val uiShowChannelPreview: Boolean get() = Configs.uiShowChannelPreview
    val uiUseClassicPanelScreen: Boolean get() = Configs.uiUseClassicPanelScreen
    val uiDensityScaleRatio: Float get() = Configs.uiDensityScaleRatio
    val uiFontScaleRatio: Float get() = Configs.uiFontScaleRatio
    val uiTimeShowMode: Configs.UiTimeShowMode get() = Configs.uiTimeShowMode
    val uiFocusOptimize: Boolean get() = Configs.uiFocusOptimize
    val uiScreenAutoCloseDelay: Long get() = Configs.uiScreenAutoCloseDelay

    private var _updateForceRemind by mutableStateOf(false)
    var updateForceRemind: Boolean
        get() = _updateForceRemind
        set(value) {
            _updateForceRemind = value
            Configs.updateForceRemind = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _updateChannel by mutableStateOf("")
    var updateChannel: String
        get() = _updateChannel
        set(value) {
            _updateChannel = value
            Configs.updateChannel = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _videoPlayerCore by mutableStateOf(Configs.VideoPlayerCore.MEDIA3)
    var videoPlayerCore: Configs.VideoPlayerCore
        get() = _videoPlayerCore
        set(value) {
            _videoPlayerCore = value
            Configs.videoPlayerCore = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _videoPlayerRenderMode by mutableStateOf(Configs.VideoPlayerRenderMode.SURFACE_VIEW)
    var videoPlayerRenderMode: Configs.VideoPlayerRenderMode
        get() = _videoPlayerRenderMode
        set(value) {
            _videoPlayerRenderMode = value
            Configs.videoPlayerRenderMode = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _videoPlayerUserAgent by mutableStateOf("")
    var videoPlayerUserAgent: String
        get() = _videoPlayerUserAgent
        set(value) {
            _videoPlayerUserAgent = value
            Configs.videoPlayerUserAgent = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _videoPlayerHeaders by mutableStateOf("")
    var videoPlayerHeaders: String
        get() = _videoPlayerHeaders
        set(value) {
            _videoPlayerHeaders = value
            Configs.videoPlayerHeaders = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _videoPlayerLoadTimeout by mutableLongStateOf(0)
    var videoPlayerLoadTimeout: Long
        get() = _videoPlayerLoadTimeout
        set(value) {
            _videoPlayerLoadTimeout = value
            Configs.videoPlayerLoadTimeout = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _videoPlayerDisplayMode by mutableStateOf(VideoPlayerDisplayMode.ORIGINAL)
    var videoPlayerDisplayMode: VideoPlayerDisplayMode
        get() = _videoPlayerDisplayMode
        set(value) {
            _videoPlayerDisplayMode = value
            Configs.videoPlayerDisplayMode = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _videoPlayerForceAudioSoftDecode by mutableStateOf(false)
    var videoPlayerForceAudioSoftDecode: Boolean
        get() = _videoPlayerForceAudioSoftDecode
        set(value) {
            _videoPlayerForceAudioSoftDecode = value
            Configs.videoPlayerForceAudioSoftDecode = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _videoPlayerStopPreviousMediaItem by mutableStateOf(false)
    var videoPlayerStopPreviousMediaItem: Boolean
        get() = _videoPlayerStopPreviousMediaItem
        set(value) {
            _videoPlayerStopPreviousMediaItem = value
            Configs.videoPlayerStopPreviousMediaItem = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _videoPlayerSkipMultipleFramesOnSameVSync by mutableStateOf(false)
    var videoPlayerSkipMultipleFramesOnSameVSync: Boolean
        get() = _videoPlayerSkipMultipleFramesOnSameVSync
        set(value) {
            _videoPlayerSkipMultipleFramesOnSameVSync = value
            Configs.videoPlayerSkipMultipleFramesOnSameVSync = value
            afterSetWhenCloudSyncAutoPull()
        }

    private var _themeAppCurrent by mutableStateOf<AppThemeDef?>(null)
    var themeAppCurrent: AppThemeDef?
        get() = _themeAppCurrent
        set(value) {
            _themeAppCurrent = value
            Configs.themeAppCurrent = value
            afterSetWhenCloudSyncAutoPull()
        }

    // ========== 云同步：已移除，保留只读空壳 ==========
    val cloudSyncAutoPull: Boolean get() = false
    val cloudSyncProvider: CloudSyncProvider get() = Configs.cloudSyncProvider
    val cloudSyncGithubGistId: String get() = ""
    val cloudSyncGithubGistToken: String get() = ""
    val cloudSyncGiteeGistId: String get() = ""
    val cloudSyncGiteeGistToken: String get() = ""
    val cloudSyncNetworkUrl: String get() = ""
    val cloudSyncLocalFilePath: String get() = ""
    val cloudSyncWebDavUrl: String get() = ""
    val cloudSyncWebDavUsername: String get() = ""
    val cloudSyncWebDavPassword: String get() = ""

    private var _feiyangAllInOneFilePath by mutableStateOf("")
    var feiyangAllInOneFilePath: String
        get() = _feiyangAllInOneFilePath
        set(value) {
            _feiyangAllInOneFilePath = value
            Configs.feiyangAllInOneFilePath = value
            afterSetWhenCloudSyncAutoPull()
        }

    private fun afterSetWhenCloudSyncAutoPull() {
        // if (_cloudSyncAutoPull) Snackbar.show("云同步：自动拉取已启用")
    }

    init {
        runCatching { refresh() }

        // 删除过期的预约
        _epgChannelReserveList = EpgProgrammeReserveList(
            _epgChannelReserveList.filter {
                System.currentTimeMillis() < it.startAt + 60 * 1000
            }
        )
    }

    fun refresh() {
        _appBootLaunch = Configs.appBootLaunch
        _appPipEnable = Configs.appPipEnable
        _appLastLatestVersion = Configs.appLastLatestVersion
        _appAgreementAgreed = Configs.appAgreementAgreed
        _appStartupScreen = Configs.appStartupScreen
        _iptvSourceCacheTime = Configs.iptvSourceCacheTime
        _iptvSourceCurrent = Configs.iptvSourceCurrent
        _iptvSourceList = Configs.iptvSourceList
        _iptvChannelGroupHiddenList = Configs.iptvChannelGroupHiddenList
        _iptvHybridMode = Configs.iptvHybridMode
        _iptvSimilarChannelMerge = Configs.iptvSimilarChannelMerge
        _iptvChannelLogoProvider = Configs.iptvChannelLogoProvider
        _iptvChannelLogoOverride = Configs.iptvChannelLogoOverride
        _iptvChannelFavoriteEnable = Configs.iptvChannelFavoriteEnable
        _iptvChannelFavoriteListVisible = Configs.iptvChannelFavoriteListVisible
        _iptvChannelFavoriteList = Configs.iptvChannelFavoriteList
        _iptvChannelLastPlay = Configs.iptvChannelLastPlay
        _iptvChannelLinePlayableHostList = Configs.iptvChannelLinePlayableHostList
        _iptvChannelLinePlayableUrlList = Configs.iptvChannelLinePlayableUrlList
        _iptvChannelChangeFlip = Configs.iptvChannelChangeFlip
        _iptvChannelNoSelectEnable = Configs.iptvChannelNoSelectEnable
        _iptvChannelChangeListLoop = Configs.iptvChannelChangeListLoop
        _epgEnable = Configs.epgEnable
        _epgSourceCurrent = Configs.epgSourceCurrent
        _epgSourceList = Configs.epgSourceList
        _epgRefreshTimeThreshold = Configs.epgRefreshTimeThreshold
        _epgSourceFollowIptv = Configs.epgSourceFollowIptv
        _epgChannelReserveList = Configs.epgChannelReserveList
        _updateForceRemind = Configs.updateForceRemind
        _updateChannel = Configs.updateChannel
        _videoPlayerCore = Configs.videoPlayerCore
        _videoPlayerRenderMode = Configs.videoPlayerRenderMode
        _videoPlayerUserAgent = Configs.videoPlayerUserAgent
        _videoPlayerHeaders = Configs.videoPlayerHeaders
        _videoPlayerLoadTimeout = Configs.videoPlayerLoadTimeout
        _videoPlayerDisplayMode = Configs.videoPlayerDisplayMode
        _videoPlayerForceAudioSoftDecode = Configs.videoPlayerForceAudioSoftDecode
        _videoPlayerStopPreviousMediaItem = Configs.videoPlayerStopPreviousMediaItem
        _videoPlayerSkipMultipleFramesOnSameVSync = Configs.videoPlayerSkipMultipleFramesOnSameVSync
        _themeAppCurrent = Configs.themeAppCurrent
        _feiyangAllInOneFilePath = Configs.feiyangAllInOneFilePath
    }

    companion object {
        var instance: SettingsViewModel? = null
    }
}

val settingsVM: SettingsViewModel
    @Composable get() = SettingsViewModel.instance ?: viewModel<SettingsViewModel>().also {
        SettingsViewModel.instance = it
    }