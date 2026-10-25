package top.yogiczy.mytv.tv.ui.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelFavoriteList
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeReserveList
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSource
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList
import top.yogiczy.mytv.core.data.entities.vod.VodSourceList
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.data.utils.SP
import top.yogiczy.mytv.tv.ui.screen.Screens
import top.yogiczy.mytv.tv.ui.screen.components.AppThemeDef
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.VideoPlayerDisplayMode

/**
 * 应用配置
 */
object Configs {
    enum class KEY {
        /** ==================== 应用 ==================== */
        /** 开机自启 */
        APP_BOOT_LAUNCH,

        /** 画中画启用 */
        APP_PIP_ENABLE,

        /** 上一次最新版本 */
        APP_LAST_LATEST_VERSION,

        /** 协议已同意 */
        APP_AGREEMENT_AGREED,

        /** 打开直接进入直播 */
        APP_STARTUP_SCREEN,

        /** ==================== 调式 ==================== */
        /** 开发者模式 */
        DEBUG_DEVELOPER_MODE,

        /** 显示fps */
        DEBUG_SHOW_FPS,

        /** 播放器详细信息 */
        DEBUG_SHOW_VIDEO_PLAYER_METADATA,

        /** 显示布局网格 */
        DEBUG_SHOW_LAYOUT_GRIDS,

        /** ==================== 直播源 ==================== */
        /** 当前直播源 */
        IPTV_SOURCE_CURRENT,

        /** 直播源列表 */
        IPTV_SOURCE_LIST,

        /** 直播源缓存时间（毫秒） */
        IPTV_SOURCE_CACHE_TIME,

        /** 直播源分组隐藏列表 */
        IPTV_CHANNEL_GROUP_HIDDEN_LIST,

        /** 直播源频道隐藏列表 */
        IPTV_CHANNEL_HIDDEN_LIST,

        /** 混合模式 */
        IPTV_HYBRID_MODE,

        /** 相似频道合并 */
        IPTV_SIMILAR_CHANNEL_MERGE,

        /** 频道图标提供 */
        IPTV_CHANNEL_LOGO_PROVIDER,

        /** 频道图标覆盖 */
        IPTV_CHANNEL_LOGO_OVERRIDE,

        /** 是否启用直播源频道收藏 */
        IPTV_CHANNEL_FAVORITE_ENABLE,

        /** 显示直播源频道收藏列表 */
        IPTV_CHANNEL_FAVORITE_LIST_VISIBLE,

        /** 直播源频道收藏列表 */
        IPTV_CHANNEL_FAVORITE_LIST,

        /** 上一次播放频道 */
        IPTV_CHANNEL_LAST_PLAY,

        /** 直播源线路可播放host列表 */
        IPTV_CHANNEL_LINE_PLAYABLE_HOST_LIST,

        /** 直播源线路可播放地址列表 */
        IPTV_CHANNEL_LINE_PLAYABLE_URL_LIST,

        /** 换台反转 */
        IPTV_CHANNEL_CHANGE_FLIP,

        /** 是否启用数字选台 */
        IPTV_CHANNEL_NO_SELECT_ENABLE,

        /** 换台列表首尾循环 **/
        IPTV_CHANNEL_CHANGE_LIST_LOOP,

        /** ==================== 节目单 ==================== */
        /** 启用节目单 */
        EPG_ENABLE,

        /** 当前节目单来源 */
        EPG_SOURCE_CURRENT,

        /** 节目单刷新时间阈值（小时） */
        EPG_REFRESH_TIME_THRESHOLD,

        /** 节目单跟随直播源 */
        EPG_SOURCE_FOLLOW_IPTV,

        /** 节目预约列表 */
        EPG_CHANNEL_RESERVE_LIST,

        /** ==================== 界面 ==================== */
        /** 显示节目进度 */
        UI_SHOW_EPG_PROGRAMME_PROGRESS,

        /** 显示常驻节目进度 */
        UI_SHOW_EPG_PROGRAMME_PERMANENT_PROGRESS,

        /** 显示台标 */
        UI_SHOW_CHANNEL_LOGO,

        /** 显示频道预览 */
        UI_SHOW_CHANNEL_PREVIEW,

