package top.yogiczy.mytv.tv.utlis

import android.content.Context
import android.content.Intent
import com.koushikdutta.async.AsyncServer
import com.koushikdutta.async.http.body.JSONObjectBody
import com.koushikdutta.async.http.server.AsyncHttpServer
import com.koushikdutta.async.http.server.AsyncHttpServerRequest
import com.koushikdutta.async.http.server.AsyncHttpServerResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.Request
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList
import top.yogiczy.mytv.core.data.entities.vod.VodSource
import top.yogiczy.mytv.core.data.entities.vod.VodSourceList
import top.yogiczy.mytv.core.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.data.utils.Loggable
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.tv.BuildConfig
import top.yogiczy.mytv.tv.HttpServerService
import top.yogiczy.mytv.tv.R
import top.yogiczy.mytv.core.data.network.Ipv4FirstDns
import top.yogiczy.mytv.tv.migu.MiguApi
import top.yogiczy.mytv.tv.migu.MiguBackendManager
import top.yogiczy.mytv.tv.migu.MiguM3uGenerator
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.SnackbarType
import top.yogiczy.mytv.tv.ui.utils.Configs
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException


object HttpServer : Loggable("HttpServer") {
    private const val SERVER_PORT = 10481
    val serverUrl by lazy { "http://${getLocalIpAddress()}:${SERVER_PORT}" }

    /** 播放器专用回环地址：始终走 127.0.0.1，规避设备局域网/防火墙/IPv6 问题 */
    private val loopbackUrl = "http://127.0.0.1:$SERVER_PORT"

    // 同频道请求去重: 缓存 pid → 结果 (5s TTL, 同一频道5秒内只请求一次API)
    private val miguResultCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, String>>() // pid → (timestamp, url)
    // M3U8 内容短时缓存 (30s TTL)
    private val m3u8Cache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, String>>()
    private const val M3U8_CACHE_TTL = 2_000L  // 直播 playlist 刷新很快，缓存过长会导致卡顿/落后

    // ---- 冷启动就绪控制 ----
    // 播放器/直播源加载可能在服务 listen 之前就发起请求，必须先等待就绪，避免 connection refused。
    @Volatile
    private var isReady = false
    private val readyLatch = java.util.concurrent.CountDownLatch(1)

    /** 服务是否已开始监听 */
    val ready: Boolean get() = isReady

