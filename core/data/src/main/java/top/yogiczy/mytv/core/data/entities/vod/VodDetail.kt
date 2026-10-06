package top.yogiczy.mytv.core.data.entities.vod

import kotlinx.serialization.Serializable

/**
 * 播放线路 (一条线路包含多集)
 */
@Serializable
data class PlaySource(
    val name: String = "",
    val episodes: List<Episode> = emptyList(),
)

/**
 * 选集
 */
@Serializable
data class Episode(
    val name: String = "",
    val url: String = "",
)

/**
 * 影片详情 (含播放线路)
 */
@Serializable
data class VodDetail(
    val vod_id: String = "",
    val vod_name: String = "",
    val vod_pic: String = "",
    val vod_content: String = "",
    val vod_actor: String = "",
    val vod_director: String = "",
    val vod_area: String = "",
    val vod_year: String = "",
    val type_name: String = "",
    val vod_remarks: String = "",
    val vod_score: String = "",
    val play_sources: List<PlaySource> = emptyList(),
    /** 来源 key */
    val source: String = "",
)