        /** 使用经典选台界面 */
        UI_USE_CLASSIC_PANEL_SCREEN,

        /** 焦点优化 */
        UI_FOCUS_OPTIMIZE,

        /** ==================== 更新 ==================== */
        /** 更新强提醒 */
        UPDATE_FORCE_REMIND,

        /** 更新通道 */
        UPDATE_CHANNEL,

        /** ==================== 播放器 ==================== */
        /** 播放器 内核 */
        VIDEO_PLAYER_CORE,

        /** 播放器 渲染方式 */
        VIDEO_PLAYER_RENDER_MODE,

        /** 播放器 自定义ua */
        VIDEO_PLAYER_USER_AGENT,

        /** 播放器 自定义headers */
        VIDEO_PLAYER_HEADERS,

        /** 播放器 加载超时 */
        VIDEO_PLAYER_LOAD_TIMEOUT,

        /** 播放器 显示模式 */
        VIDEO_PLAYER_DISPLAY_MODE,

        /** 播放器 强制音频软解 */
        VIDEO_PLAYER_FORCE_AUDIO_SOFT_DECODE,

        /** 播放器 停止上一媒体项 */
        VIDEO_PLAYER_STOP_PREVIOUS_MEDIA_ITEM,

        /** 点播播放器 画面比例 (0=适应 1=裁剪 2=拉伸) */
        VOD_PLAYER_RESIZE_MODE,

        /** 点播 默认采集源 key (空=用内置默认) */
        VOD_DEFAULT_SOURCE_KEY,

        /** ==================== 主题 ==================== */
        /** 当前应用主题 */
        THEME_APP_CURRENT,

        /** 肥羊 AllInOne 文件路径 */
        FEIYANG_ALLINONE_FILE_PATH,

        /** ==================== 点播源 ==================== */
        /** 点播源列表 (用户添加) */
        VOD_SOURCE_LIST,

        /** ==================== 高级（Root） ==================== */
        /** Root OOM 防清理 */
        ROOT_OOM_PROTECTION_ENABLED,
    }

    /** ==================== 应用 ==================== */
    /** 开机自启 */
    var appBootLaunch: Boolean
        get() = SP.getBoolean(KEY.APP_BOOT_LAUNCH.name, false)
        set(value) = SP.putBoolean(KEY.APP_BOOT_LAUNCH.name, value)

    /** 画中画启用 */
    var appPipEnable: Boolean
        get() = SP.getBoolean(KEY.APP_PIP_ENABLE.name, false)
        set(value) = SP.putBoolean(KEY.APP_PIP_ENABLE.name, value)

    /** 上一次最新版本 */
    var appLastLatestVersion: String
        get() = SP.getString(KEY.APP_LAST_LATEST_VERSION.name, "")
        set(value) = SP.putString(KEY.APP_LAST_LATEST_VERSION.name, value)

    /** 协议已同意 */
    var appAgreementAgreed: Boolean
        get() = SP.getBoolean(KEY.APP_AGREEMENT_AGREED.name, false)
        set(value) = SP.putBoolean(KEY.APP_AGREEMENT_AGREED.name, value)

    /** 起始界面 */
    var appStartupScreen: String
        get() = SP.getString(KEY.APP_STARTUP_SCREEN.name, Screens.Dashboard.name)
        set(value) = SP.putString(KEY.APP_STARTUP_SCREEN.name, value)

    /** ==================== 高级（Root） ==================== */
    /** Root OOM 防清理开关 */
    var rootOomProtectionEnabled: Boolean
        get() = SP.getBoolean(KEY.ROOT_OOM_PROTECTION_ENABLED.name, false)
        set(value) = SP.putBoolean(KEY.ROOT_OOM_PROTECTION_ENABLED.name, value)

    /** ==================== 调试（自动按构建类型切换） ==================== */
    val debugDeveloperMode: Boolean get() = false
    val debugShowFps: Boolean get() = false
    val debugShowVideoPlayerMetadata: Boolean get() = false
    val debugShowLayoutGrids: Boolean get() = false

