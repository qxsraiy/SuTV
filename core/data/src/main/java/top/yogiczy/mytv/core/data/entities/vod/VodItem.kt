package top.yogiczy.mytv.core.data.entities.vod

import kotlinx.serialization.Serializable

/**
 * 影片条目 (列表项)
 */
@Serializable
data class VodItem(
    val vod_id: String = "",
    val vod_name: String = "",
    val vod_pic: String = "",
    val vod_remarks: String = "",
    val type_name: String = "",
    val vod_year: String = "",
    val vod_area: String = "",
    val vod_score: String = "",
    val sourceKey: String = "",
    val sourceName: String = "",
)

@Serializable
data class VodItemList(
    val list: List<VodItem> = emptyList(),
    val page: Int = 1,
    val pagecount: Int = 1,
    val total: Int = 0,
)

/**
 * 影片分类
 */
@Serializable
data class VodCategory(
    val type_id: Int = 0,
    val type_name: String = "",
    val type_pid: Int = 0,
)
