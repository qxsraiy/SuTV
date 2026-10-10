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

        fun startActivity(context: Context, url: String, channelName: String = "") {
            val intent = Intent(context, MiguFallbackActivity::class.java)
            intent.putExtra(EXTRA_URL, url)
            intent.putExtra(EXTRA_CHANNEL_NAME, channelName)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
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
        webView.webViewClient = WebViewClient()
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
     * 单击/长按 → 回主app弹频道列表
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return true
    }

    /**
     * 拦截 OK 键，在 WebView 之前处理
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                    requestChannelList()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun requestChannelList() {
        onRequestChannelList?.invoke()
        finish()
    }

    private fun loadStream(url: String) {
        val encodedUrl = Uri.encode(url)
        webView.loadUrl("file:///android_asset/hls_player.html#$encodedUrl")
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
        webView.destroy()
        super.onDestroy()
    }
}
