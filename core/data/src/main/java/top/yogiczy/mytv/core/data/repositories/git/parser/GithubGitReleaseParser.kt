package top.yogiczy.mytv.core.data.repositories.git.parser

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.yogiczy.mytv.core.data.entities.git.GitRelease
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.data.utils.Globals

/**
 * github发行版解析
 *
 * 一个 release 下可能挂了多个 APK（arm64-v8a / armeabi-v7a），
 * 这里按当前设备的首选 ABI 挑选对应的包，避免下错架构装不上。
 */
class GithubGitReleaseParser : GitReleaseParser {
    override fun isSupport(url: String): Boolean {
        return url.contains("github.com") || url.contains("api.github.com")
    }

    override suspend fun parse(data: String): GitRelease {
        val json = Globals.json.parseToJsonElement(data).jsonObject

        val version = json.getValue("tag_name").jsonPrimitive.content
            .removePrefix("v")
            .removePrefix("V")

        val assets = json["assets"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
        val preferredAbi = preferredAbi()
        val asset = assets.firstOrNull {
            it["name"]?.jsonPrimitive?.contentOrNull?.contains(preferredAbi, ignoreCase = true) == true
        } ?: assets.firstOrNull()

        val rawUrl = asset?.get("browser_download_url")?.jsonPrimitive?.contentOrNull ?: ""
        val downloadUrl = if (Constants.GITHUB_PROXY.isBlank()) rawUrl
        else Constants.GITHUB_PROXY + rawUrl

        return GitRelease(
            version = version,
            downloadUrl = downloadUrl,
            description = json["body"]?.jsonPrimitive?.contentOrNull ?: "",
        )
    }

    /** 当前设备优先使用的 ABI 关键字 */
    private fun preferredAbi(): String {
        val abis = runCatching { android.os.Build.SUPPORTED_ABIS?.toList() }.getOrNull() ?: emptyList()
        return if (abis.any { it == "arm64-v8a" }) "arm64-v8a" else "armeabi-v7a"
    }
}
