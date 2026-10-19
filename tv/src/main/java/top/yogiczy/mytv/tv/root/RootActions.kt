package top.yogiczy.mytv.tv.root

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import top.yogiczy.mytv.core.data.utils.Globals

/**
 * Root 动作：安装/移除系统应用 + OOM 防清理 + 锁定桌面。
 * 所有函数必须在 IO 线程调用。
 */
object RootActions {

    // ─── 安装为系统应用 ───

    fun isSystemApp(): Boolean {
        val context = Globals.appContext
        if (context.applicationInfo.sourceDir.startsWith("/system/")) return true
        if (!RootManager.isGranted) return false
        val check = RootManager.execRoot("ls -l /system/priv-app/SuTV/SuTV.apk 2>/dev/null")
        return check?.success == true
    }

    fun installAsSystemApp(): String {
        val context = Globals.appContext
        val apkPath = context.applicationInfo.sourceDir
        val privDir = "/system/priv-app/SuTV"
        val apkDest = "$privDir/SuTV.apk"

        if (apkPath == apkDest) {
            return "已安装为系统应用（当前运行于 $apkDest）"
        }
        val check = RootManager.execRoot("ls -l $apkDest 2>/dev/null")
        if (check != null && check.success) {
            return "已安装为系统应用，文件位置: $apkDest，重启后生效"
        }

        val steps = listOf(
            "mount -o remount,rw /system" to "重挂载 /system 失败",
            "mkdir -p $privDir" to "创建目录失败",
            "cp \"$apkPath\" $apkDest" to "复制 APK 失败",
            "chmod 644 $apkDest" to "设置权限失败",
            "chown root:root $apkDest" to "修改所有者失败",
            "sync" to "同步失败",
        )

        for ((cmd, errMsg) in steps) {
            val result = RootManager.execRoot(cmd)
            if (result == null || !result.success) {
                return "失败: $errMsg\n$cmd → ${result?.output ?: "超时"}"
            }
        }
        return "安装完成！APK 已复制到 $privDir，重启后生效，普通卸载入口将不可用"
    }

    fun removeFromSystem(): String {
        val privDir = "/system/priv-app/SuTV"
        val steps = listOf(
            "mount -o remount,rw /system" to "重挂载失败",
            "rm -rf $privDir" to "删除失败",
            "sync" to "同步失败",
        )
        for ((cmd, errMsg) in steps) {
            val result = RootManager.execRoot(cmd)
            if (result == null || !result.success) {
                return "失败: $errMsg\n$cmd → ${result?.output ?: "超时"}"
            }
        }
        return "已移除系统副本，重启后恢复为普通应用"
    }

    // ─── OOM 防清理 ───

    fun setOomPriority(enable: Boolean): String {
        val pid = Process.myPid()
        val adj = if (enable) "-17" else "0"
        val scoreAdj = if (enable) "-1000" else "0"

        val r1 = RootManager.execRoot("echo $adj > /proc/$pid/oom_adj")
        val r2 = RootManager.execRoot("echo $scoreAdj > /proc/$pid/oom_score_adj")

        val ok = (r1?.success == true) || (r2?.success == true)
        return if (ok) {
            if (enable) "进程守护已开启（oom_adj=-17）"
            else "进程守护已关闭"
        } else {
            "设置失败：${r1?.output ?: ""} ${r2?.output ?: ""}"
        }
    }

    fun checkOomPriority(): Boolean {
        val pid = Process.myPid()
        val result = RootManager.execRoot("cat /proc/$pid/oom_adj 2>/dev/null")
        return result?.output?.trim()?.toIntOrNull()?.let { it < 0 } ?: false
    }

    // ─── 锁定为桌面 ───

    fun isDefaultLauncher(): Boolean {
        val pm = Globals.appContext.packageManager
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == Globals.appContext.packageName
    }

    fun setAsDefaultLauncher(): String {
        val context = Globals.appContext
        val pm = context.packageManager
        val ourPkg = context.packageName

        if (isDefaultLauncher()) {
            return "已是默认桌面"
        }
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val candidates = pm.queryIntentActivities(homeIntent, 0)
        val isCandidate = candidates.any { it.activityInfo.packageName == ourPkg }
        if (!isCandidate) {
            return "本应用不在桌面候选列表中（AndroidManifest 未声明 HOME intent-filter）"
        }

        val others = candidates.filter { it.activityInfo.packageName != ourPkg }
        for (info in others) {
            val pkg = info.activityInfo.packageName
            RootManager.execRoot("pm disable-user --user 0 $pkg")
        }

        Thread.sleep(500)

        return if (isDefaultLauncher()) {
            "已锁定为桌面，按 HOME 键验证"
        } else {
            "已禁用其他桌面应用，请按 HOME 键设置为默认桌面"
        }
    }

    fun restoreDefaultLauncher(): String {
        val context = Globals.appContext
        val pm = context.packageManager
        val result = RootManager.execRoot("pm list packages -d 2>/dev/null")
        val disabled = result?.output?.lines()?.mapNotNull {
            it.removePrefix("package:").takeIf { s -> s.isNotBlank() }
        } ?: emptyList()

        var restored = 0
        for (pkg in disabled) {
            val r = RootManager.execRoot("pm enable $pkg")
            if (r?.success == true) restored++
        }
        return if (restored > 0) "已恢复 $restored 个被禁用的桌面应用"
        else "没有需要恢复的桌面应用"
    }
}
