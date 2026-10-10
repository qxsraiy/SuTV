package top.yogiczy.mytv.tv.utlis

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import android.util.Log
import okhttp3.ConnectionPool
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 网络变化监听：网络切换时清理 OkHttp 连接池，强制重新解析 DNS。
 *
 * 问题场景：WiFi 断开重连后，OkHttp 连接池中保留旧 TCP 连接（已失效），
 * 后续请求复用旧连接会超时而非立即失败，表现为"卡住"。
 * 解决：网络变化时 evictAll() 所有连接池，新请求走全新连接+DNS。
 */
object NetworkMonitor {
    private const val TAG = "NetworkMonitor"
    private val pools = CopyOnWriteArrayList<ConnectionPool>()
    private var registered = false

    fun register(context: Context) {
        if (registered) return
        registered = true
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder().build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "网络恢复，清理连接池")
                pools.forEach { it.evictAll() }
            }
            override fun onLost(network: Network) {
                Log.i(TAG, "网络断开，清理连接池")
                pools.forEach { it.evictAll() }
            }
        })
        Log.i(TAG, "已注册网络监听")
    }

    fun trackPool(pool: ConnectionPool) {
        pools.addIfAbsent(pool)
    }
}
