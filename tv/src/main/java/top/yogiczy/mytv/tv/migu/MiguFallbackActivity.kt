package top.yogiczy.mytv.tv.migu

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
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
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * WebView 兜底播放器 - 支持切台和触摸唤出频道列表
 * 逻辑与直播界面一致：点击/双击/长按 → 频道列表
 */
class MiguFallbackActivity : Activity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_CHANNEL_NAME = "extra_channel_name"

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

    // 频道列表
    private data class FallbackChannel(val name: String, val pid: String, val group: String)
    private val channelList = mutableListOf<FallbackChannel>()
    private var currentChannelIdx = -1
    private var hostUrl = ""
    private var channelListDialog: AlertDialog? = null

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialUrl = intent.getStringExtra(EXTRA_URL) ?: ""
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: ""

        if (initialUrl.isEmpty()) {
            Toast.makeText(this, "播放地址为空", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        hostUrl = extractHost(initialUrl)

        // 手势检测：单击/双击/长按都弹出频道列表
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                showChannelListDialog()
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                showChannelListDialog()
                return true
            }
            override fun onLongPress(e: MotionEvent) {
                showChannelListDialog()
            }
        })

        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            // 拦截触摸事件交给手势检测
            setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                true
            }
        }

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
        // WebView 不接收触摸事件，交给 rootLayout 处理
        webView.isClickable = false
        webView.isFocusable = false

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
        if (channelName.isNotEmpty()) {
            showChannelName(channelName)
        }

        loadChannelList(initialUrl)
    }

    private fun extractHost(url: String): String {
        return try {
            val uri = Uri.parse(url)
            "${uri.scheme}://${uri.host}:${uri.port}"
        } catch (e: Exception) {
            "http://localhost:10481"
        }
    }

    private fun extractPid(url: String): String {
        val segments = Uri.parse(url).pathSegments
        return segments.lastOrNull() ?: ""
    }

    private fun loadChannelList(currentUrl: String) {
        val currentPid = extractPid(currentUrl)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val req = Request.Builder().url("$hostUrl/migu.m3u").get().build()
                client.newCall(req).execute().use { resp ->
                    val m3u = resp.body?.string() ?: ""
                    val channels = parseM3u(m3u)

                    withContext(Dispatchers.Main) {
                        channelList.clear()
                        channelList.addAll(channels)
                        currentChannelIdx = channelList.indexOfFirst { it.pid == currentPid }
                        if (currentChannelIdx < 0 && channelList.isNotEmpty()) {
                            currentChannelIdx = 0
                        }
                    }
                }
            } catch (e: Exception) {
                // 静默失败
            }
        }
    }

    private fun parseM3u(m3u: String): List<FallbackChannel> {
        val result = mutableListOf<FallbackChannel>()
        val lines = m3u.split("\n", "\r\n")
        var currentName = ""
        var currentGroup = ""
        var currentPid = ""

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF")) {
                val nameMatch = Regex("tvg-name=\"([^\"]*)\"").find(trimmed)
                val groupMatch = Regex("group-title=\"([^\"]*)\"").find(trimmed)
                val tvgIdMatch = Regex("tvg-id=\"([^\"]*)\"").find(trimmed)
                currentName = nameMatch?.groupValues?.get(1) ?: trimmed.substringAfterLast(",").trim()
                currentGroup = groupMatch?.groupValues?.get(1) ?: ""
                currentPid = tvgIdMatch?.groupValues?.get(1) ?: ""
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                if (currentPid.isEmpty()) {
                    currentPid = trimmed.substringAfterLast("/").substringBefore("?")
                }
                if (currentName.isNotEmpty()) {
                    result.add(FallbackChannel(currentName, currentPid, currentGroup))
                }
                currentName = ""
                currentPid = ""
            }
        }
        return result
    }

    /**
     * 弹出频道列表对话框（与直播界面逻辑一致）
     */
    private fun showChannelListDialog() {
        if (channelList.isEmpty()) {
            Toast.makeText(this, "频道列表加载中...", Toast.LENGTH_SHORT).show()
            return
        }
        if (channelListDialog?.isShowing == true) return

        val displayNames = channelList.mapIndexed { idx, ch ->
            "${idx + 1}. ${ch.group} - ${ch.name}"
        }.toTypedArray()

        channelListDialog = AlertDialog.Builder(this)
            .setTitle("频道列表")
            .setItems(displayNames) { _, which ->
                if (which in channelList.indices) {
                    switchToChannel(which)
                }
            }
            .setOnDismissListener { channelListDialog = null }
            .create()
            .apply {
                show()
                // 高亮当前频道
                if (currentChannelIdx >= 0 && currentChannelIdx < displayNames.size) {
                    listView.setItemChecked(currentChannelIdx, true)
                    listView.setSelection(currentChannelIdx)
                }
            }
    }

    private fun switchToChannel(idx: Int) {
        if (channelList.isEmpty() || idx < 0 || idx >= channelList.size) return

        currentChannelIdx = idx
        val channel = channelList[idx]
        val fallbackUrl = "$hostUrl/migufb/${channel.pid}"
        loadStream(fallbackUrl)
        showChannelName("${channel.group} · ${channel.name}")
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // 频道列表对话框打开时，只处理上下选择和确认/返回
        if (channelListDialog?.isShowing == true) {
            return super.onKeyDown(keyCode, event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (channelList.isNotEmpty()) {
                    val newIdx = if (currentChannelIdx > 0) currentChannelIdx - 1 else channelList.size - 1
                    switchToChannel(newIdx)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (channelList.isNotEmpty()) {
                    val newIdx = if (currentChannelIdx < channelList.size - 1) currentChannelIdx + 1 else 0
                    switchToChannel(newIdx)
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (channelList.isNotEmpty()) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        val newIdx = if (currentChannelIdx > 0) currentChannelIdx - 1 else channelList.size - 1
                        switchToChannel(newIdx)
                    } else {
                        val newIdx = if (currentChannelIdx < channelList.size - 1) currentChannelIdx + 1 else 0
                        switchToChannel(newIdx)
                    }
                    return true
                }
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                showChannelListDialog()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        if (channelListDialog?.isShowing == true) {
            channelListDialog?.dismiss()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(overlayHideRunnable)
        channelListDialog?.dismiss()
        webView.destroy()
        super.onDestroy()
    }
}
