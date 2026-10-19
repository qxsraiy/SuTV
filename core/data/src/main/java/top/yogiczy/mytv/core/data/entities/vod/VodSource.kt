package top.yogiczy.mytv.core.data.entities.vod

import kotlinx.serialization.Serializable

/**
 * 采集源 (苹果CMS API)
 */
@Serializable
data class VodSource(
    /** 唯一英文ID */
    val key: String = "",
    /** 显示名 */
    val name: String = "",
    /** CMS API 地址 (如 https://站点/api.php/provide/vod) */
    val api: String = "",
    /** 广告标记: clean=干净 / intro=仅片头 / burned=正片烧录 */
    val ad: String = "",
    /** 是否默认源 */
    val default: Boolean = false,
)

@Serializable
data class VodSourceList(
    val value: List<VodSource> = emptyList(),
) : List<VodSource> by value
