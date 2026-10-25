package top.yogiczy.mytv.tv.ui.screensold.main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelFirstOrNull
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelGroupIdx
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelIdx
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelLastOrNull
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelList
import top.yogiczy.mytv.core.data.entities.channel.ChannelLine
import top.yogiczy.mytv.core.data.entities.channel.ChannelLineList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeReserve
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeReserveList
import top.yogiczy.mytv.core.data.utils.ChannelUtil
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.data.utils.Loggable
import top.yogiczy.mytv.core.util.utils.urlHost
import top.yogiczy.mytv.tv.ui.utils.Configs
import top.yogiczy.mytv.tv.ui.utils.IptvSourcePriority
import top.yogiczy.mytv.tv.migu.MiguFallbackActivity
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.VideoPlayerState
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.VideoPlayer
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.rememberVideoPlayerState
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Stable
class MainContentState(
    private val coroutineScope: CoroutineScope,
    private val videoPlayerState: VideoPlayerState,
    private val channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    private val favoriteChannelListProvider: () -> ChannelList = { ChannelList() },
    private val settingsViewModel: SettingsViewModel,
    private val onReloadChannelList: () -> Unit = {},
) : Loggable("MainContentState") {
    private var _currentChannel by mutableStateOf(Channel())
    val currentChannel get() = _currentChannel

    private var _currentChannelLineIdx by mutableIntStateOf(0)
    val currentChannelLineIdx get() = _currentChannelLineIdx

    val currentChannelLine get() = _currentChannel.lineList.getOrNull(_currentChannelLineIdx) ?: ChannelLine()

    private var _currentPlaybackEpgProgramme by mutableStateOf<EpgProgramme?>(null)
    val currentPlaybackEpgProgramme get() = _currentPlaybackEpgProgramme

    private var _tempChannelScreenHideJob: Job? = null

    private var _isTempChannelScreenVisible by mutableStateOf(false)
    var isTempChannelScreenVisible
        get() = _isTempChannelScreenVisible
        set(value) {
            _isTempChannelScreenVisible = value
        }

    private var _isChannelScreenVisible by mutableStateOf(false)
    var isChannelScreenVisible
        get() = _isChannelScreenVisible
        set(value) {
            _isChannelScreenVisible = value
        }

    // ==================== 直播源容灾 ====================

    /** 换源遮罩层是否可见 */
    private var _isSourceSwitching by mutableStateOf(false)
    val isSourceSwitching get() = _isSourceSwitching

    /** 换源遮罩提示文案 */
    private var _sourceSwitchMessage by mutableStateOf("正在尝试更换直播源")
    val sourceSwitchMessage get() = _sourceSwitchMessage

    /** 网络故障蓝屏是否可见 */
    private var _isNetworkError by mutableStateOf(false)
    val isNetworkError get() = _isNetworkError

    /** 换源流程 Job */
    private var _recoveryJob: Job? = null

    /** 用户是否在换源过程中主动打断 */
    private var _recoveryAborted = false

    private var _isVideoPlayerControllerScreenVisible by mutableStateOf(false)
    var isVideoPlayerControllerScreenVisible
        get() = _isVideoPlayerControllerScreenVisible
        set(value) {
            _isVideoPlayerControllerScreenVisible = value
        }

    private var _isQuickOpScreenVisible by mutableStateOf(false)
    var isQuickOpScreenVisible
        get() = _isQuickOpScreenVisible
        set(value) {
            _isQuickOpScreenVisible = value
        }

    private var _isEpgScreenVisible by mutableStateOf(false)
    var isEpgScreenVisible
        get() = _isEpgScreenVisible
        set(value) {
            _isEpgScreenVisible = value
        }

    private var _isChannelLineScreenVisible by mutableStateOf(false)
    var isChannelLineScreenVisible
        get() = _isChannelLineScreenVisible
        set(value) {
            _isChannelLineScreenVisible = value
        }

    private var _isVideoPlayerDisplayModeScreenVisible by mutableStateOf(false)
    var isVideoPlayerDisplayModeScreenVisible
        get() = _isVideoPlayerDisplayModeScreenVisible
        set(value) {
            _isVideoPlayerDisplayModeScreenVisible = value
        }

    private var _isVideoTracksScreenVisible by mutableStateOf(false)
    var isVideoTracksScreenVisible
        get() = _isVideoTracksScreenVisible
        set(value) {
            _isVideoTracksScreenVisible = value
        }

    private var _isAudioTracksScreenVisible by mutableStateOf(false)
    var isAudioTracksScreenVisible
        get() = _isAudioTracksScreenVisible
        set(value) {
            _isAudioTracksScreenVisible = value
        }

    private var _isSubtitleTracksScreenVisible by mutableStateOf(false)
    var isSubtitleTracksScreenVisible
        get() = _isSubtitleTracksScreenVisible
        set(value) {
            _isSubtitleTracksScreenVisible = value
        }

    init {
        val channelGroupList = channelGroupListProvider()

        changeCurrentChannel(settingsViewModel.iptvChannelLastPlay.isEmptyOrElse {
            channelGroupList.channelFirstOrNull() ?: Channel.EMPTY
        })

        videoPlayerState.onReady {
            settingsViewModel.iptvChannelLinePlayableUrlList += currentChannelLine.url
            settingsViewModel.iptvChannelLinePlayableHostList += currentChannelLine.url.urlHost()
        }

        videoPlayerState.onError {
            if (_currentPlaybackEpgProgramme != null) return@onError
            // 频道列表打开时不触发兜底（用户正在选台）
            if (_isChannelScreenVisible) return@onError
            // 换源/蓝屏流程进行中，忽略新的错误
            if (_recoveryJob?.isActive == true) return@onError

            settingsViewModel.iptvChannelLinePlayableUrlList -= currentChannelLine.url
            settingsViewModel.iptvChannelLinePlayableHostList -= currentChannelLine.url.urlHost()

            if (_currentChannelLineIdx < _currentChannel.lineList.size - 1) {
                changeCurrentChannel(_currentChannel, _currentChannelLineIdx + 1)
            } else {
                // 当前频道所有线路都失败 → 启动容灾流程
                startSourceRecovery()
            }
        }

        videoPlayerState.onInterrupt {
            changeCurrentChannel(
                _currentChannel,
                _currentChannelLineIdx,
                _currentPlaybackEpgProgramme
            )
        }

        videoPlayerState.onIsBuffering { isBuffering ->
            if (isBuffering) {
                _isTempChannelScreenVisible = true
            } else {
                _tempChannelScreenHideJob?.cancel()
                _tempChannelScreenHideJob = coroutineScope.launch {
                    val name = _currentChannel.name
                    val lineIdx = _currentChannelLineIdx
                    delay(Constants.UI_TEMP_CHANNEL_SCREEN_SHOW_DURATION)
                    if (name == _currentChannel.name && lineIdx == _currentChannelLineIdx) {
                        _isTempChannelScreenVisible = false
                    }
                }
            }
        }
    }

    private fun getPrevFavoriteChannel(): Channel? {
        if (!settingsViewModel.iptvChannelFavoriteListVisible) return null

        val channelGroupList = channelGroupListProvider()
        val favoriteChannelList = favoriteChannelListProvider()

        if (_currentChannel !in favoriteChannelList) return null

        val currentIdx = favoriteChannelList.indexOf(_currentChannel)

        return favoriteChannelList.getOrElse(currentIdx - 1) {
            if (settingsViewModel.iptvChannelChangeListLoop) favoriteChannelList.lastOrNull()
            else channelGroupList.channelLastOrNull()
        }
    }

    private fun getNextFavoriteChannel(): Channel? {
        if (!settingsViewModel.iptvChannelFavoriteListVisible) return null

        val channelGroupList = channelGroupListProvider()
        val favoriteChannelList = favoriteChannelListProvider()

        if (_currentChannel !in favoriteChannelList) return null

        val currentIdx = favoriteChannelList.indexOf(_currentChannel)

        return favoriteChannelList.getOrElse(currentIdx + 1) {
            if (settingsViewModel.iptvChannelChangeListLoop) favoriteChannelList.firstOrNull()
            else channelGroupList.channelFirstOrNull()
        }
    }

    private fun getPrevChannel(): Channel {
        return getPrevFavoriteChannel() ?: run {
            val channelGroupList = channelGroupListProvider()
            return if (settingsViewModel.iptvChannelChangeListLoop) {
                val group =
                    channelGroupList.getOrElse(channelGroupList.channelGroupIdx(_currentChannel)) { channelGroupList.first() }
                val currentIdx = group.channelList.indexOf(_currentChannel)
                group.channelList.getOrElse(currentIdx - 1) { group.channelList.last() }
            } else {
                val currentIdx = channelGroupList.channelIdx(_currentChannel)
                channelGroupList.channelList.getOrElse(currentIdx - 1) {
                    channelGroupList.channelLastOrNull() ?: Channel()
                }
            }
        }
    }

    private fun getNextChannel(): Channel {
        return getNextFavoriteChannel() ?: run {
            val channelGroupList = channelGroupListProvider()
            return if (settingsViewModel.iptvChannelChangeListLoop) {
                val group =
                    channelGroupList.getOrElse(channelGroupList.channelGroupIdx(_currentChannel)) { channelGroupList.first() }
                val currentIdx = group.channelList.indexOf(_currentChannel)
                group.channelList.getOrElse(currentIdx + 1) { group.channelList.first() }
            } else {
                val currentIdx = channelGroupList.channelIdx(_currentChannel)
                channelGroupList.channelList.getOrElse(currentIdx + 1) {
                    channelGroupList.channelFirstOrNull() ?: Channel()
                }
            }
        }
    }

    private fun getLineIdx(lineList: ChannelLineList, lineIdx: Int? = null): Int {
        if (lineList.isEmpty()) return 0
        val idx = if (lineIdx == null) {
            val idx = lineList.indexOfFirst { line ->
                settingsViewModel.iptvChannelLinePlayableUrlList.contains(line.url)
            }

            if (idx < 0) {
                lineList.indexOfFirst { line ->
                    settingsViewModel.iptvChannelLinePlayableHostList.contains(line.url.urlHost())
                }
            } else idx
        } else (lineIdx + lineList.size) % lineList.size

        return max(0, min(idx, lineList.size - 1))
    }

    fun changeCurrentChannel(
        channel: Channel,
        lineIdx: Int? = null,
        playbackEpgProgramme: EpgProgramme? = null,
    ) {
        settingsViewModel.iptvChannelLastPlay = channel

        if (channel == _currentChannel && lineIdx == _currentChannelLineIdx && playbackEpgProgramme == _currentPlaybackEpgProgramme) return

        if (channel == _currentChannel && lineIdx != _currentChannelLineIdx) {
            settingsViewModel.iptvChannelLinePlayableUrlList -= currentChannelLine.url
            settingsViewModel.iptvChannelLinePlayableHostList -= currentChannelLine.url.urlHost()
        }

        _isTempChannelScreenVisible = true

        _currentChannel = channel
        _currentChannelLineIdx = getLineIdx(_currentChannel.lineList, lineIdx)

        _currentPlaybackEpgProgramme = playbackEpgProgramme

        var url = currentChannelLine.playableUrl
        if (_currentPlaybackEpgProgramme != null) {
            val timeFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
            val query = listOf(
                "playseek=",
                timeFormat.format(_currentPlaybackEpgProgramme!!.startAt),
                "-",
                timeFormat.format(_currentPlaybackEpgProgramme!!.endAt),
            ).joinToString("")
            url = if (URI(url).query.isNullOrBlank()) "$url?$query" else "$url&$query"
            url = ChannelUtil.urlToCanPlayback(url)
        }
        val line = currentChannelLine.copy(url = url)

        log.d("播放${_currentChannel.name}（${_currentChannelLineIdx + 1}/${_currentChannel.lineList.size}）: $line")

        if (line.url.startsWith("webview://")) {
            videoPlayerState.metadata = VideoPlayer.Metadata()
            videoPlayerState.stop()
        } else {
            videoPlayerState.prepare(line)
        }
    }

    fun changeCurrentChannelToPrev() {
        changeCurrentChannel(getPrevChannel())
    }

    fun changeCurrentChannelToNext() {
        changeCurrentChannel(getNextChannel())
    }

    fun reverseEpgProgrammeOrNot(channel: Channel, programme: EpgProgramme) {
        val reverse = settingsViewModel.epgChannelReserveList.firstOrNull {
            it.test(channel, programme)
        }

        if (reverse != null) {
            settingsViewModel.epgChannelReserveList =
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList - reverse)
            Snackbar.show("取消预约：${reverse.channel} - ${reverse.programme}")
        } else {
            val newReserve = EpgProgrammeReserve(
                channel = channel.name,
                programme = programme.title,
                startAt = programme.startAt,
                endAt = programme.endAt,
            )

            settingsViewModel.epgChannelReserveList =
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList + newReserve)
            Snackbar.show("已预约：${channel.name} - ${programme.title}")
        }
    }

    fun supportPlayback(
        channel: Channel = _currentChannel,
        lineIdx: Int? = _currentChannelLineIdx,
    ): Boolean {
        val currentLineIdx = getLineIdx(channel.lineList, lineIdx)
        return ChannelUtil.urlSupportPlayback(channel.lineList[currentLineIdx].url)
    }

    // ==================== 直播源容灾实现 ====================

    /** 用户主动打断换源（OK / 返回 / 换台） */
    fun cancelSourceRecovery() {
        _recoveryAborted = true
        _recoveryJob?.cancel()
        _recoveryJob = null
        _isSourceSwitching = false
        _isNetworkError = false
        _isChannelScreenVisible = true
    }

    /** 蓝屏「有反应」：关闭蓝屏回到频道列表 */
    fun dismissNetworkError() {
        _isNetworkError = false
        _isChannelScreenVisible = true
    }

    private fun startSourceRecovery() {
        if (_recoveryJob?.isActive == true) return
        _recoveryAborted = false
        _recoveryJob = coroutineScope.launch {
            val deadline = System.currentTimeMillis() + Constants.IPTV_SOURCE_SWITCH_TOTAL_TIMEOUT
            try {
                // 1) 静默期：先原地重试当前频道（从第 1 条线路开始）
                _isTempChannelScreenVisible = false
                log.w("频道所有线路失败，进入容灾: ${_currentChannel.name}")
                changeCurrentChannel(_currentChannel, 0)
                if (awaitPlaying(Constants.IPTV_SOURCE_FAIL_SILENT_DELAY)) {
                    log.i("静默重试成功，取消换源")
                    return@launch
                }
                if (_recoveryAborted) return@launch

                // 2) 显示遮罩
                _isSourceSwitching = true
                _sourceSwitchMessage = "正在尝试更换直播源"

                // 3) 选目标源
                //    当前是内置咪咕 → 并发探测备用源取最快
                //    当前不是内置咪咕 → 直接切回内置咪咕（保证有画面）
                val currentIsMigu =
                    top.yogiczy.mytv.tv.ui.utils.IptvSourcePriority.isBuiltinMigu(
                        settingsViewModel.iptvSourceCurrent
                    )
                val candidates = mutableListOf<top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource>()
                if (currentIsMigu) {
                    probeFastestBackup(deadline)?.let { candidates.add(it) }
                    candidates.add(top.yogiczy.mytv.tv.ui.utils.IptvSourcePriority.builtinMigu())
                } else {
                    candidates.add(top.yogiczy.mytv.tv.ui.utils.IptvSourcePriority.builtinMigu())
                    probeFastestBackup(deadline)?.let { candidates.add(it) }
                }

                for (source in candidates) {
                    if (_recoveryAborted) return@launch
                    if (System.currentTimeMillis() > deadline) break
                    _sourceSwitchMessage = "正在切换到：${source.name}"
                    if (switchSourceAndPlay(source)) {
                        log.i("换源成功: ${source.name}")
                        _isSourceSwitching = false
                        return@launch
                    }
                }

                // 4) 全部失败 → WebView 兜底
                if (_recoveryAborted) return@launch
                log.e("所有源均失败，进入 WebView 兜底")
                goWebViewFallback()
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 用户打断，正常退出
            } catch (e: Throwable) {
                log.e("换源流程异常: ${e.message}")
                _isSourceSwitching = false
                goWebViewFallback()
            }
        }
    }

    /** 等待播放真正出画面 */
    private suspend fun awaitPlaying(timeoutMs: Long): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (videoPlayerState.isPlaying && videoPlayerState.currentPosition > 0) return true
            delay(300)
        }
        return false
    }

    /** 并发探测备用源，返回拉取耗时最短且成功的那个 */
    private suspend fun probeFastestBackup(
        deadline: Long
    ): top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource? {
        val currentUrl = settingsViewModel.iptvSourceCurrent.url
        val backups = IptvSourcePriority.effectiveBackups().filterNot { it.url == currentUrl }
        if (backups.isEmpty()) return null
        if (System.currentTimeMillis() >= deadline) return null

        return coroutineScope {
            val startedAt = System.currentTimeMillis()
            val jobs = backups.map { src ->
                async(Dispatchers.IO) {
                    val t0 = System.currentTimeMillis()
                    val ok = withTimeoutOrNull(Constants.IPTV_SOURCE_PROBE_TIMEOUT) {
                        runCatching {
                            top.yogiczy.mytv.core.data.repositories.iptv.IptvRepository(src)
                                .getChannelGroupList(Configs.iptvSourceCacheTime)
                                .isNotEmpty()
                        }.getOrDefault(false)
                    } ?: false
                    if (ok) src to (System.currentTimeMillis() - t0) else null
                }
            }
            val done = jobs.mapNotNull { runCatching { it.await() }.getOrNull() }
            log.i(
                "备用源探测完成 ${System.currentTimeMillis() - startedAt}ms: " +
                    backups.joinToString { it.name } +
                    " → 可用 ${done.size} 个"
            )
            done.minByOrNull { it.second }?.first
        }
    }

    /** 切到指定源并播放（优先同名频道，否则播该源第一个频道） */
    private suspend fun switchSourceAndPlay(
        source: top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource,
    ): Boolean {
        val prevChannel = _currentChannel
        settingsViewModel.iptvSourceCurrent = source
        onReloadChannelList()

        val list = awaitChannelList(Constants.IPTV_SOURCE_LOAD_TIMEOUT) ?: return false
        if (_recoveryAborted) return false

        val all = list.channelList.filter { it.lineList.isNotEmpty() }
        if (all.isEmpty()) return false

        // 1) 同名（标准名 / 原名） 2) 找不到就播第一个，保证有画面
        val target = all.firstOrNull {
            it.standardName.isNotBlank() && it.standardName == prevChannel.standardName
        } ?: all.firstOrNull { it.name == prevChannel.name }
        ?: all.first()

        log.i("换源后播放: ${target.name}（源 ${source.name}）")
        changeCurrentChannel(target, 0)
        return awaitPlaying(Constants.IPTV_SOURCE_LOAD_TIMEOUT)
    }

    /** 等待新源的频道列表加载完成 */
    private suspend fun awaitChannelList(timeoutMs: Long): ChannelGroupList? {
        val end = System.currentTimeMillis() + timeoutMs
        delay(300) // 给 UI 状态一个切换到 Loading 的窗口
        while (System.currentTimeMillis() < end) {
            val list = channelGroupListProvider()
            if (list.isNotEmpty()) return list
            delay(200)
        }
        return null
    }

    /** WebView 兜底（仅咪咕频道可用） */
    private fun goWebViewFallback() {
        _isSourceSwitching = false
        _isTempChannelScreenVisible = false

        val baseUrl = currentChannelLine.url
        if (!baseUrl.contains("/migu/")) {
            log.e("当前频道非咪咕源，无法 WebView 兜底 → 蓝屏")
            showNetworkError()
            return
        }
        val fallbackUrl = baseUrl.replace("/migu/", "/migufb/")
        val channelName = _currentChannel.name

        MiguFallbackActivity.onRequestChannelList = {
            _isChannelScreenVisible = true
        }
        MiguFallbackActivity.onPlaybackFailed = {
            showNetworkError()
        }
        // WebView 里按方向键换台 → 回到主 app 的直播源播放器（不再停留在 WebView）
        MiguFallbackActivity.onSwitchChannel = { prev ->
            _isSourceSwitching = false
            _isNetworkError = false
            isChannelScreenVisible = false
            if (prev) changeCurrentChannelToPrev() else changeCurrentChannelToNext()
        }
        MiguFallbackActivity.startActivity(Globals.appContext, fallbackUrl, channelName)
    }

    /** 蓝屏 */
    private fun showNetworkError() {
        _isSourceSwitching = false
        _isTempChannelScreenVisible = false
        _isNetworkError = true
        _recoveryJob = null
    }

}

@Composable
fun rememberMainContentState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    videoPlayerState: VideoPlayerState = rememberVideoPlayerState(),
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    favoriteChannelListProvider: () -> ChannelList = { ChannelList() },
    settingsViewModel: SettingsViewModel = settingsVM,
    onReloadChannelList: () -> Unit = {},
): MainContentState {
    val favoriteChannelListProviderUpdated by rememberUpdatedState(favoriteChannelListProvider)
    val reloadUpdated by rememberUpdatedState(onReloadChannelList)

    return remember(settingsVM.videoPlayerCore) {
        MainContentState(
            coroutineScope = coroutineScope,
            videoPlayerState = videoPlayerState,
            channelGroupListProvider = channelGroupListProvider,
            favoriteChannelListProvider = favoriteChannelListProviderUpdated,
            settingsViewModel = settingsViewModel,
            onReloadChannelList = reloadUpdated,
        )
    }
}