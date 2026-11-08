package top.yogiczy.mytv.core.util.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.io.File
import java.util.concurrent.TimeUnit

object Downloader {
    /** GitHub 加速镜像（主地址重试失败后兜底，prefix + 原始 URL） */
    private val MIRROR_PREFIXES = listOf(
        "", // 原始地址
        "https://ghfast.top/",
        "https://gh-proxy.com/",
    )

    private fun newClient(onProgressCb: ((Int) -> Unit)?): OkHttpClient {
        val interceptor = Interceptor { chain ->
            val originalResponse = chain.proceed(chain.request())
            originalResponse.newBuilder()
                .body(DownloadResponseBody(originalResponse, onProgressCb)).build()
        }
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addNetworkInterceptor(interceptor)
            .build()
    }

    /**
     * 下载到文件（流式写盘）。
     * 主地址失败后自动重试 2 次（间隔 2s/4s），再依次尝试加速镜像。
     * 失败抛出带具体原因的异常（HTTP 码 / 超时 / 连接重置）。
     */
    suspend fun downloadTo(url: String, filePath: String, onProgressCb: ((Int) -> Unit)?) =
        withContext(Dispatchers.IO) {
            var lastError: Exception? = null
            for ((mirrorIdx, prefix) in MIRROR_PREFIXES.withIndex()) {
                val target = prefix + url
                val attempts = if (mirrorIdx == 0) 3 else 1
                for (attempt in 1..attempts) {
                    try {
                        doDownload(target, filePath, onProgressCb)
                        return@withContext
                    } catch (ex: Exception) {
                        lastError = ex
                        if (attempt < attempts) delay(2000L * attempt)
                    }
                }
            }
            throw Exception("下载文件失败: ${lastError?.message ?: "未知错误"}", lastError)
        }

    private fun doDownload(url: String, filePath: String, onProgressCb: ((Int) -> Unit)?) {
        val client = newClient(onProgressCb)
        val request = okhttp3.Request.Builder().url(url)
            .header("User-Agent", "SuTV-Android/2.4")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val body = resp.body ?: throw Exception("空响应")
            val file = File(filePath)
            body.byteStream().use { input ->
                file.outputStream().buffered().use { output ->
                    input.copyTo(output, 64 * 1024)
                }
            }
        }
    }

    private class DownloadResponseBody(
        private val originalResponse: okhttp3.Response,
        private val onProgressCb: ((Int) -> Unit)?,
    ) : okhttp3.ResponseBody() {
        override fun contentLength() = originalResponse.body!!.contentLength()

        override fun contentType() = originalResponse.body?.contentType()

        override fun source(): BufferedSource {
            return object : ForwardingSource(originalResponse.body!!.source()) {
                var totalBytesRead = 0L
                var lastReported = -1

                override fun read(sink: okio.Buffer, byteCount: Long): Long {
                    val bytesRead = super.read(sink, byteCount)
                    totalBytesRead += if (bytesRead != -1L) bytesRead else 0
                    val len = contentLength()
                    if (len > 0) {
                        val progress = (totalBytesRead * 100 / len).toInt()
                        if (progress != lastReported) {
                            lastReported = progress
                            onProgressCb?.invoke(progress)
                        }
                    }
                    return bytesRead
                }
            }.buffer()
        }
    }
}
