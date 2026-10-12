package top.yogiczy.mytv.tv.ui.screensold.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import androidx.appcompat.widget.AppCompatDrawableManager.preload
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.tencent.smtt.export.external.interfaces.WebResourceResponse
import com.tencent.smtt.sdk.QbSdk
import com.tencent.smtt.sdk.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.tv.ui.material.Visibility
import top.yogiczy.mytv.tv.ui.screensold.webview.components.WebViewPlaceholder
import java.io.ByteArrayInputStream
import java.io.File

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    modifier: Modifier = Modifier,
    urlProvider: () -> String = { "webview://https://tv.cctv.com/live/index.shtml" },
    onVideoResolutionChanged: (width: Int, height: Int) -> Unit = { _, _ -> },
) {
    val url = urlProvider().removePrefix("webview://")
    var placeholderVisible by remember { mutableStateOf(true) }

    val context = LocalContext.current
    var isCoreReplaced by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isCoreReplaced = WebViewManager.isCoreReplaced()

        if (!isCoreReplaced) {
            WebViewManager.replaceCore(context)
            isCoreReplaced = WebViewManager.isCoreReplaced()
        }
    }

    Visibility({ isCoreReplaced }) {
        Box(modifier = modifier.fillMaxSize()) {
            if (WebViewManager.existTbsX5()) {
                AndroidView(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxHeight()
                        .background(Color.Black),
                    factory = {
                        X5WebView(it).apply {
                            webViewClient = X5WebviewClient(
                                onPageStarted = { placeholderVisible = true },
                                onPageFinished = { placeholderVisible = false },
                            )

                            setBackgroundColor(Color.Black.toArgb())
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )

                            settings.javaScriptEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.domStorageEnabled = true
                            settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                            settings.loadsImagesAutomatically = false
                            settings.blockNetworkImage = true
                            settings.userAgentString =
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0"
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.setSupportZoom(false)
                            settings.displayZoomControls = false
                            settings.builtInZoomControls = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            settings.mediaPlaybackRequiresUserGesture = false

                            isHorizontalScrollBarEnabled = false
                            isVerticalScrollBarEnabled = false
                            isClickable = false
                            isFocusable = false
                            isFocusableInTouchMode = false

                            addJavascriptInterface(
                                WebViewInterface(
                                    onVideoResolutionChanged = onVideoResolutionChanged,
                                ), "Android"
                            )
                        }
                    },
                    update = { it.loadUrl(url) },
                )
            } else {
                AndroidView(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxHeight()
                        .background(Color.Black),
                    factory = {
                        SystemWebView(it).apply {
                            webViewClient = SystemWebviewClient(
                                onPageStarted = { placeholderVisible = true },
                                onPageFinished = { placeholderVisible = false },
                            )

                            setBackgroundColor(Color.Black.toArgb())
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )

                            settings.javaScriptEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.domStorageEnabled = true
                            settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                            settings.loadsImagesAutomatically = false
                            settings.blockNetworkImage = true
                            settings.userAgentString =
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0"
                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.setSupportZoom(false)
                            settings.displayZoomControls = false
                            settings.builtInZoomControls = false
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            settings.mediaPlaybackRequiresUserGesture = false

                            isHorizontalScrollBarEnabled = false
                            isVerticalScrollBarEnabled = false
                            isClickable = false
                            isFocusable = false
                            isFocusableInTouchMode = false

                            addJavascriptInterface(
                                WebViewInterface(
                                    onVideoResolutionChanged = onVideoResolutionChanged,
                                ), "Android"
                            )
                        }
                    },
                    update = { it.loadUrl(url) },
                )
            }

            Visibility({ placeholderVisible }) { WebViewPlaceholder() }
        }
    }
}

