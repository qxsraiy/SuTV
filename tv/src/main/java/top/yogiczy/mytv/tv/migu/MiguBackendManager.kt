package top.yogiczy.mytv.tv.migu

import android.content.Context
import android.os.FileUtils
import java.io.File

/**
 * 咪咕后端管理器
 * 管理频道数据、M3U 生成、播放地址获取
 */
class MiguBackendManager(private val context: Context) {
    
    companion object {
        private const val TAG = "MiguBackendManager"
        private var instance: MiguBackendManager? = null
        
        fun getInstance(context: Context): MiguBackendManager {
            return instance ?: MiguBackendManager(context.applicationContext).also { instance = it }
        }
    }
    
    private val cacheDir: File = File(context.cacheDir, "migu")
    private val dataDir: File = File(context.filesDir, "migu")
    
    init {
        cacheDir.mkdirs()
        dataDir.mkdirs()
    }
    
    /**
     * 加载所有频道数据
     */
    suspend fun loadAllChannels(): List<MiguCategory> {
        val categories = MiguApi.fetchCategories()
        
        for (category in categories) {
            if (category.vomsID.isNotEmpty()) {
                val channels = MiguApi.fetchChannels(category.vomsID)
                category.dataList.clear()
                category.dataList.addAll(channels)
            }
        }
        
        return categories
    }
    
    /**
     * 获取频道播放地址
     */
    suspend fun getChannelStreamUrl(pid: String): MiguStreamResult {
        return MiguApi.getStreamUrl720p(pid)
    }
    
    /**
     * 保存 M3U 文件
     */
    suspend fun saveM3uFile(categories: List<MiguCategory>, hostUrl: String = "http://localhost:1234"): File? {
        val m3uFile = File(dataDir, "migu.m3u")
        if (MiguM3uGenerator.saveM3u(categories, m3uFile, hostUrl)) {
            return m3uFile
        }
        return null
    }
    
    /**
     * 保存 TXT 文件 (TVAuto 格式)
     */
    suspend fun saveTxtFile(categories: List<MiguCategory>, hostUrl: String = "http://localhost:1234"): File? {
        val txtFile = File(dataDir, "migu.txt")
        if (MiguM3uGenerator.saveTxt(categories, txtFile, hostUrl)) {
            return txtFile
        }
        return null
    }
    
    /**
     * 清理缓存
     */
    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }
}