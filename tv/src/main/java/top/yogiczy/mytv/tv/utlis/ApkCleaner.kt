package top.yogiczy.mytv.tv.utlis

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.io.File
import top.yogiczy.mytv.core.data.utils.Globals
import top.yogiczy.mytv.core.util.utils.compareVersion

/**
 * 本地 APK 清理。
 *
 * 更新检查/下载会在本地留下 apk（默认 cacheDir/latest.apk，也可能有历史文件）。
 * 若这些 apk 的版本 **等于或低于** 当前运行版本，则没有任何安装价值，只会堆积垃圾，直接删除。
 *
 * 安全约束：
 *  - 跳过最近 [PROTECT_WINDOW_MS] 内修改过的文件（可能正在下载或正在等待安装）
 *  - 无法解析版本号的 apk 只清理我们自己的固定文件名 latest.apk
 *  - 任何异常都不影响主流程
 */
object ApkCleaner {
    private const val TAG = "ApkCleaner"
    private const val PROTECT_WINDOW_MS = 90_000L
    private const val OWN_DOWNLOAD_NAME = "latest.apk"

    /**
     * 清理版本号 <= [currentVersion] 的本地 apk。
     * @return 删除的文件数
     */
    fun cleanObsoleteApks(
        context: Context,
        currentVersion: String,
    ): Int {
        var removed = 0
        val now = System.currentTimeMillis()

        val dirs = buildList {
            runCatching { Globals.cacheDir }.getOrNull()?.let { add(it) }
            context.cacheDir?.let { add(it) }
            context.filesDir?.let { add(it) }
            context.getExternalFilesDir(null)?.let { add(it) }
            context.externalCacheDir?.let { add(it) }
        }.distinctBy { it.absolutePath }

        dirs.forEach { dir ->
            val apks = runCatching {
                dir.listFiles { f -> f.isFile && f.name.endsWith(".apk", ignoreCase = true) }
            }.getOrNull() ?: return@forEach

            apks.forEach { file ->
                runCatching {
                    // 正在下载/刚下载完待安装的文件不动
                    if (now - file.lastModified() < PROTECT_WINDOW_MS) {
                        Log.d(TAG, "跳过（近期修改）: ${file.name}")
                        return@runCatching
                    }

                    val apkVersion = readApkVersionName(context, file)
                    val obsolete = if (apkVersion.isNullOrBlank()) {
                        // 解析不出：只处理我们自己的下载文件名
                        file.name.equals(OWN_DOWNLOAD_NAME, ignoreCase = true)
                    } else {
                        // <= 当前版本 → 删除
                        apkVersion.compareVersion(currentVersion) <= 0
                    }

                    if (obsolete) {
                        if (file.delete()) {
                            removed++
                            Log.i(TAG, "已删除过期 apk: ${file.name} (v${apkVersion ?: "?"} <= v$currentVersion)")
                        } else {
                            Log.w(TAG, "删除失败: ${file.absolutePath}")
                        }
                    } else {
                        Log.d(TAG, "保留较新 apk: ${file.name} (v$apkVersion > v$currentVersion)")
                    }
                }.onFailure {
                    Log.w(TAG, "清理 ${file.name} 出错: ${it.message}")
                }
            }
        }

        Log.i(TAG, "清理完成，删除 $removed 个过期 apk")
        return removed
    }

    private fun readApkVersionName(context: Context, file: File): String? = runCatching {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(
            file.absolutePath,
            PackageManager.GET_META_DATA,
        )
        info?.versionName
    }.getOrNull()
}