    /** ==================== 直播源 ==================== */
    /** 当前直播源 */
    var iptvSourceCurrent: IptvSource
        get() = Globals.json.decodeFromString(SP.getString(KEY.IPTV_SOURCE_CURRENT.name, "")
            .ifBlank { Globals.json.encodeToString(Constants.IPTV_SOURCE_LIST.first()) })
        set(value) = SP.putString(KEY.IPTV_SOURCE_CURRENT.name, Globals.json.encodeToString(value))

    /** 直播源列表 (仅用户添加的, 内置源从Constants读取) */
    var iptvSourceList: IptvSourceList
        get() = Globals.json.decodeFromString(
            SP.getString(KEY.IPTV_SOURCE_LIST.name, "")
                .ifBlank { Globals.json.encodeToString(IptvSourceList()) }
        )
        set(value) = SP.putString(KEY.IPTV_SOURCE_LIST.name, Globals.json.encodeToString(value))

    /** 直播源缓存时间（毫秒） */
    var iptvSourceCacheTime: Long
        get() = SP.getLong(KEY.IPTV_SOURCE_CACHE_TIME.name, Constants.IPTV_SOURCE_CACHE_TIME)
        set(value) = SP.putLong(KEY.IPTV_SOURCE_CACHE_TIME.name, value)

    /** 直播源分组隐藏列表 */
    var iptvChannelGroupHiddenList: Set<String>
        get() = SP.getStringSet(KEY.IPTV_CHANNEL_GROUP_HIDDEN_LIST.name, emptySet())
        set(value) = SP.putStringSet(KEY.IPTV_CHANNEL_GROUP_HIDDEN_LIST.name, value)

    /** 直播源频道隐藏列表（按频道名屏蔽具体频道） */
    var iptvChannelHiddenList: Set<String>
        get() = SP.getStringSet(KEY.IPTV_CHANNEL_HIDDEN_LIST.name, emptySet())
        set(value) = SP.putStringSet(KEY.IPTV_CHANNEL_HIDDEN_LIST.name, value)

    /** 混合模式 */
    var iptvHybridMode: IptvHybridMode
        get() = IptvHybridMode.fromValue(
            SP.getInt(KEY.IPTV_HYBRID_MODE.name, IptvHybridMode.DISABLE.value)
        )
        set(value) = SP.putInt(KEY.IPTV_HYBRID_MODE.name, value.value)

    /** 相似频道合并 */
    var iptvSimilarChannelMerge: Boolean
        get() = SP.getBoolean(KEY.IPTV_SIMILAR_CHANNEL_MERGE.name, false)
        set(value) = SP.putBoolean(KEY.IPTV_SIMILAR_CHANNEL_MERGE.name, value)

    /** 频道图标提供 */
    var iptvChannelLogoProvider: String
        get() = SP.getString(KEY.IPTV_CHANNEL_LOGO_PROVIDER.name, Constants.CHANNEL_LOGO_PROVIDER)
        set(value) = SP.putString(KEY.IPTV_CHANNEL_LOGO_PROVIDER.name, value)

    /** 频道图标覆盖 */
    var iptvChannelLogoOverride: Boolean
        get() = SP.getBoolean(KEY.IPTV_CHANNEL_LOGO_OVERRIDE.name, false)
        set(value) = SP.putBoolean(KEY.IPTV_CHANNEL_LOGO_OVERRIDE.name, value)

    /** 是否启用直播源频道收藏 */
    var iptvChannelFavoriteEnable: Boolean
        get() = SP.getBoolean(KEY.IPTV_CHANNEL_FAVORITE_ENABLE.name, true)
        set(value) = SP.putBoolean(KEY.IPTV_CHANNEL_FAVORITE_ENABLE.name, value)

    /** 显示直播源频道收藏列表 */
    var iptvChannelFavoriteListVisible: Boolean
        get() = SP.getBoolean(KEY.IPTV_CHANNEL_FAVORITE_LIST_VISIBLE.name, false)
        set(value) = SP.putBoolean(KEY.IPTV_CHANNEL_FAVORITE_LIST_VISIBLE.name, value)

