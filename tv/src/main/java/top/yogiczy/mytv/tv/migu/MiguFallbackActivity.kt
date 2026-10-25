package top.yogiczy.mytv.tv.migu

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

/**
 * WebView 兜底播放器
 * 点击/长按/OK键 → 关闭兜底，回到主 app 弹经典频道列表（与直播界面一致）
 */
class MiguFallbackActivity : Activity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_CHANNEL_NAME = "extra_channel_name"

        @Volatile
        var onRequestChannelList: (() -> Unit)? = null

        /** WebView 兜底播放失败回调（触发蓝屏） */
        @Volatile
        var onPlaybackFailed: (() -> Unit)? = null

        /** 方向键换台回调：true=上一个频道，false=下一个频道 */
        @Volatile
        var onSwitchChannel: ((prev: Boolean) -> Unit)? = null

        fun startActivity(context: Context, url: String, channelName: String = "") {
            val intent = Intent(context, MiguFallbackActivity::class.java)
            intent.putExtra(EXTRA_URL, url)
            intent.putExtra(EXTRA_CHANNEL_NAME, channelName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        /** 滑动换台的最小速度（像素/秒） */
        private const val SWIPE_MIN_VELOCITY = 600f

        /** 注入的探测脚本：上报 playing / error / stalled */
        private val JS_STATE_PROBE = """
            (function() {
              try {
                var v = document.getElementById('video');
                if (!v) { AndroidBridge.onState('error'); return; }
                v.addEventListener('playing', function(){ AndroidBridge.onState('playing'); });
                v.addEventListener('error', function(){ AndroidBridge.onState('error'); });
                var t = 0;
                var timer = setInterval(function() {
                  t += 500;
                  if (!v.paused && v.readyState >= 3) {
                    AndroidBridge.onState('playing');
                    clearInterval(timer);
                  } else if (t >= ${top.yogiczy.mytv.core.data.utils.Constants.WEBVIEW_FALLBACK_TIMEOUT}) {
                    AndroidBridge.onState('stalled');
                    clearInterval(timer);
                  }
                }, 500);
              } catch (e) { AndroidBridge.onState('error'); }
            })();
        """.trimIndent()
    }

    private lateinit var webView: WebView
    private lateinit var overlayText: TextView
    private lateinit var rootLayout: FrameLayout
    private lateinit var gestureDetector: GestureDetector
    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialUrl = intent.getStringExtra(EXTRA_URL) ?: ""
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: ""

        if (initialUrl.isEmpty()) {
            Toast.makeText(this, "播放地址为空", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                requestChannelList()
                return true
            }
            override fun onLongPress(e: MotionEvent) {
                requestChannelList()
            }
        })

        rootLayout = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        webView = WebView(this)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "Mozilla/5.0 (Linux; Android 10; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            allowFileAccessFromFileURLs = true
        }
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                pageFinished = true
                // 注入播放状态探测
                view?.evaluateJavascript(JS_STATE_PROBE, null)
            }

            override fun onReceivedError(
                view: WebView?,
                request: android.webkit.WebResourceRequest?,
                error: android.webkit.WebResourceError?,
            ) {
                super.onReceivedError(view, request, error)
                // 只关心主框架失败
                if (request?.isForMainFrame == true) {
                    reportFailure("主框架加载失败")
                }
            }

            @Deprecated("兼容旧 API")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?,
            ) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                reportFailure("加载失败: $description")
            }
        }
        webView.addJavascriptInterface(Bridge(), "AndroidBridge")
        webView.setBackgroundColor(Color.BLACK)

        overlayText = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(24, 12, 24, 12)
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 8f
                setColor(Color.parseColor("#CC000000"))
            }
            visibility = View.GONE
        }

        val overlayParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            marginStart = 32
            topMargin = 64
        }

        rootLayout.addView(webView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        rootLayout.addView(overlayText, overlayParams)
        setContentView(rootLayout)

        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )

        loadStream(initialUrl)
        if (channelName.isNotEmpty()) showChannelName(channelName)
    }

    /**
     * Activity 级别拦截所有触摸事件，WebView 收不到触摸
     * - 单击 / 长按 → 回主 app 弹频道列表
     * - 上滑 → 下一个频道；下滑 → 上一个频道
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = ev.x
                touchDownY = ev.y
                touchDownAt = System.currentTimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                val dx = ev.x - touchDownX
                val dy = ev.y - touchDownY
                val dt = System.currentTimeMillis() - touchDownAt
                val minDist = 70f * resources.displayMetrics.density
                if (dt in 1..800 &&
                    kotlin.math.abs(dy) > minDist &&
                    kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.5f
                ) {
                    // 上滑(dy<0)=下一个频道；下滑(dy>0)=上一个频道
                    switchChannel(prev = dy > 0)
                    return true
                }
            }
        }
        gestureDetector.onTouchEvent(ev)
        return true
    }

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownAt = 0L

    /**
     * 拦截按键，在 WebView 之前处理
     * - OK / 确认 → 关闭兜底，回主 app 弹频道列表
     * - 上/左 → 上一个频道
     * - 下/右 → 下一个频道
     * 换台一律回到主 app 的直播源播放器，不再停留在 WebView
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                    requestChannelList()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT -> {
                    switchChannel(prev = true)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    switchChannel(prev = false)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** 换台：通知主 app 切到相邻频道，然后关闭 WebView 回到直播画面 */
    private fun switchChannel(prev: Boolean) {
        handler.removeCallbacks(watchdogRunnable)
        val callback = onSwitchChannel
        finish()
        callback?.invoke(prev)
    }

    private fun requestChannelList() {
        onRequestChannelList?.invoke()
        finish()
    }

    // ==================== 播放失败检测 ====================

    private var pageFinished = false
    private var playStarted = false
    private var failureReported = false

    /** JS 桥：页面内上报播放状态 */
    inner class Bridge {
        @android.webkit.JavascriptInterface
        fun onState(state: String) {
            handler.post {
                when (state) {
                    "playing" -> {
                        playStarted = true
                        watchdogRunnable.let { handler.removeCallbacks(it) }
                    }
                    "error" -> reportFailure("播放器报错")
                    "stalled" -> reportFailure("无数据")
                }
            }
        }
    }

    /** 兜底超时看门狗：超时仍未出画面即判失败 */
    private val watchdogRunnable = Runnable {
        if (!playStarted) reportFailure("超时未出画面")
    }

    private fun reportFailure(reason: String) {
        if (failureReported || playStarted) return
        failureReported = true
        handler.removeCallbacks(watchdogRunnable)
        android.util.Log.e("MiguFallback", "WebView 兜底失败: $reason")
        onPlaybackFailed?.invoke()
        finish()
    }

    private fun loadStream(url: String) {
        val encodedUrl = Uri.encode(url)
        webView.loadUrl("file:///android_asset/hls_player.html#$encodedUrl")
        // 启动看门狗
        handler.removeCallbacks(watchdogRunnable)
        handler.postDelayed(watchdogRunnable, top.yogiczy.mytv.core.data.utils.Constants.WEBVIEW_FALLBACK_TIMEOUT)
    }

    private var overlayHideRunnable = Runnable { overlayText.visibility = View.GONE }

    private fun showChannelName(name: String) {
        overlayText.text = name
        overlayText.visibility = View.VISIBLE
        handler.removeCallbacks(overlayHideRunnable)
        handler.postDelayed(overlayHideRunnable, 3000)
    }

    override fun onDestroy() {
        handler.removeCallbacks(overlayHideRunnable)
        handler.removeCallbacks(watchdogRunnable)
        webView.destroy()
        super.onDestroy()
    }
}
