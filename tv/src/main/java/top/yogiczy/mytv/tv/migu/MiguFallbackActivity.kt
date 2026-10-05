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
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * WebView 兜底播放器 - 支持切台
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
    private val handler = Handler(Looper.getMainLooper())

    // 频道列表
    private data class FallbackChannel(val name: String, val pid: String, val group: String)
    private val channelList = mutableListOf<FallbackChannel>()
    private var currentChannelIdx = -1
    private var hostUrl = ""

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

        // 从 URL 提取 host
        hostUrl = extractHost(initialUrl)

        // 创建根布局
        rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        // 创建 WebView
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

        // 创建频道名覆盖层
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

        // 全屏
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )

        // 加载初始频道
        loadStream(initialUrl)
        if (channelName.isNotEmpty()) {
            showChannelName(channelName)
        }

        // 后台加载频道列表
        loadChannelList(initialUrl)
    }

    /**
     * 从 fallback URL 提取 host (如 http://192.168.1.224:10481)
     */
    private fun extractHost(url: String): String {
        return try {
            val uri = Uri.parse(url)
            "${uri.scheme}://${uri.host}:${uri.port}"
        } catch (e: Exception) {
            "http://localhost:10481"
        }
    }

    /**
     * 从 URL 提取 pid
     */
    private fun extractPid(url: String): String {
        val segments = Uri.parse(url).pathSegments
        return segments.lastOrNull() ?: ""
    }

    /**
     * 后台加载频道列表
     */
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
                // 静默失败，不影响播放
            }
        }
    }

    /**
     * 解析 M3U 为频道列表
     */
    private fun parseM3u(m3u: String): List<FallbackChannel> {
        val result = mutableListOf<FallbackChannel>()
        val lines = m3u.split("\n", "\r\n")
        var currentName = ""
        var currentGroup = ""
        var currentPid = ""

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF")) {
                // 解析 tvg-name 和 group-title
                val nameMatch = Regex("tvg-name=\"([^\"]*)\"").find(trimmed)
                val groupMatch = Regex("group-title=\"([^\"]*)\"").find(trimmed)
                val tvgIdMatch = Regex("tvg-id=\"([^\"]*)\"").find(trimmed)
                currentName = nameMatch?.groupValues?.get(1) ?: trimmed.substringAfterLast(",").trim()
                currentGroup = groupMatch?.groupValues?.get(1) ?: ""
                currentPid = tvgIdMatch?.groupValues?.get(1) ?: ""
            } else if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                // URL 行 - 提取 pid
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
     * 切换到指定频道
     */
    private fun switchToChannel(idx: Int) {
        if (channelList.isEmpty() || idx < 0 || idx >= channelList.size) return

        currentChannelIdx = idx
        val channel = channelList[idx]
        val fallbackUrl = "$hostUrl/migufb/${channel.pid}"
        loadStream(fallbackUrl)
        showChannelName("${channel.group} · ${channel.name}")
    }

    /**
     * 加载 HLS 流到 WebView
     */
    private fun loadStream(url: String) {
        val encodedUrl = Uri.encode(url)
        webView.loadUrl("file:///android_asset/hls_player.html#$encodedUrl")
    }

    /**
     * 显示频道名（3秒后自动隐藏）
     */
    private var overlayHideRunnable = Runnable { overlayText.visibility = View.GONE }

    private fun showChannelName(name: String) {
        overlayText.text = name
        overlayText.visibility = View.VISIBLE
        handler.removeCallbacks(overlayHideRunnable)
        handler.postDelayed(overlayHideRunnable, 3000)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
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
                // 左右键也可以切台
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
                // OK 键显示当前频道名
                if (currentChannelIdx >= 0 && currentChannelIdx < channelList.size) {
                    val ch = channelList[currentChannelIdx]
                    showChannelName("${ch.group} · ${ch.name}")
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(overlayHideRunnable)
        webView.destroy()
        super.onDestroy()
    }
}