    /** 直播源频道收藏列表 */
    var iptvChannelFavoriteList: ChannelFavoriteList
        get() = Globals.json.decodeFromString(
            SP.getString(
                KEY.IPTV_CHANNEL_FAVORITE_LIST.name,
                Globals.json.encodeToString(ChannelFavoriteList())
            )
        )
        set(value) = SP.putString(
            KEY.IPTV_CHANNEL_FAVORITE_LIST.name,
            Globals.json.encodeToString(value)
        )

    /** 上一次播放频道 */
    var iptvChannelLastPlay: Channel
        get() = Globals.json.decodeFromString(
            SP.getString(
                KEY.IPTV_CHANNEL_LAST_PLAY.name,
                Globals.json.encodeToString(Channel.EMPTY)
            )
        )
        set(value) = SP.putString(
            KEY.IPTV_CHANNEL_LAST_PLAY.name,
            Globals.json.encodeToString(value)
        )

    /** 直播源线路可播放host列表 */
    var iptvChannelLinePlayableHostList: Set<String>
        get() = SP.getStringSet(KEY.IPTV_CHANNEL_LINE_PLAYABLE_HOST_LIST.name, emptySet())
        set(value) = SP.putStringSet(KEY.IPTV_CHANNEL_LINE_PLAYABLE_HOST_LIST.name, value)

    /** 直播源线路可播放地址列表 */
    // IPTV_CHANNEL_LINE_PLAYABLE_URL_LIST,
    var iptvChannelLinePlayableUrlList: Set<String>
        get() = SP.getStringSet(KEY.IPTV_CHANNEL_LINE_PLAYABLE_URL_LIST.name, emptySet())
        set(value) = SP.putStringSet(KEY.IPTV_CHANNEL_LINE_PLAYABLE_URL_LIST.name, value)

    /** 换台反转 - 写死关闭 */
    val iptvChannelChangeFlip: Boolean get() = false

    /** 数字选台 - 写死关闭 */
    val iptvChannelNoSelectEnable: Boolean get() = false

    /** 换台列表首尾循环 - 写死关闭 */
    val iptvChannelChangeListLoop: Boolean get() = false

    /** ==================== 节目单 ==================== */
    /** 启用节目单 (默认关闭) */
    var epgEnable: Boolean
        get() = SP.getBoolean(KEY.EPG_ENABLE.name, false)
        set(value) = SP.putBoolean(KEY.EPG_ENABLE.name, value)

    /** 当前节目单来源 */
    var epgSourceCurrent: EpgSource
        get() = Globals.json.decodeFromString(SP.getString(KEY.EPG_SOURCE_CURRENT.name, "")
            .ifBlank { Globals.json.encodeToString(Constants.EPG_SOURCE_LIST.first()) })
        set(value) = SP.putString(KEY.EPG_SOURCE_CURRENT.name, Globals.json.encodeToString(value))

    /** 节目单刷新时间阈值（小时） */
    var epgRefreshTimeThreshold: Int
        get() = SP.getInt(KEY.EPG_REFRESH_TIME_THRESHOLD.name, Constants.EPG_REFRESH_TIME_THRESHOLD)
        set(value) = SP.putInt(KEY.EPG_REFRESH_TIME_THRESHOLD.name, value)

    /** 节目单跟随直播源 */
    var epgSourceFollowIptv: Boolean
        get() = SP.getBoolean(KEY.EPG_SOURCE_FOLLOW_IPTV.name, false)
        set(value) = SP.putBoolean(KEY.EPG_SOURCE_FOLLOW_IPTV.name, value)

    /** 节目预约列表 */
    var epgChannelReserveList: EpgProgrammeReserveList
        get() = Globals.json.decodeFromString(
            SP.getString(
                KEY.EPG_CHANNEL_RESERVE_LIST.name,
                Globals.json.encodeToString(EpgProgrammeReserveList())
            )
        )
        set(value) = SP.putString(
            KEY.EPG_CHANNEL_RESERVE_LIST.name,
            Globals.json.encodeToString(value)
        )

    /** ==================== 界面（全部写死） ==================== */
    /** 显示节目进度 - 写死关闭 */
    val uiShowEpgProgrammeProgress: Boolean get() = false

    /** 显示台标 - 写死开启 */
    val uiShowChannelLogo: Boolean get() = true

