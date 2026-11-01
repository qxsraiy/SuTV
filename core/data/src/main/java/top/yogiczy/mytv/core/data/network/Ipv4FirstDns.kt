package top.yogiczy.mytv.core.data.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import okhttp3.Dns

/**
 * IPv4 优先的 DNS 解析。
 *
 * 背景：很多 IPTV 源（含咪咕 CDN）是双栈域名。部分设备/路由器虽然拿到了 IPv6 地址，
 * 但 IPv6 出口实际不通（半死状态），系统会优先用 AAAA 记录去连，结果 TCP 建连失败，
 * Media3 报 ERROR_CODE_IO_NETWORK_CONNECTION_FAILED(2001)。
 * 同一网络下 IPv4-only 的设备反而一切正常 —— 这就是「有的能用有的不行」的根因。
 *
 * 策略：
 *   1) 优先返回全部 IPv4 地址
 *   2) IPv4 存在时，把 IPv6 放在后面作为兜底（仍然是 Happy Eyeballs 式的多地址尝试）
 *   3) 完全没有 IPv4（纯 IPv6 源）时，原样返回 IPv6，保证 IPv6-only 源不受影响
 */
object Ipv4FirstDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val all: List<InetAddress> = try {
            InetAddress.getAllByName(hostname).toList()
        } catch (e: UnknownHostException) {
            throw e
        }

        val v4 = all.filterIsInstance<Inet4Address>()
        val v6 = all.filterIsInstance<Inet6Address>()

        return when {
            v4.isNotEmpty() -> v4 + v6
            v6.isNotEmpty() -> v6
            else -> all
        }
    }
}
