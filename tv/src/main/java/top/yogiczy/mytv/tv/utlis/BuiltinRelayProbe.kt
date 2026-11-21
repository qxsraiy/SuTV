package top.yogiczy.mytv.tv.utlis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yogiczy.mytv.core.data.entities.vod.VodSource
import top.yogiczy.mytv.core.data.network.TrustAllSSLSocketFactory

/**
 * 内置中转源（cmszz）主备线路探测。
 *
 * 内置中转有两个接入点：主地址走 IPv6，备用地址走 IPv4。
 * 进入点播页且用户未自定义默认源时探测一次主地址连通性（结果缓存 60 秒），
 * 不通则自动切到备用地址；UI 全程只显示"内置中转"，不暴露真实链接。
 */
object BuiltinRelayProbe {
    const val KEY = "cmszz"

    /** true = 主地址（v6）不通，改用备用地址（v4） */
    @Volatile
    var useAlt = false
        private set

    @Volatile
    private var lastProbeAt = 0L

    private const val PROBE_INTERVAL_MS = 60_000L

    private val client by lazy {
        okhttp3.OkHttpClient.Builder()
            .sslSocketFactory(
                TrustAllSSLSocketFactory.sslSocketFactory,
                TrustAllSSLSocketFactory.trustManager
            )
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(2500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(3000, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
            .also { NetworkMonitor.trackPool(it.connectionPool) }
    }

    /** 探测主地址连通性；60 秒内不重复探测。拿到任意 HTTP 响应即视为可达，连接异常才切备用 */
    suspend fun probe(primaryApi: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (now - lastProbeAt < PROBE_INTERVAL_MS) return@withContext
        lastProbeAt = now
        useAlt = try {
            client.newCall(
                okhttp3.Request.Builder()
                    .url("$primaryApi?ac=list")
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
            ).execute().close()
            false // 主地址可达
        } catch (_: Exception) {
            true // 连接失败（无 v6 出口等）→ 切备用
        }
    }

    /** 按探测结果返回该源当前应使用的 API 地址 */
    fun apiOf(source: VodSource): String =
        if (source.key == KEY && useAlt && source.altApi.isNotEmpty()) source.altApi else source.api

    /** UI/接口展示用的脱敏地址（内置中转不暴露真实链接） */
    fun displayApi(source: VodSource): String =
        if (source.key == KEY) "内置线路 · 自动选择最优通道" else source.api
}
