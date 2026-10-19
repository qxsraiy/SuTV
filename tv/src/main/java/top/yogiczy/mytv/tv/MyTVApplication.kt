package top.yogiczy.mytv.tv

import android.app.Application
import android.content.Intent
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.util.DebugLogger
import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import top.yogiczy.mytv.core.data.AppData
import top.yogiczy.mytv.core.data.utils.Globals
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MyTVApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()

        Globals.appContext = applicationContext
        // 关键初始化 (同步)
        UnsafeTrustManager.enableUnsafeTrustManager()
        top.yogiczy.mytv.tv.utlis.NetworkMonitor.register(applicationContext)
        // 非关键初始化 (延迟到首帧后, 加速启动)
        coroutineScope.launch(Dispatchers.IO) {
            initSentry()
            crashHandle()
            AppData.init(applicationContext)
            // 图片缓存清理: 超过 50MB 自动裁剪
            cleanupImageCache()
        }
    }

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun cleanupImageCache() {
        try {
            val cacheDir = cacheDir.resolve("image_cache")
            if (!cacheDir.exists()) return
            var totalSize = 0L
            cacheDir.walkBottomUp().filter { it.isFile }.forEach { totalSize += it.length() }
            val maxBytes = 50L * 1024 * 1024 // 50MB
            if (totalSize > maxBytes) {
                android.util.Log.i("CacheCleanup", "图片缓存 ${totalSize / 1024}KB 超限, 清理旧文件")
                val files = cacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return
                var deleted = 0L
                for (f in files) {
                    if (totalSize - deleted < maxBytes * 0.8) break
                    deleted += f.length()
                    f.delete()
                }
                android.util.Log.i("CacheCleanup", "已清理 ${deleted / 1024}KB")
            }
            // 清理旧的 TS 代理临时文件
            Globals.cacheDir.listFiles()?.filter { it.name.startsWith("tsproxy_") }?.forEach { it.delete() }
        } catch (_: Exception) { }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader(this).newBuilder()
            .logger(DebugLogger())
            .components {
                add(SvgDecoder.Factory())
            }
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .build()
    }

    private fun initSentry() {
        SentryAndroid.init(this) { options ->
            options.environment = BuildConfig.BUILD_TYPE
            options.dsn = BuildConfig.SENTRY_DSN
            // release 关闭性能追踪, 减少 OkHttp 拦截器开销
            options.tracesSampleRate = if (BuildConfig.DEBUG) 1.0 else 0.0
            options.isEnableUserInteractionTracing = BuildConfig.DEBUG
            options.beforeSend =
                SentryOptions.BeforeSendCallback { event: SentryEvent, _: Hint ->
                    if (event.level == null) event.level = SentryLevel.FATAL

                    if (BuildConfig.DEBUG) return@BeforeSendCallback null
                    if (SentryLevel.ERROR != event.level && SentryLevel.FATAL != event.level) return@BeforeSendCallback null
                    if (event.exceptions?.any { ex -> ex.type?.contains("Http") == true } == true) return@BeforeSendCallback null

                    event
                }
        }

        @Suppress("UnstableApiUsage")
        Sentry.withScope { scope ->
            Globals.deviceId = scope.options.distinctId ?: ""
        }
    }

    private fun crashHandle() {
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            // 忽略 Compose LazyLayout 重复 release 异常（Compose 已知 bug，不影响播放）
            if (throwable is IllegalStateException &&
                throwable.message?.contains("Release should only be called once") == true &&
                throwable.stackTrace.any { it.className.contains("LazyLayoutPinnableItem") }
            ) {
                android.util.Log.w("CrashHandler", "忽略 Compose LazyLayout 重复 release 异常", throwable)
                return@setDefaultUncaughtExceptionHandler
            }

            // 忽略 NavHost 返回栈空导致的 IndexOutOfBoundsException
            if (throwable is IndexOutOfBoundsException &&
                throwable.stackTrace.any { it.className.contains("NavHost") }
            ) {
                android.util.Log.w("CrashHandler", "忽略 NavHost 返回栈异常", throwable)
                return@setDefaultUncaughtExceptionHandler
            }

            throwable.printStackTrace()
            Sentry.captureException(throwable)

            val intent = Intent(this, CrashHandlerActivity::class.java).apply {
                putExtra("error_message", throwable.message)
                putExtra("error_stacktrace", throwable.stackTraceToString())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)

            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(1)
        }
    }
}
