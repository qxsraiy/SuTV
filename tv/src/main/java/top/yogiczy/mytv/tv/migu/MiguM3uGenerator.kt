package top.yogiczy.mytv.tv.migu

import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * M3U 播放列表生成器
 * 从 migu_video 的 updateData.js 移植
 */
object MiguM3uGenerator {
    
    /**
     * 生成 M3U 播放列表
     */
    fun generateM3u(categories: List<MiguCategory>, hostUrl: String = "http://localhost:1234"): String {
        val sb = StringBuilder()
        
        // M3U 头部
        sb.append("#EXTM3U\n")
        sb.append("#EXTM3U epg-url=\"$hostUrl/playback.xml\"\n")
        sb.append("#EXT-X-VERSION:3\n\n")
        
        // 频道列表 - 标准 M3U 格式
        for (category in categories) {
            for (channel in category.dataList) {
                if (channel.logo.isNotEmpty()) {
                    sb.append("#EXTINF:-1 tvg-id=\"${channel.pid}\" tvg-name=\"${channel.name}\" tvg-logo=\"${channel.logo}\" group-title=\"${category.name}\",${channel.name}\n")
                } else {
                    sb.append("#EXTINF:-1 tvg-id=\"${channel.pid}\" tvg-name=\"${channel.name}\" group-title=\"${category.name}\",${channel.name}\n")
                }
                sb.append("$hostUrl/migu/${channel.pid}\n\n")
            }
        }
        
        return sb.toString()
    }
    
    /**
     * 生成 TXT 格式频道列表 (TVAuto 格式)
     */
    fun generateTxt(categories: List<MiguCategory>, hostUrl: String = "http://localhost:1234"): String {
        val sb = StringBuilder()
        
        for (category in categories) {
            for (channel in category.dataList) {
                sb.append("\"${channel.name}\",\"$hostUrl/migu/${channel.pid}\";\n")
            }
        }
        
        return sb.toString()
    }
    
    /**
     * 保存 M3U 到文件
     */
    fun saveM3u(categories: List<MiguCategory>, file: File, hostUrl: String = "http://localhost:1234"): Boolean {
        return try {
            FileWriter(file).use { writer ->
                writer.write(generateM3u(categories, hostUrl))
            }
            true
        } catch (e: Exception) {
            MiguApi.logger.severe("保存 M3U 失败: ${e.message}")
            false
        }
    }
    
    /**
     * 保存 TXT 到文件
     */
    fun saveTxt(categories: List<MiguCategory>, file: File, hostUrl: String = "http://localhost:1234"): Boolean {
        return try {
            FileWriter(file).use { writer ->
                writer.write(generateTxt(categories, hostUrl))
            }
            true
        } catch (e: Exception) {
            MiguApi.logger.severe("保存 TXT 失败: ${e.message}")
            false
        }
    }
}