    /** 显示频道预览 - 写死关闭 */
    val uiShowChannelPreview: Boolean get() = false

    /** 焦点优化 - 写死开启 */
    val uiFocusOptimize: Boolean get() = false

    /** ==================== 更新 ==================== */
    /** 更新强提醒 */
    var updateForceRemind: Boolean
        get() = SP.getBoolean(KEY.UPDATE_FORCE_REMIND.name, false)
        set(value) = SP.putBoolean(KEY.UPDATE_FORCE_REMIND.name, value)

    /** 更新通道 */
    var updateChannel: String
        get() = SP.getString(KEY.UPDATE_CHANNEL.name, "stable")
        set(value) = SP.putString(KEY.UPDATE_CHANNEL.name, value)

    /** ==================== 播放器 ==================== */
    /** 播放器 内核 */
    var videoPlayerCore: VideoPlayerCore
        get() = VideoPlayerCore.fromValue(
            SP.getInt(KEY.VIDEO_PLAYER_CORE.name, VideoPlayerCore.MEDIA3.value)
        )
        set(value) = SP.putInt(KEY.VIDEO_PLAYER_CORE.name, value.value)

    /** 播放器 渲染方式 — TextureView 支持透明度过渡，切换界面不割裂 */
    var videoPlayerRenderMode: VideoPlayerRenderMode
        get() = VideoPlayerRenderMode.fromValue(
            SP.getInt(KEY.VIDEO_PLAYER_RENDER_MODE.name, VideoPlayerRenderMode.TEXTURE_VIEW.value)
        )
        set(value) = SP.putInt(KEY.VIDEO_PLAYER_RENDER_MODE.name, value.value)

    /** 播放器 自定义ua */
    var videoPlayerUserAgent: String
        get() = SP.getString(KEY.VIDEO_PLAYER_USER_AGENT.name, "").ifBlank {
            Constants.VIDEO_PLAYER_USER_AGENT
        }
        set(value) = SP.putString(KEY.VIDEO_PLAYER_USER_AGENT.name, value)

    /** 播放器 自定义headers */
    var videoPlayerHeaders: String
        get() = SP.getString(KEY.VIDEO_PLAYER_HEADERS.name, "")
        set(value) = SP.putString(KEY.VIDEO_PLAYER_HEADERS.name, value)

    /** 播放器 加载超时 */
    var videoPlayerLoadTimeout: Long
        get() = SP.getLong(KEY.VIDEO_PLAYER_LOAD_TIMEOUT.name, Constants.VIDEO_PLAYER_LOAD_TIMEOUT)
        set(value) = SP.putLong(KEY.VIDEO_PLAYER_LOAD_TIMEOUT.name, value)

    /** 播放器 显示模式 */
    var videoPlayerDisplayMode: VideoPlayerDisplayMode
        get() = VideoPlayerDisplayMode.fromValue(
            SP.getInt(KEY.VIDEO_PLAYER_DISPLAY_MODE.name, VideoPlayerDisplayMode.ORIGINAL.value)
        )
        set(value) = SP.putInt(KEY.VIDEO_PLAYER_DISPLAY_MODE.name, value.value)

    /** 播放器 强制音频软解 */
    var videoPlayerForceAudioSoftDecode: Boolean
        get() = SP.getBoolean(KEY.VIDEO_PLAYER_FORCE_AUDIO_SOFT_DECODE.name, false)
        set(value) = SP.putBoolean(KEY.VIDEO_PLAYER_FORCE_AUDIO_SOFT_DECODE.name, value)

    /** 播放器 停止上一媒体项 */
    var videoPlayerStopPreviousMediaItem: Boolean
        get() = SP.getBoolean(KEY.VIDEO_PLAYER_STOP_PREVIOUS_MEDIA_ITEM.name, true)
        set(value) = SP.putBoolean(KEY.VIDEO_PLAYER_STOP_PREVIOUS_MEDIA_ITEM.name, value)

    /** 点播播放器 画面比例 (0=适应 1=裁剪 2=拉伸) */
    var vodPlayerResizeMode: Int
        get() = SP.getInt(KEY.VOD_PLAYER_RESIZE_MODE.name, 0)
        set(value) = SP.putInt(KEY.VOD_PLAYER_RESIZE_MODE.name, value)

