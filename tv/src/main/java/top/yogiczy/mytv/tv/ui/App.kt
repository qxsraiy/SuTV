package top.yogiczy.mytv.tv.ui

import androidx.annotation.IntRange
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import top.yogiczy.mytv.allinone.AllInOne
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource.Companion.needExternalStoragePermission
import top.yogiczy.mytv.tv.ui.material.Padding
import top.yogiczy.mytv.tv.ui.material.PopupHandleableApplication
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.SnackbarType
import top.yogiczy.mytv.tv.ui.material.SnackbarUI
import top.yogiczy.mytv.tv.ui.material.Visibility
import top.yogiczy.mytv.tv.ui.screen.main.MainScreen
import top.yogiczy.mytv.tv.ui.screen.monitor.MonitorPopup
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.theme.DESIGN_WIDTH
import top.yogiczy.mytv.tv.ui.theme.SAFE_AREA_HORIZONTAL_PADDING
import top.yogiczy.mytv.tv.ui.theme.SAFE_AREA_VERTICAL_PADDING
import top.yogiczy.mytv.tv.ui.tooling.PreviewWithLayoutGrids
import top.yogiczy.mytv.tv.ui.utils.rememberReadExternalStoragePermission
import java.io.File

@Composable
fun App(
    modifier: Modifier = Modifier,
    settingsViewModel: SettingsViewModel = settingsVM,
    onBackPressed: () -> Unit = {},
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val doubleBackPressedExitState = rememberDoubleBackPressedExitState()

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = LocalDensity.current.density * configuration.screenWidthDp.toFloat() / DESIGN_WIDTH,
            fontScale = LocalDensity.current.fontScale,
        ),
    ) {
        PopupHandleableApplication {
            MainScreen(
                modifier = modifier,
                onBackPressed = {
                    if (doubleBackPressedExitState.allowExit) {
                        onBackPressed()
                    } else {
                        doubleBackPressedExitState.backPress()
                        Snackbar.show("再按一次退出")
                    }
                },
            )
        }

        SnackbarUI()
        Visibility({ settingsViewModel.debugShowFps }) { MonitorPopup() }
        Visibility({ settingsViewModel.debugShowLayoutGrids }) { PreviewWithLayoutGrids { } }
    }

    // 一次性迁移：清掉可能含「旧局域网 IP」的咪咕播放列表缓存。
    // 旧版本生成 /migu.m3u 时用的是局域网 IP，IP 变了就会让播放器连不上 → Media3 2001。
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val sp = context.getSharedPreferences("app_migrate", android.content.Context.MODE_PRIVATE)
                if (!sp.getBoolean("migu_loopback_fix_v1", false)) {
                    top.yogiczy.mytv.core.data.repositories.iptv.IptvRepository(
                        top.yogiczy.mytv.tv.ui.utils.IptvSourcePriority.builtinMigu()
                    ).clearCache()
                    sp.edit().putBoolean("migu_loopback_fix_v1", true).apply()
                }
            }
        }
    }

    // 启动时清理一次本地过期 apk（版本 <= 当前版本的 apk 没有安装价值，避免堆积）
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                top.yogiczy.mytv.tv.utlis.ApkCleaner.cleanObsoleteApks(
                    context = context,
                    currentVersion = top.yogiczy.mytv.tv.BuildConfig.VERSION_NAME,
                )
            }
        }
    }

    if (settingsViewModel.iptvSourceCurrent.needExternalStoragePermission()) {
        val (hasPermission, requestPermission) = rememberReadExternalStoragePermission()
        LaunchedEffect(Unit) { if (!hasPermission) requestPermission() }
    }

    LaunchedEffect(settingsViewModel.iptvSourceCurrent) {
        if (settingsViewModel.feiyangAllInOneFilePath.isNotBlank()) {
            AllInOne.start(
                context,
                settingsViewModel.feiyangAllInOneFilePath,
                onFail = {
                    Snackbar.show("二进制 启动失败", type = SnackbarType.ERROR)
                },
                onUnsupported = {
                    Snackbar.show("二进制 不支持当前平台", type = SnackbarType.ERROR)
                },
            )
        }
    }
}

/**
 * 退出应用二次确认
 */
class DoubleBackPressedExitState internal constructor(
    @IntRange(from = 0)
    private val resetSeconds: Int,
) {
    private var _allowExit by mutableStateOf(false)
    val allowExit get() = _allowExit

    fun backPress() {
        _allowExit = true
        channel.trySend(resetSeconds)
    }

    private val channel = Channel<Int>(Channel.CONFLATED)

    @OptIn(FlowPreview::class)
    suspend fun observe() {
        channel.consumeAsFlow()
            .debounce { it.toLong() * 1000 }
            .collect { _allowExit = false }
    }
}

/**
 * 退出应用二次确认状态
 */
@Composable
fun rememberDoubleBackPressedExitState(@IntRange(from = 0) resetSeconds: Int = 2) =
    remember { DoubleBackPressedExitState(resetSeconds = resetSeconds) }
        .also { LaunchedEffect(it) { it.observe() } }

val ParentPadding = PaddingValues(
    vertical = SAFE_AREA_VERTICAL_PADDING.dp,
    horizontal = SAFE_AREA_HORIZONTAL_PADDING.dp,
)

@Composable
fun rememberChildPadding(direction: LayoutDirection = LocalLayoutDirection.current) =
    remember {
        Padding(
            start = ParentPadding.calculateStartPadding(direction),
            top = ParentPadding.calculateTopPadding(),
            end = ParentPadding.calculateEndPadding(direction),
            bottom = ParentPadding.calculateBottomPadding()
        )
    }