private class SystemWebviewClient(
    private val onPageStarted: () -> Unit,
    private val onPageFinished: () -> Unit,
) : android.webkit.WebViewClient() {

    override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: Bitmap?) {
        onPageStarted()
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: android.webkit.WebView, url: String) {
        view.evaluateJavascript(WebViewManager.preload) {
            onPageFinished()
        }
    }

    override fun shouldInterceptRequest(
        view: android.webkit.WebView?,
        request: WebResourceRequest?
    ): android.webkit.WebResourceResponse? {
        val url = request?.url.toString()
        if (WebViewManager.requestBlocking.any { it.containsMatchIn(url) }) {
            return android.webkit.WebResourceResponse(
                "text/plain",
                "UTF-8",
                ByteArrayInputStream("".toByteArray(Charsets.UTF_8))
            )
        }

        return null
    }
}

private class SystemWebView(context: Context) : android.webkit.WebView(context) {
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return false
    }
}

private class X5WebviewClient(
    private val onPageStarted: () -> Unit,
    private val onPageFinished: () -> Unit,
) : com.tencent.smtt.sdk.WebViewClient() {

    override fun onPageStarted(
        view: com.tencent.smtt.sdk.WebView?,
        url: String?,
        favicon: Bitmap?,
    ) {
        onPageStarted()
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: com.tencent.smtt.sdk.WebView, url: String) {
        view.evaluateJavascript(WebViewManager.preload) {
            onPageFinished()
        }
    }

    override fun shouldInterceptRequest(p0: WebView?, url: String): WebResourceResponse? {
        if (WebViewManager.requestBlocking.any { it.containsMatchIn(url) }) {
            return WebResourceResponse(
                "text/plain",
                "UTF-8",
                ByteArrayInputStream("".toByteArray(Charsets.UTF_8))
            )
        }

        return null
    }
}

private class X5WebView(context: Context) : com.tencent.smtt.sdk.WebView(context) {
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return false
    }
}

private class WebViewInterface(
    private val onVideoResolutionChanged: (width: Int, height: Int) -> Unit = { _, _ -> },
) {
    @JavascriptInterface
    fun changeVideoResolution(width: Int, height: Int) {
        onVideoResolutionChanged(width, height)
    }
}

object WebViewManager {

    private val log = Logger.create("WebViewManager")
    private var coreHasReplaced = false

