package top.yogiczy.mytv.tv.utlis

import android.content.Context
import android.content.Intent
import com.koushikdutta.async.AsyncServer
import com.koushikdutta.async.http.body.JSONObjectBody
import com.koushikdutta.async.http.body.MultipartFormDataBody
import com.koushikdutta.async.http.body.StringBody
import com.koushikdutta.async.http.server.AsyncHttpServer
import com.koushikdutta.async.http.server.AsyncHttpServerRequest
import com.koushikdutta.async.http.server.AsyncHttpServerResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.Request
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSource
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSourceList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList
import top.yogiczy.mytv.core.data.repositories.epg.EpgRepository
import top.yogiczy.mytv.core.data.repositories.iptv.IptvRepository
import top.yogiczy.mytv.core.data.utils.ChannelAlias
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.data.utils.Loggable
import top.yogiczy.mytv.core.data.utils.Logger
import top.yogiczy.mytv.core.util.utils.ApkInstaller
import top.yogiczy.mytv.tv.BuildConfig
import top.yogiczy.mytv.tv.HttpServerService
import top.yogiczy.mytv.tv.R
import top.yogiczy.mytv.tv.migu.MiguApi
import top.yogiczy.mytv.tv.migu.MiguBackendManager
import top.yogiczy.mytv.tv.migu.MiguM3uGenerator
import top.yogiczy.mytv.tv.sync.CloudSync
import top.yogiczy.mytv.tv.sync.CloudSyncData
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.material.SnackbarType
import top.yogiczy.mytv.tv.ui.utils.Configs
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException


object HttpServer : Loggable("HttpServer") {
    private const val SERVER_PORT = 10481
    private val uploadedApkFile by lazy {
        File(Globals.cacheDir, "uploaded_apk.apk").apply { deleteOnExit() }
    }

    val serverUrl by lazy { "http://${getLocalIpAddress()}:${SERVER_PORT}" }