    /**
     * 等待本地服务就绪（含路由注册完成）。
     * 返回 true 表示就绪，false 表示超时。
     */
    fun awaitReady(timeoutMs: Long = 8000L): Boolean {
        if (isReady) return true
        return try {
            readyLatch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS) || isReady
        } catch (_: InterruptedException) {
            isReady
        }
    }

    /** 启动后立即标记（幂等） */
    private fun markReady() {
        if (!isReady) {
            isReady = true
            readyLatch.countDown()
        }
    }

    private val sharedClient by lazy {
        val builder = okhttp3.OkHttpClient.Builder().dns(Ipv4FirstDns)
            .followRedirects(true)
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        val client = builder.build()
        top.yogiczy.mytv.tv.utlis.NetworkMonitor.trackPool(client.connectionPool)
        client
    }

    private val noRedirectClient by lazy {
        val builder = okhttp3.OkHttpClient.Builder().dns(Ipv4FirstDns)
            .followRedirects(false)
            .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
        val client = builder.build()
        top.yogiczy.mytv.tv.utlis.NetworkMonitor.trackPool(client.connectionPool)
        client
    }

    private val proxyClient by lazy {
        val builder = okhttp3.OkHttpClient.Builder().dns(Ipv4FirstDns)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
        val client = builder.build()
        top.yogiczy.mytv.tv.utlis.NetworkMonitor.trackPool(client.connectionPool)
        client
    }

    fun start(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Clean up old TS proxy temp files（Globals 可能尚未初始化，不能因此让服务起不来）
                runCatching {
                    Globals.cacheDir.listFiles()
                        ?.filter { it.name.startsWith("tsproxy_") }
                        ?.forEach { it.delete() }
                }.onFailure { log.w("清理 TS 临时文件失败: ${it.message}") }

                val server = AsyncHttpServer()

                server.addAction("OPTIONS", ".+") { _, response ->
                    wrapResponse(response)
                    response.send("ok")
                }


                server.get("/") { _, response ->
                    handleRawResource(response, context, "text/html", R.raw.web_push)
                }
                server.get("/web_push_css.css") { _, response ->
                    handleRawResource(response, context, "text/css", R.raw.web_push_css)
                }
                server.get("/web_push_js.js") { _, response ->
                    handleRawResource(response, context, "text/javascript", R.raw.web_push_js)
                }

                server.get("/api/info") { _, response ->
                    handleGetInfo(response)
                }

                server.post("/api/iptv-source/push") { request, response ->
                    handleIptvSourcePush(request, response)
                }

                server.post("/api/vod-source/push") { request, response ->
                    handleVodSourcePush(request, response)
                }

                server.get("/api/vod-source/list") { _, response ->
                    handleVodSourceList(response)
                }

                server.get("/api/about") { _, response ->
                    handleAboutGet(response)
                }

                server.get("/api/logs") { _, response ->
                    handleLogsGet(response)
                }

                server.get("/api/file/content") { request, response ->
                    handleFileContentGet(request, response)
                }

                server.post("/api/file/content") { request, response ->
                    handleFileContentPost(request, response)
                }

                server.post("/api/file/content-with-dir") { request, response ->
                    handleFileContentWithDirPost(request, response)
                }

                // 咪咕直播源 - 内置后端
                server.get("/migu.m3u") { request, response ->
                    handleMiguM3u(request, response, context)
                }

                server.get("/migu.txt") { request, response ->
                    handleMiguTxt(request, response, context)
                }

                // 咪咕频道播放地址 - 返回 302 重定向到实际流地址
                server.get("/migu/(.+)") { request, response ->
                    handleMiguChannel(request, response, context, isFallback = false)
                }

                // 兜底播放专用端点（WebView 用）
                server.get("/migufb/(.+)") { request, response ->
                    handleMiguChannel(request, response, context, isFallback = true)
                }

                // M3U8 代理端点 - 代理 TS 片段和子 M3U8
                server.get("/miguprox/(.+)") { request, response ->
                    handleMiguProxy(request, response)
                }

                // === 点播 (VOD) 代理 ===
                server.get("/vod/sources") { _, response ->
                    handleVodSources(response)
                }
                server.get("/vod/categories") { request, response ->
                    handleVodCategories(request, response)
                }
                server.get("/vod/list") { request, response ->
                    handleVodList(request, response)
                }
                server.get("/vod/search") { request, response ->
                    handleVodSearch(request, response)
                }
                server.get("/vod/search-multi") { request, response ->
                    handleVodSearchMulti(request, response)
                }
                // ===== 手机扫码搜索（点播）=====
                server.get("/vod/remote-search") { _, response ->
                    handleVodRemoteSearchPage(response)
                }
                server.post("/api/vod/remote-search") { request, response ->
                    handleVodRemoteSearchSubmit(request, response)
                }
                log.i("已注册扫码搜索路由: GET /vod/remote-search, POST /api/vod/remote-search")

                server.get("/vod/detail") { request, response ->
                    handleVodDetail(request, response)
                }

                // 所有路由注册完成后再开始监听，并标记就绪
                server.listen(AsyncServer.getDefault(), SERVER_PORT)
                markReady()
                log.i("设置服务已启动: $serverUrl")

                // 预热：把首次播放请求要付的冷启动成本提前做掉
                // 1) 咪咕频道列表（/migu.m3u 首次请求依赖）
                // 2) 咪咕 API 的 DNS + TLS 握手（playurl 首次请求依赖）
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        val manager = MiguBackendManager.getInstance(context)
                        manager.loadAllChannels()
                        log.i("咪咕后端预热完成")
                    }.onFailure { log.e("咪咕后端预热失败: ${it.message}") }

                    runCatching {
                        // 轻量握手预热：只做 DNS + TLS，不解析完整响应体
                        val warm = Request.Builder()
                            .head()
                            .url("https://play.miguvideo.com/")
                            .build()
                        proxyClient.newCall(warm).execute().close()
                        log.i("咪咕 API 连接预热完成")
                    }.onFailure { log.i("咪咕 API 预热跳过: ${it.message}") }
                }
            } catch (ex: Exception) {
                log.e("设置服务启动失败: ${ex.message}", ex)
                launch(Dispatchers.Main) {
                    Snackbar.show("设置服务启动失败", type = SnackbarType.ERROR)
                }
            }
        }
    }

    private fun wrapResponse(response: AsyncHttpServerResponse) = response.apply {
        headers.set("Access-Control-Allow-Methods", "POST, GET, DELETE, PUT, OPTIONS")
        headers.set("Access-Control-Allow-Origin", "*")
        headers.set("Access-Control-Allow-Headers", "Origin, Content-Type, X-Auth-Token")
    }

    private fun responseSuccess(response: AsyncHttpServerResponse) {
        wrapResponse(response).apply {
            setContentType("application/json")
            send("{\"code\": 0}")
        }
    }

    private fun handleRawResource(
        response: AsyncHttpServerResponse,
        context: Context,
        contentType: String,
        id: Int,
    ) {
        wrapResponse(response).apply {
            setContentType(contentType)
            send(context.resources.openRawResource(id).use { it.readBytes().decodeToString() })
        }
    }

    private fun handleGetInfo(response: AsyncHttpServerResponse) {
        wrapResponse(response).apply {
            setContentType("application/json")
            send(
                Globals.json.encodeToString(
                    AppInfo(
                        appTitle = Constants.APP_TITLE,
                        appRepo = Constants.APP_REPO,
                        logHistory = Logger.history,
                    )
                )
            )
        }
    }

    private fun handleIptvSourcePush(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
    ) {
        val body = request.getBody<JSONObjectBody>().get()
        val name = body.get("name").toString()
        val type = body.get("type").toString()
        val url = body.get("url").toString()
        val filePath = body.get("filePath").toString()
        val content = body.get("content").toString()

        var newIptvSource: IptvSource? = null

        when (type) {
            "url" -> {
                newIptvSource = IptvSource(name, url)
            }

            "file" -> {
                newIptvSource = IptvSource(name, filePath, true)
            }

            "content" -> {
                val file =
                    File(Globals.fileDir, "iptv_source_local_${System.currentTimeMillis()}.txt")
                file.writeText(content)
                newIptvSource = IptvSource(name, file.path, true)
            }
        }

        newIptvSource?.let {
            Configs.iptvSourceList = IptvSourceList(Configs.iptvSourceList + it)
            Configs.iptvSourceCurrent = it
        }

        responseSuccess(response)
    }

    // ===================== 手机扫码搜索（点播） =====================

    /** 手机端页面：输入片名 → 确定 → 电视端立即搜索 */
    private fun handleVodRemoteSearchPage(response: AsyncHttpServerResponse) {
        log.i("命中扫码搜索页面请求")
        response.setContentType("text/html; charset=utf-8")
        response.send(VOD_REMOTE_SEARCH_HTML)
    }

    /** 手机端提交搜索关键词 */
    private fun handleVodRemoteSearchSubmit(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                wrapResponse(response)
                response.setContentType("application/json; charset=utf-8")

                val keyword = runCatching {
                    request.getBody<JSONObjectBody>().get().optString("q", "")
                }.getOrNull()?.trim().orEmpty()

                if (keyword.isEmpty()) {
                    response.send("""{"ok":false,"msg":"请输入片名"}""")
                    return@launch
                }

                val accepted = top.yogiczy.mytv.tv.ui.screen.vod.VodRemoteSearchBus.submit(keyword)
                if (accepted) {
                    log.i("手机扫码搜索: $keyword")
                    response.send("""{"ok":true,"q":"$keyword"}""")
                } else {
                    log.i("手机扫码搜索被拒绝（电视端不在点播页）: $keyword")
                    response.send("""{"ok":false,"msg":"电视端不在点播页面，请回到点播后再扫码"}""")
                }
            } catch (e: Exception) {
                log.e("手机扫码搜索失败: ${e.message}", e)
                runCatching {
                    response.setContentType("application/json; charset=utf-8")
                    response.send("""{"ok":false,"msg":"服务异常"}""")
                }
            }
        }
    }

    private val VOD_REMOTE_SEARCH_HTML = """
<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>手机搜索点播</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  body {
    margin: 0; min-height: 100vh; padding: 28px 20px;
    font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif;
    background: #12151c; color: #e8ecf3;
    display: flex; flex-direction: column; align-items: center;
  }
  h1 { font-size: 20px; margin: 8px 0 4px; font-weight: 600; }
  p.tip { color: #8b95a7; font-size: 13px; margin: 0 0 22px; text-align: center; line-height: 1.5; }
  .card { width: 100%; max-width: 420px; }
  input {
    width: 100%; padding: 15px 16px; font-size: 17px;
    border-radius: 12px; border: 1px solid #2b3242; background: #1a1f2b; color: #e8ecf3;
    outline: none;
  }
  input:focus { border-color: #5b8cff; }
  button {
    width: 100%; margin-top: 14px; padding: 15px 16px; font-size: 17px; font-weight: 600;
    border: none; border-radius: 12px; background: #3d6cf5; color: #fff;
    transition: opacity .15s;
  }
  button:active { opacity: .75; }
  #status { margin-top: 16px; font-size: 14px; min-height: 20px; text-align: center; color: #9fb0c9; }
  #status.ok { color: #55d08a; }
  #status.err { color: #ff6b6b; }
</style>
</head>
<body>
  <h1>搜索点播</h1>
  <p class="tip">手机与电视需在同一局域网。<br>输入片名后点「确定搜索」，电视端会立即开始搜索。</p>
  <div class="card">
    <input id="q" type="text" placeholder="输入片名，例如：寻踪者" autocomplete="off" autocorrect="off" autocapitalize="off">
    <button id="go">确定搜索</button>
    <div id="status"></div>
  </div>
<script>
(function () {
  var input = document.getElementById('q');
  var btn = document.getElementById('go');
  var statusEl = document.getElementById('status');
  var busy = false;

  function setStatus(text, cls) {
    statusEl.textContent = text || '';
    statusEl.className = cls || '';
  }

  async function submit() {
    var q = (input.value || '').trim();
    if (!q) { setStatus('请输入片名', 'err'); return; }
    if (busy) return;
    busy = true;
    setStatus('发送中…', '');
    try {
      var resp = await fetch('/api/vod/remote-search', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ q: q })
      });
      var data = await resp.json().catch(function () { return {}; });
      if (data && data.ok) {
        setStatus('已发送：' + q + '　电视端正在搜索', 'ok');
        input.value = '';
        input.focus();
      } else {
        setStatus('发送失败：' + ((data && data.msg) || '未知错误'), 'err');
      }
    } catch (e) {
      setStatus('网络错误：' + e.message, 'err');
    } finally {
      busy = false;
    }
  }

  btn.addEventListener('click', submit);
  input.addEventListener('keydown', function (e) {
    if (e.key === 'Enter') { e.preventDefault(); submit(); }
  });
  input.focus();
})();
</script>
</body>
</html>
    """.trimIndent()

    private fun handleVodSourcePush(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
    ) {
        val body = request.getBody<JSONObjectBody>().get()
        val key = body.get("key").toString().ifBlank { "custom_${System.currentTimeMillis()}" }
        val name = body.get("name").toString()
        val api = body.get("api").toString()

        if (name.isBlank() || api.isBlank()) {
            wrapResponse(response).apply { send("名称和接口不能为空") }
            return
        }

        val newSource = VodSource(key = key, name = name, api = api, ad = "clean")
        Configs.vodSourceList = VodSourceList(Configs.vodSourceList + newSource)

        responseSuccess(response)
    }

    private fun handleVodSourceList(response: AsyncHttpServerResponse) {
        val allSources = Constants.VOD_SOURCE_LIST + Configs.vodSourceList
        val json = Globals.json.encodeToString(allSources)
        wrapResponse(response).apply { send(json) }
    }

    private fun handleAboutGet(response: AsyncHttpServerResponse) {
        wrapResponse(response).apply {
            setContentType("application/json")
            send(
                Globals.json.encodeToString(
                    AppAbout(
                        applicationId = BuildConfig.APPLICATION_ID,
                        flavor = BuildConfig.FLAVOR,
                        buildType = BuildConfig.BUILD_TYPE,
                        versionCode = BuildConfig.VERSION_CODE,
                        versionName = BuildConfig.VERSION_NAME,
                        deviceName = Globals.deviceName,
                        deviceId = Globals.deviceId,
                    )
                )
            )
        }
    }

    private fun handleLogsGet(response: AsyncHttpServerResponse) {
        wrapResponse(response).apply {
            setContentType("application/json")
            send(Globals.json.encodeToString(Logger.history))
        }
    }

    private fun handleFileContentGet(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse
    ) {
        val path = request.query.getString("path")

        val file = File(path).takeIf { it.exists() } ?: run {
            return response.code(404).send("File not found")
        }

        wrapResponse(response).send(file.readText())
    }

    private fun handleFileContentPost(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse
    ) {
        val body = request.getBody<JSONObjectBody>().get()
        val path = body.get("path").toString()
        val content = body.get("content").toString()

        val file = File(path)

        if (file.exists()) {
            file.writeText(content)
        } else {
            file.parentFile?.mkdirs()
            file.writeText(content)
        }

        responseSuccess(response)
    }

    private fun handleFileContentWithDirPost(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse
    ) {
        val body = request.getBody<JSONObjectBody>().get()
        val dir = body.get("dir").toString()
        val filename = body.get("filename").toString()
        val content = body.get("content").toString()

        val file = when (dir) {
            "cache" -> File(Globals.cacheDir, filename)
            "file" -> File(Globals.fileDir, filename)
            else -> return response.code(400).send("Invalid dir")
        }

        if (file.exists()) {
            file.writeText(content)
        } else {
            file.parentFile?.mkdirs()
            file.writeText(content)
        }

        wrapResponse(response).send(file.path)
    }

    /**
     * 取本机局域网 IPv4。
     *
     * 关键点：必须排除 VPN / 蜂窝 / 虚拟网卡（tun、ppp、rmnet、vgate、dummy、ifb…），
     * 否则可能返回一个「看起来像内网但实际不可达」的地址，
     * 导致播放器连 http://<错IP>:10481 直接 EHOSTUNREACH → Media3 2001。
     * 优先 wlan / eth / ap 网卡。
     */
    private fun getLocalIpAddress(): String {
        val defaultIp = "127.0.0.1"
        val excludePrefix = listOf(
            "tun", "tap", "ppp", "rmnet", "ccmni", "vgate", "vpn",
            "dummy", "ifb", "sit", "ip6tnl", "gre", "p2p", "rev_rmnet",
        )

        fun isValidIpv4(ip: String): Boolean = when {
            ip.startsWith("192.168.") -> true
            ip.startsWith("10.") -> true
            ip.startsWith("172.") -> {
                val second = ip.split(".").getOrNull(1)?.toIntOrNull()
                second in 16..31
            }
            else -> false
        }

        try {
            val candidates = mutableListOf<Pair<String, String>>() // (intfName, ip)
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                if (!intf.isUp) continue
                val name = intf.name ?: ""
                if (excludePrefix.any { name.startsWith(it) }) continue

                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        val ip = inetAddress.hostAddress ?: continue
                        if (isValidIpv4(ip)) candidates.add(name to ip)
                    }
                }
            }

            // 优先 wlan / eth / ap，其余退而求其次
            val preferred = candidates.firstOrNull { (n, _) ->
                n.startsWith("wlan") || n.startsWith("eth") || n.startsWith("ap")
            }
            val picked = preferred ?: candidates.firstOrNull()
            if (picked != null) {
                log.i("本机局域网 IP: ${picked.second} (${picked.first})")
                return picked.second
            }

            return defaultIp
        } catch (ex: SocketException) {
            log.e("IP Address: ${ex.message}", ex)
            return defaultIp
        }
    }

    fun startService(context: Context) {
        runCatching {
            context.startService(Intent(context, HttpServerService::class.java))
        }.onFailure { it.printStackTrace() }
    }

    fun stopService(context: Context) {
        runCatching {
            context.stopService(Intent(context, HttpServerService::class.java))
        }.onFailure { it.printStackTrace() }
    }

    /**
     * 处理咪咕频道播放地址请求 - 返回 302 重定向
     */
    /**
     * 处理 M3U8 代理请求 - 代理 TS 片段和子 M3U8 文件
     */
    private fun handleMiguProxy(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                wrapResponse(response)
                val encodedUrl = request.path.substringAfter("/miguprox/")
                val targetUrl = java.net.URLDecoder.decode(encodedUrl, "UTF-8")
                
                log.i("代理请求: ${targetUrl.take(80)}...")
                
                val req = Request.Builder().url(targetUrl).get().build()
                proxyClient.newCall(req).execute().use { resp ->
                    val contentType = resp.header("Content-Type") ?: ""
                    val bodyBytes = resp.body?.bytes() ?: ByteArray(0)
                    val bodyStr = String(bodyBytes, Charsets.UTF_8)
                    
                    if (bodyStr.startsWith("#EXTM3U") || contentType.contains("mpegurl") || targetUrl.contains(".m3u8")) {
                        // M3U8 文件，重写 URL
                        val baseUrl = resp.request.url.toString()
                        val rewritten = rewriteM3u8Urls(bodyStr, baseUrl)
                        response.setContentType("application/vnd.apple.mpegurl")
                        response.send(rewritten)
                        log.i("代理 M3U8 子文件: ${rewritten.length} 字节, 前200字: ${rewritten.take(200)}")
                    } else {
                        // TS 片段，写入临时文件再发送，确保二进制完整
                        val tempFile = java.io.File(Globals.cacheDir, "tsproxy_${System.currentTimeMillis()}_${(0..999999).random()}.ts")
                        tempFile.writeBytes(bodyBytes)
                        response.setContentType("video/mp2t")
                        response.sendFile(tempFile)
                        // 不能立刻删除（可能还没写完），延迟 30s 清理；同时顺手清理 2 分钟前的残留
                        CoroutineScope(Dispatchers.IO).launch {
                            kotlinx.coroutines.delay(30_000)
                            runCatching { tempFile.delete() }
                            runCatching {
                                val now = System.currentTimeMillis()
                                Globals.cacheDir.listFiles()
                                    ?.filter { it.name.startsWith("tsproxy_") && now - it.lastModified() > 120_000 }
                                    ?.forEach { it.delete() }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                log.e("代理请求失败: ${e.message}", e)
                response.send("代理失败")
            }
        }
    }

    private fun handleMiguChannel(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse, context: Context, isFallback: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                wrapResponse(response)
                val pid = request.path.substringAfterLast("/")
                
                // 去重: 5秒内同一 pid 复用缓存的播放地址
                val cached = miguResultCache[pid]
                val result = if (cached != null && System.currentTimeMillis() - cached.first < 5000) {
                    // 复用缓存的 URL, 构造一个成功结果
                    val r = MiguApi.getStreamUrl720p(pid) // 缓存只用于减少API调用, 仍需获取最新
                    r
                } else {
                    val r = MiguApi.getStreamUrl720p(pid)
                    if (r.isSuccess) {
                        miguResultCache[pid] = System.currentTimeMillis() to r.url
                    }
                    r
                }
                
                if (!result.isSuccess) {
                    // 冷启动/网络抖动：重试一次（playurl 首次请求常因 DNS/TLS 握手慢而失败）
                    log.w("获取播放地址失败(${result.error})，300ms 后重试一次")
                    kotlinx.coroutines.delay(300)
                    val retry = MiguApi.getStreamUrl720p(pid)
                    if (!retry.isSuccess) {
                        response.send("获取播放地址失败: ${retry.error}")
                        log.e("获取播放地址重试仍失败: ${retry.error}")
                        return@launch
                    }
                    if (retry.isSuccess) {
                        miguResultCache[pid] = System.currentTimeMillis() to retry.url
                    }
                    val finalUrlRetry = resolveMiguRedirect(retry.url)
                    val target = if (finalUrlRetry.isNotEmpty()) finalUrlRetry else retry.url
                    // 统一走本地代理：播放器只连 localhost，不再直连 CDN
                    proxyM3u8(target, response)
                    log.i("重试成功（代理）: ${target.take(80)}...")
                    return@launch
                }
                
                // 统一走本地代理：把远端 M3U8 拉下来、改写分片地址指向 /miguprox/，
                // 播放器全程只连 localhost:10481。
                // 这样设备 IPv6 半死 / DNS 异常 / CDN 直连被拦 都不会导致 Media3 报 2001。
                val finalUrlAny = resolveMiguRedirect(result.url)
                val targetAny = if (finalUrlAny.isNotEmpty()) finalUrlAny else result.url
                proxyM3u8(targetAny, response)
                log.i("代理 M3U8: ${targetAny.take(80)}...")
                return@launch
            } catch (e: Exception) {
                log.e("处理咪咕频道请求失败: ${e.message}", e)
                response.send("错误: ${e.message}")
            }
        }
    }
    
    /**
     * 代理 M3U8 内容 - 获取远程 M3U8，重写 URL 指向本地代理
     */
    private fun proxyM3u8(remoteUrl: String, response: AsyncHttpServerResponse) {
        try {
            // 检查 M3U8 缓存 (30s TTL)
            val cached = m3u8Cache[remoteUrl]
            if (cached != null && System.currentTimeMillis() - cached.first < M3U8_CACHE_TTL) {
                response.send(cached.second)
                return
            }

            val req = Request.Builder().url(remoteUrl).get().build()
            proxyClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                val baseUrl = resp.request.url.toString()
                
                // 重写 M3U8 中的相对 URL 为本地代理 URL
                val rewritten = rewriteM3u8Urls(body, baseUrl)
                
                response.setContentType("application/vnd.apple.mpegurl")
                response.send(rewritten)
                // 缓存 M3U8 内容 (30s TTL)
                m3u8Cache[remoteUrl] = System.currentTimeMillis() to rewritten
                // 清理过期缓存
                if (m3u8Cache.size > 20) {
                    val now = System.currentTimeMillis()
                    m3u8Cache.entries.removeAll { now - it.value.first > M3U8_CACHE_TTL }
                }
                log.i("代理 M3U8 成功, ${body.length} -> ${rewritten.length} 字节")
            }
        } catch (e: Exception) {
            log.e("代理 M3U8 失败: ${e.message}", e)
            response.send("#EXTM3U\n# 代理失败: ${e.message}\n")
        }
    }
    
    /**
     * 重写 M3U8 中的 URL，将相对 URL 转为本地代理 URL
     */
    private fun rewriteM3u8Urls(m3u8Content: String, baseUrl: String): String {
        val serverBase = loopbackUrl + "/miguprox/"
        val lines = m3u8Content.split("\n", "\r\n")
        val result = StringBuilder()
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                result.append(line).append("\n")
            } else {
                // 这是一个 URL 行，将相对 URL 转为绝对 URL，再转为代理 URL
                val absoluteUrl = resolveRelativeUrl(trimmed, baseUrl)
                val encoded = java.net.URLEncoder.encode(absoluteUrl, "UTF-8")
                result.append(serverBase).append(encoded).append("\n")
            }
        }
        
        return result.toString()
    }
    
    /**
     * 将相对 URL 解析为绝对 URL
     */
    private fun resolveRelativeUrl(relativeUrl: String, baseUrl: String): String {
        return try {
            val base = java.net.URL(baseUrl)
            java.net.URL(base, relativeUrl).toString()
        } catch (e: Exception) {
            relativeUrl
        }
    }
    
    /**
     * 请求 migu 流地址，解析重定向获取最终播放 URL
     * migu CDN 会对带 ddCalcu 的 URL 做重定向到最终 CDN 节点
     */
    private fun resolveMiguRedirect(streamUrl: String): String {
        return try {
            val req = Request.Builder().url(streamUrl).get().build()
            noRedirectClient.newCall(req).execute().use { resp ->
                val location = resp.header("Location")
                if (location != null && location.isNotEmpty() && location.startsWith("http")) {
                    log.i("migu 重定向解析: ${location.take(80)}...")
                    location
                } else {
                    // 没有重定向，可能流地址本身就是最终地址
                    ""
                }
            }
        } catch (e: Exception) {
            log.e("migu 重定向解析失败: ${e.message}", e)
            ""
        }
    }

    /**
     * 处理咪咕 M3U 请求
     */
    private fun handleMiguM3u(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse, context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                wrapResponse(response)
                response.setContentType("audio/x-mpegurl")
                
                val manager = MiguBackendManager.getInstance(context)
                val categories = manager.loadAllChannels()
                // 播放器就在本机：一律用回环地址，避免局域网 IP 变化/不可达导致 2001
                val hostUrl = loopbackUrl
                
                val m3uContent = MiguM3uGenerator.generateM3u(categories, hostUrl)
                response.send(m3uContent)
                
                log.i("咪咕 M3U 生成成功: ${categories.sumOf { it.dataList.size }} 个频道")
            } catch (e: Exception) {
                log.e("咪咕 M3U 生成失败: ${e.message}", e)
                response.send("#EXTM3U\n# 生成失败: ${e.message}\n")
            }
        }
    }

    /**
     * 处理咪咕 TXT 请求 (TVAuto 格式)
     */
    private fun handleMiguTxt(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse, context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                wrapResponse(response)
                response.setContentType("text/plain")
                
                val manager = MiguBackendManager.getInstance(context)
                val categories = manager.loadAllChannels()
                // 播放器就在本机：一律用回环地址，避免局域网 IP 变化/不可达导致 2001
                val hostUrl = loopbackUrl
                
                val txtContent = MiguM3uGenerator.generateTxt(categories, hostUrl)
                response.send(txtContent)
                
                log.i("咪咕 TXT 生成成功")
            } catch (e: Exception) {
                log.e("咪咕 TXT 生成失败: ${e.message}", e)
                response.send("# 生成失败: ${e.message}")
            }
        }
    }

    // === VOD 代理处理函数 ===

    private fun handleVodSources(response: AsyncHttpServerResponse) {
        try {
            wrapResponse(response)
            val sources = Constants.VOD_SOURCE_LIST + Configs.vodSourceList
            val json = Globals.json.encodeToString(sources)
            response.send(json)
        } catch (e: Exception) {
            log.e("VOD 源列表失败: ${e.message}", e)
            response.send("[]")
        }
    }

    private fun handleVodCategories(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        try {
            wrapResponse(response)
            val sourceKey = request.query.getString("source") ?: ""
            val source = (Constants.VOD_SOURCE_LIST + Configs.vodSourceList).find { it.key == sourceKey }
                ?: throw Exception("采集源不存在: $sourceKey")
            val url = "${source.api}?ac=list"
            val body = fetchUrl(url)
            val cmsResp = Globals.json.decodeFromString(VodCmsListResponse.serializer(), body)
            val cats = cmsResp.`class`.map {
                top.yogiczy.mytv.core.data.entities.vod.VodCategory(
                    type_id = it.type_id,
                    type_name = it.type_name,
                    type_pid = it.type_pid,
                )
            }
            response.send(Globals.json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(top.yogiczy.mytv.core.data.entities.vod.VodCategory.serializer()),
                cats
            ))
        } catch (e: Exception) {
            log.e("VOD 分类失败: ${e.message}", e)
            response.send("[]")
        }
    }

    private fun handleVodList(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        try {
            wrapResponse(response)
            val sourceKey = request.query.getString("source") ?: ""
            val typeId = request.query.getString("t") ?: ""
            val page = request.query.getString("page") ?: "1"
            val source = (Constants.VOD_SOURCE_LIST + Configs.vodSourceList).find { it.key == sourceKey }
                ?: throw Exception("采集源不存在: $sourceKey")
            val url = buildString {
                append(source.api).append("?ac=videolist&pg=").append(page)
                if (typeId.isNotEmpty()) append("&t=").append(typeId)
            }
            val body = fetchUrl(url)
            val cmsResp = Globals.json.decodeFromString(VodCmsListResponse.serializer(), body)
            val items = cmsResp.list.map { it.toVodItem() }
            val result = top.yogiczy.mytv.core.data.entities.vod.VodItemList(
                list = items, page = cmsResp.page,
                pagecount = cmsResp.pagecount, total = cmsResp.total
            )
            response.send(Globals.json.encodeToString(
                top.yogiczy.mytv.core.data.entities.vod.VodItemList.serializer(), result
            ))
        } catch (e: Exception) {
            log.e("VOD 列表失败: ${e.message}", e)
            response.send("""{"list":[],"pagecount":0}""")
        }
    }

    private fun handleVodSearch(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        try {
            wrapResponse(response)
            val sourceKey = request.query.getString("source") ?: ""
            val wd = request.query.getString("wd") ?: ""
            val page = request.query.getString("page") ?: "1"
            val source = (Constants.VOD_SOURCE_LIST + Configs.vodSourceList).find { it.key == sourceKey }
                ?: throw Exception("采集源不存在: $sourceKey")
            val url = "${source.api}?wd=${java.net.URLEncoder.encode(wd, "UTF-8")}&pg=$page"
            val body = fetchUrl(url)
            val cmsResp = Globals.json.decodeFromString(VodCmsListResponse.serializer(), body)
            val items = cmsResp.list.map { it.toVodItem() }
            val result = top.yogiczy.mytv.core.data.entities.vod.VodItemList(
                list = items, page = cmsResp.page,
                pagecount = cmsResp.pagecount, total = cmsResp.total
            )
            response.send(Globals.json.encodeToString(
                top.yogiczy.mytv.core.data.entities.vod.VodItemList.serializer(), result
            ))
        } catch (e: Exception) {
            log.e("VOD 搜索失败: ${e.message}", e)
            response.send("""{"list":[],"pagecount":0}""")
        }
    }

    /**
     * 多源并发搜索: 取5个源同时搜索, 聚合去重后返回
     * 参数: wd=关键词, offset=批次偏移(每次+5换源)
     * 智能回退: 全词搜不到时, 逐步缩短关键词重试, 最后用原始关键词过滤结果
     */
    private fun handleVodSearchMulti(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                wrapResponse(response)
                val wd = request.query.getString("wd") ?: ""
                val offset = (request.query.getString("offset") ?: "0").toIntOrNull() ?: 0

                val all = Constants.VOD_SOURCE_LIST + Configs.vodSourceList
                if (all.isEmpty()) {
                    response.send("""{"list":[],"pagecount":0}""")
                    return@launch
                }

                val size = all.size
                val batch = (0 until 5).map { i ->
                    val s = all[(offset + i) % size]
                    Triple(s.key, s.name, s.api)
                }

                // 搜索单个源, 返回结果列表
                suspend fun searchOne(key: String, name: String, api: String, keyword: String): List<top.yogiczy.mytv.core.data.entities.vod.VodItem> {
                    val encoded = java.net.URLEncoder.encode(keyword, "UTF-8")
                    return try {
                        withTimeoutOrNull(4000) {
                            val url = "$api?wd=$encoded&pg=1"
                            val req = okhttp3.Request.Builder().url(url)
                                .header("User-Agent", "Mozilla/5.0").build()
                            sharedClient.newCall(req).execute().use { resp ->
                                if (!resp.isSuccessful) return@withTimeoutOrNull emptyList()
                                val body = resp.body?.string() ?: return@withTimeoutOrNull emptyList()
                                if (!body.trimStart().startsWith("{")) return@withTimeoutOrNull emptyList()
                                val cms = Globals.json.decodeFromString(VodCmsListResponse.serializer(), body)
                                cms.list.map { it.toVodItem().copy(sourceKey = key, sourceName = name) }
                            }
                        } ?: emptyList()
                    } catch (_: Exception) { emptyList() }
                }

                // 第一轮: 用原始关键词搜索
                var results = batch.map { (key, name, api) ->
                    async(Dispatchers.IO) { searchOne(key, name, api, wd) }
                }.awaitAll().flatten().distinctBy { it.vod_name }

                // 智能回退: 如果全词搜不到, 逐步缩短关键词
                if (results.isEmpty() && wd.length > 2) {
                    // 生成缩短的关键词候选: 去掉末尾的非中文字符, 再逐步去掉末尾字符
                    val chinesePart = wd.takeWhile { it.code in 0x4E00..0x9FFF }
                    val candidates = mutableListOf<String>()
                    if (chinesePart.isNotEmpty() && chinesePart != wd) {
                        candidates.add(chinesePart)  // "大明1566" → "大明"
                    }
                    // 逐步去掉末尾字符 (至少保留2个字符)
                    var shortened = wd
                    while (shortened.length > 2) {
                        shortened = shortened.dropLast(1)
                        if (shortened != chinesePart) candidates.add(shortened)
                    }

                    for (kw in candidates.distinct()) {
                        results = batch.map { (key, name, api) ->
                            async(Dispatchers.IO) { searchOne(key, name, api, kw) }
                        }.awaitAll().flatten().distinctBy { it.vod_name }
                        if (results.isNotEmpty()) {
                            // 用原始关键词过滤结果 (模糊匹配: 原始关键词的数字部分需在标题中)
                            val filtered = results.filter { item ->
                                wd.all { ch ->
                                    if (ch.isDigit()) item.vod_name.contains(ch) || item.vod_name.any { it == ch }
                                    else true  // 中文字符已在搜索中匹配
                                }
                            }
                            results = if (filtered.isNotEmpty()) filtered else results
                            log.i("多源搜索回退: '$wd' → 用'$kw'搜索, ${results.size}条结果")
                            break
                        }
                    }
                }

                val result = top.yogiczy.mytv.core.data.entities.vod.VodItemList(
                    list = results, page = 1, pagecount = 1, total = results.size
                )
                response.send(Globals.json.encodeToString(
                    top.yogiczy.mytv.core.data.entities.vod.VodItemList.serializer(), result
                ))
                log.i("多源搜索完成: '$wd' → ${results.size} 条结果")
            } catch (e: Exception) {
                log.e("多源搜索失败: ${e.message}", e)
                response.send("""{"list":[],"pagecount":0}""")
            }
        }
    }

    /** 从拼音字符串提取首字母 (如 "zhanlang" → "zl") */
    private fun handleVodDetail(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        try {
            wrapResponse(response)
            val sourceKey = request.query.getString("source") ?: ""
            val id = request.query.getString("id") ?: ""
            val source = (Constants.VOD_SOURCE_LIST + Configs.vodSourceList).find { it.key == sourceKey }
                ?: throw Exception("采集源不存在: $sourceKey")
            val url = "${source.api}?ac=detail&ids=$id"
            val body = fetchUrl(url)
            val cmsResp = Globals.json.decodeFromString(VodCmsListResponse.serializer(), body)
            val item = cmsResp.list.firstOrNull() ?: throw Exception("未找到影片")
            val detail = item.toVodDetail(sourceKey)
            response.send(Globals.json.encodeToString(
                top.yogiczy.mytv.core.data.entities.vod.VodDetail.serializer(), detail
            ))
        } catch (e: Exception) {
            log.e("VOD 详情失败: ${e.message}", e)
            response.send("""{"vod_name":"","play_sources":[]}""")
        }
    }

    /** HTTP GET 抓取 */
    private fun fetchUrl(url: String): String {
        val req = okhttp3.Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()
        sharedClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            return resp.body?.string() ?: throw Exception("空响应")
        }
    }
}