    /** 点播 默认采集源 key (空=用内置默认) */
    var vodDefaultSourceKey: String
        get() = SP.getString(KEY.VOD_DEFAULT_SOURCE_KEY.name, "") ?: ""
        set(value) = SP.putString(KEY.VOD_DEFAULT_SOURCE_KEY.name, value)

    /** ==================== 主题 ==================== */
    /** 当前应用主题 */
    var themeAppCurrent: AppThemeDef?
        get() = SP.getString(KEY.THEME_APP_CURRENT.name, "").let {
            if (it.isBlank()) null else Globals.json.decodeFromString(it)
        }
        set(value) = SP.putString(
            KEY.THEME_APP_CURRENT.name,
            value?.let { Globals.json.encodeToString(value) } ?: "")

    /** 肥羊 AllInOne 文件路径 */
    var feiyangAllInOneFilePath: String
        get() = SP.getString(KEY.FEIYANG_ALLINONE_FILE_PATH.name, "")
        set(value) = SP.putString(KEY.FEIYANG_ALLINONE_FILE_PATH.name, value)

    /** ==================== 点播源 ==================== */
    /** 点播源列表 (仅用户添加, 内置源从Constants读取) */
    var vodSourceList: VodSourceList
        get() = Globals.json.decodeFromString(
            SP.getString(KEY.VOD_SOURCE_LIST.name, "")
                .ifBlank { Globals.json.encodeToString(VodSourceList()) }
        )
        set(value) = SP.putString(KEY.VOD_SOURCE_LIST.name, Globals.json.encodeToString(value))

    enum class IptvHybridMode(val value: Int, val label: String) {
        /** 禁用 */
        DISABLE(0, "禁用"),

        /** 直播源优先 */
        IPTV_FIRST(1, "直播源优先"),

        /** 混合优先 */
        HYBRID_FIRST(2, "混合优先");

        companion object {
            fun fromValue(value: Int): IptvHybridMode {
                return entries.firstOrNull { it.value == value } ?: DISABLE
            }
        }
    }

    enum class VideoPlayerCore(val value: Int, val label: String) {
        /** Media3 */
        MEDIA3(0, "Media3");

        companion object {
            fun fromValue(value: Int): VideoPlayerCore {
                return entries.firstOrNull { it.value == value } ?: MEDIA3
            }
        }
    }

    enum class VideoPlayerRenderMode(val value: Int, val label: String) {
        /** SurfaceView */
        SURFACE_VIEW(0, "SurfaceView"),

        /** TextureView */
        TEXTURE_VIEW(1, "TextureView");

        companion object {
            fun fromValue(value: Int): VideoPlayerRenderMode {
                return entries.firstOrNull { it.value == value } ?: SURFACE_VIEW
            }
        }
    }