    fun start(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val server = AsyncHttpServer()

                server.addAction("OPTIONS", ".+") { _, response ->
                    wrapResponse(response)
                    response.send("ok")
                }

                server.listen(AsyncServer.getDefault(), SERVER_PORT)

                server.get("/") { _, response ->
                    handleRawResource(response, context, "text/html", R.raw.web_push)
                }
                server.get("/web_push_css.css") { _, response ->
                    handleRawResource(response, context, "text/css", R.raw.web_push_css)
                }
                server.get("/web_push_js.js") { _, response ->
                    handleRawResource(response, context, "text/javascript", R.raw.web_push_js)
                }

                server.get("/advance") { _, response ->
                    handleAssets(response, context, "text/html", "remote-configs/index.html")
                }

                server.get("/remote-configs/(.*)") { request, response ->
                    val contentType = when (request.path.split(".").last()) {
                        "css" -> "text/css"
                        "js" -> "text/javascript"
                        "html" -> "text/html"
                        "json" -> "application/json"
                        "svg" -> "image/svg+xml"
                        "png" -> "image/png"
                        else -> "text/plain"
                    }

                    handleAssets(response, context, contentType, request.path.removePrefix("/"))
                }

                server.get("/api/info") { _, response ->
                    handleGetInfo(response)
                }

                server.post("/api/iptv-source/push") { request, response ->
                    handleIptvSourcePush(request, response)
                }

                server.post("/api/epg-source/push") { request, response ->
                    handleEpgSourcePush(request, response)
                }

                server.get("/api/channel-alias") { _, response ->
                    handleGetChannelAlias(response)
                }

                server.post("/api/channel-alias") { request, response ->
                    handleUpdateChannelAlias(request, response)
                }

                server.get("/api/configs") { _, response ->
                    handleConfigsGet(response)
                }

                server.post("/api/configs") { request, response ->
                    handleConfigsPush(request, response)
                }

                server.post("/api/upload/apk") { request, response ->
                    handleUploadApk(request, response, context)
                }

                server.get("/api/cloud-sync/data") { _, response ->
                    handleCloudSyncDataGet(response)
                }

                server.post("/api/cloud-sync/data") { request, response ->
                    handleCloudSyncDataPost(request, response)
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

                server.post("/api/upload/allinone") { request, response ->
                    handleUploadAllInOne(request, response)
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
                server.get("/vod/detail") { request, response ->
                    handleVodDetail(request, response)
                }

                log.i("设置服务已启动: $serverUrl")
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
            send(context.resources.openRawResource(id).readBytes().decodeToString())
        }
    }

    private fun handleAssets(
        response: AsyncHttpServerResponse,
        context: Context,
        contentType: String,
        filename: String,
    ) {
        wrapResponse(response).apply {
            setContentType(contentType)
            send(context.assets.open(filename).reader().readText())
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

    private fun handleEpgSourcePush(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
    ) {
        val body = request.getBody<JSONObjectBody>().get()
        val name = body.get("name").toString()
        val url = body.get("url").toString()

        EpgSource(name, url).let {
            Configs.epgSourceList = EpgSourceList(Configs.epgSourceList + it)
            Configs.epgSourceCurrent = it
        }

        responseSuccess(response)
    }

    private fun handleGetChannelAlias(response: AsyncHttpServerResponse) {
        wrapResponse(response).apply {
            send(runCatching { ChannelAlias.aliasFile.readText() }.getOrElse { "" })
        }
    }

    private fun handleUpdateChannelAlias(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
    ) {
        val alias = request.getBody<StringBody>().get()

        ChannelAlias.aliasFile.writeText(alias)
        runBlocking {
            IptvRepository.clearAllCache()
            EpgRepository.clearAllCache()
        }

        responseSuccess(response)
    }

    private fun handleConfigsGet(response: AsyncHttpServerResponse) {
        wrapResponse(response).apply {
            setContentType("application/json")
            send(Globals.json.encodeToString(Configs.toPartial()))
        }
    }

    private fun handleConfigsPush(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
    ) {
        val body = request.getBody<JSONObjectBody>().get()
        val configs = Globals.json.decodeFromString<Configs.Partial>(body.toString())
        Configs.fromPartial(configs)

        responseSuccess(response)
    }

    private fun handleUploadApk(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
        context: Context,
    ) {
        val body = request.getBody<MultipartFormDataBody>()

        val os = uploadedApkFile.outputStream()
        val contentLength = request.headers["Content-Length"]?.toLong() ?: 1
        var hasReceived = 0L

        body.setMultipartCallback { part ->
            if (part.isFile) {
                body.setDataCallback { _, bb ->
                    val byteArray = bb.allByteArray
                    hasReceived += byteArray.size
                    Snackbar.show(
                        "正在接收文件: ${(hasReceived * 100f / contentLength).toInt()}%",
                        leadingLoading = true,
                        id = "uploading_apk",
                    )
                    os.write(byteArray)
                }
            }
        }

        body.setEndCallback {
            Snackbar.show("正在准备安装，请稍后...", leadingLoading = true, duration = 10_000)
            body.dataEmitter.close()
            os.flush()
            os.close()
            ApkInstaller.installApk(context, uploadedApkFile.path)
        }

        responseSuccess(response)
    }

    private fun handleUploadAllInOne(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse,
    ) {
        val body = request.getBody<MultipartFormDataBody>()

        val contentLength = request.headers["Content-Length"]?.toLong() ?: 1
        var hasReceived = 0L

        val allinoneFile = File(Globals.fileDir, "uploads/allinone").apply { parentFile?.mkdirs() }

        body.setMultipartCallback { part ->
            if (part.isFile) {
                with(allinoneFile.outputStream()) {

                    body.setDataCallback { _, bb ->
                        val byteArray = bb.allByteArray
                        hasReceived += byteArray.size
                        Snackbar.show(
                            "正在接收文件: ${(hasReceived * 100f / contentLength).toInt()}%",
                            leadingLoading = true,
                            id = "uploading_file",
                        )
                        write(byteArray)
                    }
                }
            }
        }

        body.setEndCallback {
            body.dataEmitter.close()
            Configs.feiyangAllInOneFilePath = allinoneFile.absolutePath
            Snackbar.show("文件接收完成", id = "uploading_file")
        }

        responseSuccess(response)
    }

    private fun handleCloudSyncDataGet(response: AsyncHttpServerResponse) {
        wrapResponse(response).apply {
            setContentType("application/json")
            send(Globals.json.encodeToString(runBlocking { CloudSync.getData() }))
        }
    }

    private fun handleCloudSyncDataPost(
        request: AsyncHttpServerRequest,
        response: AsyncHttpServerResponse
    ) {
        val body = request.getBody<JSONObjectBody>().get()
        val data = Globals.json.decodeFromString<CloudSyncData>(body.toString())
        runBlocking { data.apply() }

        responseSuccess(response)
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

    private fun getLocalIpAddress(): String {
        val defaultIp = "127.0.0.1"

        try {
            val en = NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        val ip = inetAddress.hostAddress ?: defaultIp
                        val isValid = when {
                            ip.startsWith("192.168.") -> true
                            ip.startsWith("10.") -> true
                            ip.startsWith("172.") -> {
                                val parts = ip.split(".")
                                val secondOctet = parts.getOrNull(1)?.toIntOrNull()
                                secondOctet in 16..31
                            }

                            else -> false
                        }

                        if (isValid)
                            return ip
                    }
                }
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
                
                val client = okhttp3.OkHttpClient.Builder()
                    .followRedirects(true)
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                val req = Request.Builder().url(targetUrl).get().build()
                client.newCall(req).execute().use { resp ->
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
                        val tempFile = java.io.File(Globals.cacheDir, "tsproxy_${System.currentTimeMillis()}.ts")
                        tempFile.writeBytes(bodyBytes)
                        response.setContentType("video/mp2t")
                        response.sendFile(tempFile)
                        log.i("代理 TS 片段: ${bodyBytes.size} 字节 -> ${tempFile.name}")
                        tempFile.deleteOnExit()
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
                
                log.i("获取咪咕频道播放地址: $pid${if (isFallback) "(兜底)" else ""}")
                val result = MiguApi.getStreamUrl720p(pid)
                
                if (!result.isSuccess) {
                    response.send("获取播放地址失败: ${result.error}")
                    log.e("获取播放地址失败: ${result.error}")
                    return@launch
                }
                
                if (!isFallback) {
                    // 正常模式（ExoPlayer）：302 重定向到流地址
                    val finalUrl = resolveMiguRedirect(result.url)
                    if (finalUrl.isNotEmpty()) {
                        response.redirect(finalUrl)
                        log.i("重定向到: ${finalUrl.take(80)}...")
                    } else {
                        response.redirect(result.url)
                        log.i("直接重定向到: ${result.url.take(80)}...")
                    }
                    return@launch
                }

                // 兜底模式：直接代理 M3U8 内容，避免 CORS
                val finalUrl = resolveMiguRedirect(result.url)
                if (finalUrl.isNotEmpty()) {
                    proxyM3u8(finalUrl, response)
                } else {
                    proxyM3u8(result.url, response)
                }
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
            val client = okhttp3.OkHttpClient.Builder()
                .followRedirects(true)
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            
            val req = Request.Builder().url(remoteUrl).get().build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                val baseUrl = resp.request.url.toString()
                
                // 重写 M3U8 中的相对 URL 为本地代理 URL
                val rewritten = rewriteM3u8Urls(body, baseUrl)
                
                response.setContentType("application/vnd.apple.mpegurl")
                response.send(rewritten)
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
        val serverBase = serverUrl + "/miguprox/"
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
            val client = okhttp3.OkHttpClient.Builder()
                .followRedirects(false)
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            
            val req = Request.Builder().url(streamUrl).get().build()
            client.newCall(req).execute().use { resp ->
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
                val hostUrl = serverUrl
                
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
                val hostUrl = serverUrl
                
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
            val sources = Constants.VOD_SOURCE_LIST
            val json = Globals.json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(top.yogiczy.mytv.core.data.entities.vod.VodSource.serializer()),
                sources.value
            )
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
            val source = Constants.VOD_SOURCE_LIST.find { it.key == sourceKey }
                ?: throw Exception("采集源不存在: $sourceKey")
            val url = "${source.api}?ac=list"
            val body = fetchUrl(url)
            val cmsResp = jsonIgnorant.decodeFromString(VodCmsListResponse.serializer(), body)
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
            val source = Constants.VOD_SOURCE_LIST.find { it.key == sourceKey }
                ?: throw Exception("采集源不存在: $sourceKey")
            val url = buildString {
                append(source.api).append("?ac=videolist&pg=").append(page)
                if (typeId.isNotEmpty()) append("&t=").append(typeId)
            }
            val body = fetchUrl(url)
            val cmsResp = jsonIgnorant.decodeFromString(VodCmsListResponse.serializer(), body)
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
            val source = Constants.VOD_SOURCE_LIST.find { it.key == sourceKey }
                ?: throw Exception("采集源不存在: $sourceKey")
            val url = "${source.api}?wd=${java.net.URLEncoder.encode(wd, "UTF-8")}&pg=$page"
            val body = fetchUrl(url)
            val cmsResp = jsonIgnorant.decodeFromString(VodCmsListResponse.serializer(), body)
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

    /** 从拼音字符串提取首字母 (如 "zhanlang" → "zl") */
    private fun handleVodDetail(request: AsyncHttpServerRequest, response: AsyncHttpServerResponse) {
        try {
            wrapResponse(response)
            val sourceKey = request.query.getString("source") ?: ""
            val id = request.query.getString("id") ?: ""
            val source = Constants.VOD_SOURCE_LIST.find { it.key == sourceKey }
                ?: throw Exception("采集源不存在: $sourceKey")
            val url = "${source.api}?ac=detail&ids=$id"
            val body = fetchUrl(url)
            val cmsResp = jsonIgnorant.decodeFromString(VodCmsListResponse.serializer(), body)
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
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val req = okhttp3.Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()
        client.newCall(req).execute().use { resp ->
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

private val jsonIgnorant = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}