/** CMS 条目转 VodItem */
private fun VodCmsItem.toVodItem() = top.yogiczy.mytv.core.data.entities.vod.VodItem(
    vod_id = vod_id.toString(),
    vod_name = vod_name,
    vod_pic = vod_pic,
    vod_remarks = vod_remarks,
    type_name = type_name,
    vod_year = vod_year,
    vod_area = vod_area,
    vod_score = vod_score,
)

/** CMS 条目转 VodDetail (解析 play_from / play_url) */
private fun VodCmsItem.toVodDetail(sourceKey: String): top.yogiczy.mytv.core.data.entities.vod.VodDetail {
    val lineNames = vod_play_from.split("$$$")
    val lineUrls = vod_play_url.split("$$$")
    val playSources = lineNames.mapIndexed { i, lineName ->
        val eps = (lineUrls.getOrNull(i) ?: "").split("#")
            .filter { it.isNotBlank() }
            .map { ep ->
                val parts = ep.split("$", limit = 2)
                top.yogiczy.mytv.core.data.entities.vod.Episode(
                    name = if (parts.size > 1) parts[0] else "第${ep}集",
                    url = if (parts.size > 1) parts[1] else parts[0],
                )
            }
        top.yogiczy.mytv.core.data.entities.vod.PlaySource(name = lineName.trim(), episodes = eps)
    }.filter { it.episodes.isNotEmpty() }

    return top.yogiczy.mytv.core.data.entities.vod.VodDetail(
        vod_id = vod_id.toString(),
        vod_name = vod_name,
        vod_pic = vod_pic,
        vod_content = vod_content,
        vod_actor = vod_actor,
        vod_director = vod_director,
        vod_area = vod_area,
        vod_year = vod_year,
        type_name = type_name,
        vod_remarks = vod_remarks,
        vod_score = vod_score,
        play_sources = playSources,
        source = sourceKey,
    )
}

