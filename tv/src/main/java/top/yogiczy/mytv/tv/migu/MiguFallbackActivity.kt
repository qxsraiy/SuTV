package top.yogiczy.mytv.tv.migu

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

/**
 * TVAuto 风格 WebView 兜底播放器
 * 当 Media3/IJK 播放器失败时，使用 WebView + hls.js 播放 HLS 直播流
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
    
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: ""
        val url = intent.getStringExtra(EXTRA_URL) ?: ""
        
        if (url.isEmpty()) {
            Toast.makeText(this, "播放地址为空", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // 创建 WebView
        webView = WebView(this)
        
        // 设置 WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            // 使用移动端 UA 避免被拒绝
            userAgentString = "Mozilla/5.0 (Linux; Android 10; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            // 允许 WebView 加载 assets
            allowFileAccessFromFileURLs = true
        }
        
        // 支持全屏视频
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback?) {
                super.onShowCustomView(view, callback)
            }
        }
        
        webView.webViewClient = WebViewClient()
        
        // 设置 WebView 背景
        webView.setBackgroundColor(android.graphics.Color.BLACK)
        
        // 加载本地 hls_player.html，将流地址通过 hash 传递
        val encodedUrl = Uri.encode(url)
        webView.loadUrl("file:///android_asset/hls_player.html#$encodedUrl")
        
        setContentView(webView)
        
        // 隐藏状态栏和导航栏，全屏播放
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
        
        // 设置标题为频道名
        if (channelName.isNotEmpty()) {
            title = channelName
        }
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