    fun toPartial(): Partial {
        return Partial(
            appBootLaunch = appBootLaunch,
            appPipEnable = appPipEnable,
            appLastLatestVersion = appLastLatestVersion,
            appAgreementAgreed = appAgreementAgreed,
            appStartupScreen = appStartupScreen,
            debugShowFps = debugShowFps,
            debugShowVideoPlayerMetadata = debugShowVideoPlayerMetadata,
            debugShowLayoutGrids = debugShowLayoutGrids,
            iptvSourceCacheTime = iptvSourceCacheTime,
            iptvSourceCurrent = iptvSourceCurrent,
            iptvSourceList = iptvSourceList,
            iptvChannelGroupHiddenList = iptvChannelGroupHiddenList,
            iptvChannelHiddenList = iptvChannelHiddenList,
            iptvHybridMode = iptvHybridMode,
            iptvSimilarChannelMerge = iptvSimilarChannelMerge,
            iptvChannelLogoProvider = iptvChannelLogoProvider,
            iptvChannelLogoOverride = iptvChannelLogoOverride,
            iptvChannelFavoriteEnable = iptvChannelFavoriteEnable,
            iptvChannelFavoriteListVisible = iptvChannelFavoriteListVisible,
            iptvChannelFavoriteList = iptvChannelFavoriteList,
            iptvChannelLastPlay = iptvChannelLastPlay,
            iptvChannelLinePlayableHostList = iptvChannelLinePlayableHostList,
            iptvChannelLinePlayableUrlList = iptvChannelLinePlayableUrlList,
            iptvChannelChangeFlip = iptvChannelChangeFlip,
            iptvChannelChangeListLoop = iptvChannelChangeListLoop,
            epgEnable = epgEnable,
            epgSourceCurrent = epgSourceCurrent,
            epgRefreshTimeThreshold = epgRefreshTimeThreshold,
            epgSourceFollowIptv = epgSourceFollowIptv,
            epgChannelReserveList = epgChannelReserveList,
            uiShowEpgProgrammeProgress = uiShowEpgProgrammeProgress,
            uiShowChannelLogo = uiShowChannelLogo,
            uiShowChannelPreview = uiShowChannelPreview,
            uiFocusOptimize = uiFocusOptimize,
            updateForceRemind = updateForceRemind,
            updateChannel = updateChannel,
            videoPlayerCore = videoPlayerCore,
            videoPlayerRenderMode = videoPlayerRenderMode,
            videoPlayerUserAgent = videoPlayerUserAgent,
            videoPlayerHeaders = videoPlayerHeaders,
            videoPlayerLoadTimeout = videoPlayerLoadTimeout,
            videoPlayerDisplayMode = videoPlayerDisplayMode,
            videoPlayerForceAudioSoftDecode = videoPlayerForceAudioSoftDecode,
            videoPlayerStopPreviousMediaItem = videoPlayerStopPreviousMediaItem,
            vodPlayerResizeMode = vodPlayerResizeMode,
            vodDefaultSourceKey = vodDefaultSourceKey,
            themeAppCurrent = themeAppCurrent,
            feiyangAllInOneFilePath = feiyangAllInOneFilePath,
        )
    }

    fun fromPartial(configs: Partial) {
        configs.appBootLaunch?.let { appBootLaunch = it }
        configs.appPipEnable?.let { appPipEnable = it }
        configs.appLastLatestVersion?.let { appLastLatestVersion = it }
        configs.appAgreementAgreed?.let { appAgreementAgreed = it }
        configs.appStartupScreen?.let { appStartupScreen = it }
        configs.iptvSourceCacheTime?.let { iptvSourceCacheTime = it }
        configs.iptvSourceCurrent?.let { iptvSourceCurrent = it }
        configs.iptvSourceList?.let { iptvSourceList = it }
        configs.iptvChannelGroupHiddenList?.let { iptvChannelGroupHiddenList = it }
        configs.iptvChannelHiddenList?.let { iptvChannelHiddenList = it }
        configs.iptvHybridMode?.let { iptvHybridMode = it }
        configs.iptvSimilarChannelMerge?.let { iptvSimilarChannelMerge = it }
        configs.iptvChannelLogoProvider?.let { iptvChannelLogoProvider = it }
        configs.iptvChannelLogoOverride?.let { iptvChannelLogoOverride = it }
        configs.iptvChannelFavoriteEnable?.let { iptvChannelFavoriteEnable = it }
        configs.iptvChannelFavoriteListVisible?.let { iptvChannelFavoriteListVisible = it }
        configs.iptvChannelFavoriteList?.let { iptvChannelFavoriteList = it }
        configs.iptvChannelLastPlay?.let { iptvChannelLastPlay = it }
        configs.iptvChannelLinePlayableHostList?.let { iptvChannelLinePlayableHostList = it }
        configs.iptvChannelLinePlayableUrlList?.let { iptvChannelLinePlayableUrlList = it }
        configs.epgEnable?.let { epgEnable = it }
        configs.epgSourceCurrent?.let { epgSourceCurrent = it }
        configs.epgRefreshTimeThreshold?.let { epgRefreshTimeThreshold = it }
        configs.epgSourceFollowIptv?.let { epgSourceFollowIptv = it }
        configs.epgChannelReserveList?.let { epgChannelReserveList = it }
        configs.updateForceRemind?.let { updateForceRemind = it }
        configs.updateChannel?.let { updateChannel = it }
        configs.videoPlayerCore?.let { videoPlayerCore = it }
        configs.videoPlayerRenderMode?.let { videoPlayerRenderMode = it }
        configs.videoPlayerUserAgent?.let { videoPlayerUserAgent = it }
        configs.videoPlayerHeaders?.let { videoPlayerHeaders = it }
        configs.videoPlayerLoadTimeout?.let { videoPlayerLoadTimeout = it }
        configs.videoPlayerDisplayMode?.let { videoPlayerDisplayMode = it }
        configs.videoPlayerForceAudioSoftDecode?.let { videoPlayerForceAudioSoftDecode = it }
        configs.videoPlayerStopPreviousMediaItem?.let { videoPlayerStopPreviousMediaItem = it }
        configs.vodPlayerResizeMode?.let { vodPlayerResizeMode = it }
        configs.vodDefaultSourceKey?.let { vodDefaultSourceKey = it }
        configs.themeAppCurrent?.let { themeAppCurrent = it }
        configs.feiyangAllInOneFilePath?.let { feiyangAllInOneFilePath = it }
    }