@Serializable
private data class AppInfo(
    val appTitle: String,
    val appRepo: String,
    val logHistory: List<Logger.HistoryItem>,
)

@Serializable
private data class AppAbout(
    val applicationId: String,
    val flavor: String,
    val buildType: String,
    val versionCode: Int,
    val versionName: String,
    val deviceName: String,
    val deviceId: String,
)

// === VOD 代理实现 ===

@Serializable
private data class VodCmsItem(
    val vod_id: Int = 0,
    val vod_name: String = "",
    val vod_pic: String = "",
    val vod_remarks: String = "",
    val type_name: String = "",
    val vod_year: String = "",
    val vod_area: String = "",
    val vod_score: String = "",
    val vod_content: String = "",
    val vod_actor: String = "",
    val vod_director: String = "",
    val vod_play_from: String = "",
    val vod_play_url: String = "",
    val vod_en: String = "",
    val vod_letter: String = "",
)

@Serializable
private data class VodCmsListResponse(
    val code: Int = 0,
    val msg: String = "",
    val list: List<VodCmsItem> = emptyList(),
    val page: Int = 1,
    val pagecount: Int = 1,
    val total: Int = 0,
    val limit: Int = 0,
    val `class`: List<VodCmsCategory> = emptyList(),
)

@Serializable
private data class VodCmsCategory(
    val type_id: Int = 0,
    val type_name: String = "",
    val type_pid: Int = 0,
)