    val requestBlocking = """
            // 央视网
            www.cctv.com/newcctv/containingPages/2019whitetop/index.js
            tv.cctv.com/common/pc_nav/index.js
            time.tv.cctv.com/time.php
            res.wx.qq.com/open/js/jweixin-*.js
            alicdn.com/dingding/open-develop/*/dingtalk.js
            img.cctvpic.com/photoAlbum/templet/common/*/configtoolV1.1.js
            player.cntv.cn/creator/swfobject.js
            img.cctvpic.com/photoAlbum/templet/common/*/cctvnew-jquery.tinyscrollbar.js
            img.cctvpic.com/photoAlbum/templet/js/jquery.qrcode.min.js
            img.cctvpic.com/photoAlbum/templet/common/*/shareindex.js
            img.cctvpic.com/photoAlbum/templet/common/*/dianshibao.js
            player.cntv.cn/creator/hlsp2p.js
            img.cctvpic.com/photoAlbum/templet/common/*/zhibo_shoucang.js
            www.cctv.com/newcctv/containingPages/mapjs/index.js
            www.cctv.com/newcctv/containingPages/bottomjs/index.js
            img.cctvpic.com/photoAlbum/templet/common/*/indexPC.js
            js.player.cntv.cn/creator/html5player_analysis_lib.js
            www.cctv.com/js/cntv_Advertise.js
            img.cctvpic.com/photoAlbum/templet/common/*/stylePC.css
            img.cctvpic.com/photoAlbum/templet/common/*/common.css
            img.cctvpic.com/photoAlbum/templet/common/*/style_gq_20190905.css
            img.cctvpic.com/photoAlbum/templet/common/*/zhibo_shoucang.css
            img.cctvpic.com/photoAlbum/templet/common/*/loginstyle.css
            player.cntv.cn/html5Player/images/cctv_html5player_loading.gif
            img.cctvpic.com/*.gif
            img.cctvpic.com/*.jpg
            img.cctvpic.com/*.png
            player.cntv.cn/html5Player/images/*.png

            // 央视频
            yangshipin.cn/assets/*/ysp_account/yspLogin.css
            yangshipin.cn/static/project/test/player/vr/index.css
            yangshipin.cn/css/app.*.css
            yangshipin.cn/css/chunk-vendors.*.css
            yangshipin.cn/css/chunk-*.css
            yangshipin.cn/CCTVVideo/CCTVVideoAssets/v1/js/vconsole.min.js
            yangshipin.cn/static/project/test/player/vr/VR.js
            yangshipin.cn/static/project/test/player/vr/three.js
            yangshipin.cn/static/project/test/player/vr/index.js
            yangshipin.cn/CCTVVideo/cctvh5-trace/cctvh5-trace.min.js
            yangshipin.cn/*.gif
            yangshipin.cn/*.png
            yangshipin.cn/*.svg

            // 山东
            file.iqilu.com/custom/new/public/css/qlqz_headfooter.css
            file.iqilu.com/custom/v/channel/css/wt_pd_v*.css
            file.iqilu.com/custom/v/zhibo/media.css
            web.sdk.qcloud.com/player/tcplayer/release/*/tcplayer.min.css
            file.iqilu.com/custom/new/public/js/jquery-*.min.js
            file.iqilu.com/custom/v/js/bagapi.js
            file.iqilu.com/custom/v/js/jiemudan.js
            file.iqilu.com/custom/v/channel/js/custom.js
            file.iqilu.com/custom/v/js/common.js
            file.iqilu.com/custom/v/channel/*/images/*.jpg
            iqilu.com/vmsimgs/*.jpg
            iqilu.com/vmsimgs/*.png
            file.iqilu.com/custom/v/channel/images/*.jpg

            // 陕西
            res.cnwest.com/t/site/*/tvLive/css/live_pc.css
            res.cnwest.com/t/site/*/tvLive/css/DPlayer.min.css
            res.cnwest.com/t/site/*/public/css/header-footer.css
            res.cnwest.com/t/site/*/snrtv_live/js/jquery.tinyscrollbar.js
            res.cnwest.com/t/site/*/index/js/snrtv.baidu.tj-mod.js
            hm.baidu.com/hm.js
            cnwest.com/static/js/smc-tj-function.js
            cnwest.com/t/site/*/public/images/*.png

            // 江苏
            live.jstv.com/assets/css/chunk-vendors.*.css
            live.jstv.com/assets/css/app.*.css
            live.jstv.com/assets/css/299.*.css
            live.jstv.com/imgs/*.png
            jstv.com/lizhiapp-screenshot/*.jpg

            // 南京
            www.nbs.cn/css/nbs_zixun*.css
            www.nbs.cn/images/*.jpg

            // 北京
            s1.ssl.qhres2.com/*.css
            s1.ssl.qhres2.com/*/layui/layui.all.js
            s2.ssl.qhres2.com/*/swiper4/js/swiper.min.js
            s3.ssl.qhres2.com/*/feb/monitor.js
            s4.ssl.qhres2.com/static/*/v5/modules/footer.js
            s1.ssl.qhres2.com/static/*/v5/modules/totop.js
            s5.ssl.qhres2.com/*/sharejs/js/jquery.share.min.js
            s2.ssl.qhres2.com/static/*/v5/modules/header.js
            s3.ssl.qhres2.com/static/*/v5/common_bundle.js
            zz.bdstatic.com/linksubmit/push.js
            cdn.btime.com/*.jpg
            cdn.btime.com/*.png
            www.brtn.cn/static/img/v5/*.png
            ssl.qhimg.com/d/inn/*/*.png
            qhimg.com/*.png

            // 安徽
            h5.ahtv.cn/themes/default/css/base.css
            h5.ahtv.cn/themes/default/css/common.css
            h5.ahtv.cn/themes/default/css/list-livecon.css
            h5.ahtv.cn/themes/default/css/aliplayer-min.css
            h5.ahtv.cn/themes/default/js/responsive.js
            res.wx.qq.com/open/js/jweixin-*.js
            h5.ahtv.cn/themes/default/js/h5_go.js
            h5.ahtv.cn/themes/default/js/cyberplayer.js
            h5.ahtv.cn/themes/default/js/elderly-h5.min.js
            image.ahtv.cn/*.jpg
            h5.ahtv.cn/themes/default/images/*.png

            // 广西
            www.gxtv.cn/static/web/css/publicCss/2018GXNTV.common.css
            tv.gxtv.cn/static/web/css/newLive/2018Live.main.css
            tv.gxtv.cn/static/Hplus/js/plugins/layer/skin/layer.css
            tv.gxtv.cn/static/Hplus/js/plugins/layer/layer.min.js
            www.gxtv.cn/static/cmcMediaPlayer/cmcMediaPlayer.js
            www.gxtv.cn/static/mediaPlayerCommon.js
            www.gxtv.cn/static/ieVersion.js
            tv.gxtv.cn/static/web/js/gxtv.nicescroll.js
            www.gxtv.cn/static/gxtv-dsj.js
            www.gxtv.cn/static/web/js/publicJs/gxtv.header.js
            www.gxtv.cn/static/web/js/publicJs/gxtv.select.js
            www.gxtv.cn/static/web/js/publicJs/gxtv.footer.js
            tv.gxtv.cn/static/js/rainbow/ParsedQueryString.js
            tv.gxtv.cn/static/js/rainbow/swfobject.js
            tv.gxtv.cn/static/web/images/*.jpg
            tv.gxtv.cn/static/web/images/*.png

            // 湖南
            css.mgtv.com/imgotv-channel/*/live-cast.css
            hitv.com/libs/??vplayer/*/vplayer.css
            css.mgtv.com/imgotv-channel/*/page/live/live-cast.css
            css.mgtv.com/imgotv-channel/*/page/live/live-index.css
            css.mgtv.com/imgotv-member/page/member/member-dialog.css
            css.mgtv.com/imgotv-member/page/vip/vip-dialog.css
            css.mgtv.com/imgotv-member/page/member/member-dialog.css
            honey.mgtv.com/honey-*/lib/iwt*.js
            hitv.com/libs/??es6-shim/*/es6-shim.min.js,es-promise/*/promise.polyfill.min.js,es6-weakmap/*/index.js
            hitv.com/libs/??jquery/*/jquery.min.js,mvp-player/*/mvp.player.min.js
            mgtv.com/act/pcweb_cashier_umd/pcweb-cashier.umd.js
            mgtv.com/sdk/pclive-sdk/latest/ad-sdk.js
            mgtv.com/honey-*/mod/feedback.js
            mgtv.com/imgotv-channel/*/plugin/jquery.scrollpanel.js
            mgtv.com/honey-*/mod/gototop.js
            mgtv.com/honey-*/plugin/stk.js
            js.mgtv.com/imgotv-channel/*/widget/vipdialog.js
            js.mgtv.com/imgotv-channel/*/widget/mvp-player-income.js
            js.mgtv.com/imgotv-channel/6*/widget/mvp-player.js
            mgtv.com/honey-*/plugin/monitor.js
            mgtv.com/honey-*/lib/mba.min.js
            mgtv.com/act/pcweb_cashier_umd/pcweb-cashier.umd.js
            img.mgtv.com/imgotv-channel/*.jpg
            img.mgtv.com/imgotv-channel/*.png
            img.mgtv.com/imgotv-channel/*.svg
            hitv.com/*.png

            // 上海
            live.kankanews.com/css/reset.css
            live.kankanews.com/css/global.css
            live.kankanews.com/css/noSupport.css
            skin.kankanews.com/sensors/sensorsdata.min.js
            skin.kankanews.com/v6/js/libs/jquery-*.min.js
            live.kankanews.com/js/toMpage.js
            live.kankanews.com/js/adSDK.js
            live.kankanews.com/js/noSupport.js
            skin.kankanews.com/*.gif
            skin.kankanews.com/*.png
            statickksmg.com/cont/*.png

            // 浙江
            www.cztv.com/static/api/css/share_style*.css
            www.cztv.com/assets/index-*.css
            www.cztv.com/assets/bottomView-*.css
            www.cztv.com/assets/login-Bit-*.css
            www.cztv.com/assets/index-CMM-*.css
            www.cztv.com/assets/directSeedingTV-*.css
            www.cztv.com/static/api/js/share.js
            apps.bdimg.com/libs/jquery/*/jquery.min.js
            www.cztv.com/static/TCaptcha.js
            cztvcloud.com/*/js/jquery-*.js
            data.volccdn.com/obj/data-static/log-sdk/collect/*/collect-rangers-v*.js
            wap.cztv.com/articles/src/js/count.js
            www.cztv.com/assets/SourceHanSansCN-Bold-*.ttf
            www.cztv.com/assets/SourceHanSansCN-Normal-*.ttf
            www.cztv.com/assets/*.png
            oss.cztv.com/ucc/*.png
        """.trimIndent().lines()
        .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("//") }
        .mapNotNull { rule ->
            runCatching {
                rule.trim()
                    .replace(".", "\\.")
                    .replace("*", ".*")
                    .toRegex()
            }.getOrNull()
        }

    val preload = """
function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

var WebviewVideoPlayerImpl = {
  _videoVolume: 1.0,
  _videoPaused: false,
  _videoWidth: 0,
  _videoHeight: 0,
  _videoStatusTimer: null,

  _resetCss() {
    const stylesheets = document.querySelectorAll('link[rel="stylesheet"], style')
    stylesheets.forEach(sheet => sheet.remove())

    const elements = document.querySelectorAll('*')
    elements.forEach(element => {
      element.removeAttribute('style')
    })
  },

  _getVideoEl() {
    return document.querySelector('video')
  },

  async _waitVideoReady() {
    while (true) {
      const videoEl = this._getVideoEl()
      if (videoEl) return videoEl
      await delay(100)
    }
  },

  _fullscreenVideo() {
    const videoEl = this._getVideoEl()
    videoEl.style = 'position: fixed; left: -1px; top: -1px; height: calc(100vh + 2px); width: calc(100vw + 2px); z-index: 99999; background: black;'

    for (const child of document.body.children) {
      child.style['z-index'] = -1
    }
  },

  _isVideoFullscreen() {
    const videoEl = this._getVideoEl()
    return videoEl && videoEl.style.position === 'fixed' && videoEl.style.left === '-1px' && videoEl.style.top === '-1px'
  },

  async _initialize() {
    await WebviewVideoPlayerImpl_hostBeforeInitialize[location.host]?.()
    await this._waitVideoReady()

    const error = await WebviewVideoPlayerImpl_hostInitialize[location.host]?.()
    if (error) {
      return
    }

    await this._waitVideoReady()
    this._resetCss()
    this._fullscreenVideo()

    const videoEl = this._getVideoEl()

    clearInterval(this._videoStatusTimer)
    this._videoStatusTimer = setInterval(() => {
      const videoEl = this._getVideoEl()
      if (!videoEl) return

      videoEl.volume = this._videoVolume

      if (!this._videoPaused && videoEl.paused) {
        videoEl.play()
      }

      const videoWidth = videoEl.videoWidth
      const videoHeight = videoEl.videoHeight

      if (videoWidth != 0 && videoHeight != 0 && (videoWidth !== this._videoWidth || videoHeight !== this._videoHeight)) {
        this._videoWidth = videoWidth
        this._videoHeight = videoHeight
        window.Android?.changeVideoResolution(videoWidth, videoHeight)
      }
    }, 500)
  },

  async initialize() {
    while (true) {
      if (!this._isVideoFullscreen()) {
        await this._initialize()
      }
      await delay(1000)
    }
  },
}

var WebviewVideoPlayerImpl_hostInitialize = {
  'tv.cctv.com': async () => {
    const errorMsgEl = document.getElementById('error_msg_player')
    if (errorMsgEl) {
      WebviewVideoPlayerImpl._resetCss()
      errorMsgEl.style = 'position: fixed; left: -1px; top: -1px; height: calc(2px + 100vh); width: calc(2px + 100vw); z-index: 99999; background: black; color: white; font-size: 3vw; text-align: center; display: flex; justify-content: center; align-items: center;'
      return true
    }
  },

  'yangshipin.cn': async () => {
    const urlParams = new URLSearchParams(window.location.search)
    const resolution = urlParams.get('resolution')

    while (true) {
      const errorMsgEl = document.querySelector('.bright-text')
      const liList = document.querySelector('.bei-list-inner')
      if (errorMsgEl || liList) break

      await delay(100)
    }

    let liList = document.querySelectorAll('.bei-list-inner span')
    for (const li of liList) {
      if (li.innerText.includes(resolution)) {
        li.click()
        break
      }
    }

    while (true) {
      if (document.querySelector('video')?.videoWidth) break

      const errorMsgEl = document.querySelector('.bright-text')
      if (errorMsgEl) {
        WebviewVideoPlayerImpl._resetCss()
        errorMsgEl.style = 'position: fixed; left: -1px; top: -1px; height: calc(2px + 100vh); width: calc(2px + 100vw); z-index: 99999; background: black; color: white; font-size: 3vw; text-align: center; display: flex; justify-content: center; align-items: center;'

        break
      }

      await delay(100)
    }
  },

  'live.snrtv.com': async () => {
    const urlParams = new URLSearchParams(window.location.search)
    const channel = urlParams.get('channel')

    let liList = document.querySelectorAll('.btnStream > li')
    for (const li of liList) {
      if (li.innerText.includes(channel)) {
        li.click()
        break
      }
    }
  },

  'live.jstv.com': async () => {
    const urlParams = new URLSearchParams(window.location.search)
    const channel = urlParams.get('channel')

    let liList = document.querySelector('#programMain')?.querySelectorAll('.swiper-slide') || []
    for (const li of liList) {
      if (li.innerText.includes(channel)) {
        li.querySelector('.imgBox')?.click()
        break
      }
    }
  },

  'www.nbs.cn': async () => {
    const urlParams = new URLSearchParams(window.location.search)
    const channel = urlParams.get('channel')

    let liList = document.querySelectorAll('.tv_list > .tv_c')
    for (const li of liList) {
      if (li.innerText.includes(channel)) {
        li.click()
        break
      }
    }
  },

  'www.brtn.cn': async () => {
    const urlParams = new URLSearchParams(window.location.search)
    const channel = urlParams.get('channel')

    let liList = document.querySelectorAll('.right_list li')
    for (const li of liList) {
      if (li.innerText.includes(channel)) {
        li.click()
        break
      }
    }
  },

  "web.guangdianyun.tv": async () => {
    while (true) {
      if (WebviewVideoPlayerImpl._getVideoEl()?.videoWidth) break
      await delay(100)
    }
  },
}

var WebviewVideoPlayerImpl_hostBeforeInitialize = {
  'tv.cctv.com': async () => {
    const urlParams = new URLSearchParams(window.location.search)
    const resolution = urlParams.get('resolution')

    const resolutionValues = {
      "超清": 720,
      "高清": 540,
      "标清": 480,
      "流畅": 360,
    }
    localStorage.setItem('cctv_live_resolution', resolutionValues[resolution] || 'auto')
  },
}

WebviewVideoPlayerImpl.initialize()
        """.trimIndent()

    fun existTbsX5(): Boolean {
        val tbsX5File = File(Globals.nativeLibraryDir, "libtbs.libmttwebview.so.so")
        return tbsX5File.exists()
    }

    fun isCoreReplaced() = coreHasReplaced

    suspend fun replaceCore(context: Context) = withContext(Dispatchers.IO) {
        log.d("Starting core replacement process")

        if (coreHasReplaced) {
            log.d("Core has already been replaced, skipping replacement")
            return@withContext
        }
        coreHasReplaced = true

        if (!existTbsX5()) {
            log.e("TBS core file not found. Aborting core replacement.")
            return@withContext
        }

        if (QbSdk.canLoadX5(context)) {
            log.d("X5 core is already loaded. No replacement needed.")
            return@withContext
        }

        log.w("X5 core is not loaded. Attempting to preinstall TBS.")

        val ok = QbSdk.preinstallStaticTbs(context)
        log.d("TBS core preinstallation initiated: $ok")
    }
}