    @Serializable
    data class Partial(
        val appBootLaunch: Boolean? = null,
        val appPipEnable: Boolean? = null,
        val appLastLatestVersion: String? = null,
        val appAgreementAgreed: Boolean? = null,
        val appStartupScreen: String? = null,
        val debugShowFps: Boolean? = null,
        val debugShowVideoPlayerMetadata: Boolean? = null,
        val debugShowLayoutGrids: Boolean? = null,
        val iptvSourceCacheTime: Long? = null,
        val iptvSourceCurrent: IptvSource? = null,
        val iptvSourceList: IptvSourceList? = null,
        val iptvChannelGroupHiddenList: Set<String>? = null,
        val iptvChannelHiddenList: Set<String>? = null,
        val iptvHybridMode: IptvHybridMode? = null,
        val iptvSimilarChannelMerge: Boolean? = null,
        val iptvChannelLogoProvider: String? = null,
        val iptvChannelLogoOverride: Boolean? = null,
        val iptvChannelFavoriteEnable: Boolean? = null,
        val iptvChannelFavoriteListVisible: Boolean? = null,
        val iptvChannelFavoriteList: ChannelFavoriteList? = null,
        val iptvChannelLastPlay: Channel? = null,
        val iptvChannelLinePlayableHostList: Set<String>? = null,
        val iptvChannelLinePlayableUrlList: Set<String>? = null,
        val iptvChannelChangeFlip: Boolean? = null,
        val iptvChannelChangeListLoop: Boolean? = null,
        val epgEnable: Boolean? = null,
        val epgSourceCurrent: EpgSource? = null,
        val epgRefreshTimeThreshold: Int? = null,
        val epgSourceFollowIptv: Boolean? = null,
        val epgChannelReserveList: EpgProgrammeReserveList? = null,
        val uiShowEpgProgrammeProgress: Boolean? = null,
        val uiShowChannelLogo: Boolean? = null,
        val uiShowChannelPreview: Boolean? = null,
        val uiFocusOptimize: Boolean? = null,
        val updateForceRemind: Boolean? = null,
        val updateChannel: String? = null,
        val videoPlayerCore: VideoPlayerCore? = null,
        val videoPlayerRenderMode: VideoPlayerRenderMode? = null,
        val videoPlayerUserAgent: String? = null,
        val videoPlayerHeaders: String? = null,
        val videoPlayerLoadTimeout: Long? = null,
        val videoPlayerDisplayMode: VideoPlayerDisplayMode? = null,
        val videoPlayerForceAudioSoftDecode: Boolean? = null,
        val videoPlayerStopPreviousMediaItem: Boolean? = null,
        val vodPlayerResizeMode: Int? = null,
        val vodDefaultSourceKey: String? = null,
        val themeAppCurrent: AppThemeDef? = null,
        val feiyangAllInOneFilePath: String? = null,
    ) {
        fun desensitized() = copy(
            iptvChannelLastPlay = null,
            iptvChannelLinePlayableHostList = null,
            iptvChannelLinePlayableUrlList = null,
        )
    }
}