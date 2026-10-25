package top.yogiczy.mytv.tv.ui.utils

import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.utils.Constants

/**
 * 直播源优先级解析。
 *
 * 规则：
 * - 默认源：用户配置优先；未配置时用内置「咪咕直播 (内置)」
 * - 备用源：用户配置优先（最多 3 个）；未配置时用 3 个内置 GitHub 源
 * - 内置源与自定义源统一按 URL 识别
 */
object IptvSourcePriority {

    /** 全部可选源（内置 + 自定义，按 URL 去重） */
    fun allSources(): List<IptvSource> {
        return (Constants.IPTV_SOURCE_LIST + Configs.iptvSourceList)
            .distinctBy { it.url }
    }

    /** 内置咪咕源 */
    fun builtinMigu(): IptvSource = Constants.IPTV_SOURCE_LIST.first()

    /** 所有内置源的 URL 集合（内置源不可删除） */
    fun builtinUrls(): Set<String> = Constants.IPTV_SOURCE_LIST.map { it.url }.toSet()

    /** 是否内置咪咕源 */
    fun isBuiltinMigu(source: IptvSource): Boolean =
        source.url == builtinMigu().url || source.url.contains("/migu.m3u")

    /** 生效的默认源 */
    fun effectiveDefault(): IptvSource {
        val url = Configs.iptvSourceDefaultUrl
        if (url.isNotBlank()) {
            allSources().firstOrNull { it.url == url }?.let { return it }
        }
        return builtinMigu()
    }

    /** 生效的备用源（最多 3 个） */
    fun effectiveBackups(): List<IptvSource> {
        val configured = Configs.iptvSourceBackupUrls
        if (configured.isNotEmpty()) {
            val resolved = configured.mapNotNull { u -> allSources().firstOrNull { it.url == u } }
            if (resolved.isNotEmpty()) return resolved.distinctBy { it.url }.take(3)
        }
        // 默认备用：3 个内置 GitHub 源
        return Constants.IPTV_SOURCE_LIST
            .filterNot { isBuiltinMigu(it) }
            .take(3)
    }

    /** 清理已失效（被删除）的配置 */
    fun pruneInvalid() {
        val valid = allSources().map { it.url }.toSet()
        if (Configs.iptvSourceDefaultUrl.isNotBlank() && Configs.iptvSourceDefaultUrl !in valid) {
            Configs.iptvSourceDefaultUrl = ""
        }
        val backups = Configs.iptvSourceBackupUrls.filter { it in valid }.toSet()
        if (backups != Configs.iptvSourceBackupUrls) {
            Configs.iptvSourceBackupUrls = backups
        }
    }
}
