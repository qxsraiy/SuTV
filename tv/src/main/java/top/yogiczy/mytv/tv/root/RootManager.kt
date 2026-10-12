package top.yogiczy.mytv.tv.root

import android.util.Log
import top.yogiczy.mytv.core.data.utils.Globals
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Root 管理：检测 + 授权 + 长驻 su 会话
 *
 * - 长驻 su 会话：授权一次后，所有 root 命令通过同一个 su 进程的 stdin/stdout 交互，
 *   避免每次执行都重新弹出 Magisk/SuperSU 授权窗口。
 * - 所有 exec 必须在 IO 线程调用（由调用方负责 Dispatchers.IO），本类不创建线程。
 * - 内部使用 synchronized 串行化 su 进程读写，避免并发命令互相干扰。
 */
object RootManager {
    private const val TAG = "RootManager"

    enum class RootStatus {
        NOT_ROOTED,
        ROOTED_NOT_GRANTED,
        GRANTED,
    }

    data class RootResult(
        val output: String,
        val exitCode: Int,
    ) {
        val success: Boolean get() = exitCode == 0
        override fun toString(): String = output
    }

    private val SU_PATHS = listOf(
        "/system/xbin/su",
        "/system/bin/su",
        "/sbin/su",
        "/vendor/bin/su",
        "/su/bin/su",
    )

    private val ROOT_MANAGER_PACKAGES = listOf(
        "com.topjohnwu.magisk",
        "io.github.huskydg.magisk",
        "com.superuser",
        "eu.chainfire.supersu",
        "com.kingroot.kinguser",
        "com.kingo.root",
        "me.weishu.kernelsu",
        "io.github.huskydg.kernelsu",
    )

    private var suProcess: Process? = null
    private var suOut: DataOutputStream? = null
    private var suIn: BufferedReader? = null
    private val lock = Any()
    private val granted = AtomicBoolean(false)
    private val fallbackSu = AtomicBoolean(false)

    // ─── 检测（不触发授权弹窗） ───

    fun isRooted(): Boolean {
        for (path in SU_PATHS) {
            if (File(path).exists()) return true
        }
        val pathEnv = System.getenv("PATH") ?: ""
        for (dir in pathEnv.split(":")) {
            if (dir.isNotBlank() && File(dir, "su").exists()) return true
        }
        try {
            val pm = Globals.appContext.packageManager
            for (pkg in ROOT_MANAGER_PACKAGES) {
                try {
                    pm.getPackageInfo(pkg, 0)
                    return true
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
        return false
    }

    fun status(): RootStatus = when {
        !isRooted() -> RootStatus.NOT_ROOTED
        granted.get() -> RootStatus.GRANTED
        else -> RootStatus.ROOTED_NOT_GRANTED
    }

    private fun suBinary(): String? {
        for (path in SU_PATHS) {
            if (File(path).exists()) return path
        }
        val pathEnv = System.getenv("PATH") ?: ""
        for (dir in pathEnv.split(":")) {
            if (dir.isNotBlank() && File(dir, "su").exists()) {
                return "$dir/su"
            }
        }
        return null
    }

    val isGranted: Boolean get() = granted.get()

    // ─── 授权 ───

    fun requestRoot(): Boolean {
        synchronized(lock) {
            if (granted.get()) return true
            val su = suBinary() ?: return false

            // 先用 su -c id 一次性验证
            val verifyResult = execOneShot(su, "-c", "id")
            if (verifyResult == null || !verifyResult.contains("uid=0")) {
                return false
            }

            // 尝试建立长驻会话
            try {
                val proc = ProcessBuilder(su).redirectErrorStream(true).start()
                suOut = DataOutputStream(proc.outputStream)
                suIn = BufferedReader(InputStreamReader(proc.inputStream))
                suProcess = proc

                val testResult = execInSession("id")
                if (testResult != null && testResult.success && testResult.output.contains("uid=0")) {
                    granted.set(true)
                    fallbackSu.set(false)
                    Log.i(TAG, "长驻 su 会话已建立")
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "长驻会话失败，降级为 su -c: ${e.message}")
            }

            granted.set(true)
            fallbackSu.set(true)
            return true
        }
    }

    fun reGrant(): Boolean {
        releaseLocked()
        return requestRoot()
    }

    // ─── 执行命令 ───

    fun execRoot(cmd: String, timeoutMs: Long = 5000): RootResult? {
        synchronized(lock) {
            if (!granted.get()) return null
            if (fallbackSu.get()) return execOneShotResult(suBinary() ?: return null, "-c", cmd)
            return execInSession(cmd, timeoutMs)
        }
    }

    private fun execOneShot(su: String, vararg args: String): String? {
        return try {
            val proc = ProcessBuilder(su, *args).redirectErrorStream(true).start()
            val output = proc.inputStream.bufferedReader().readText()
            if (!waitForProcessCompat(proc, 5000)) {
                proc.destroy()
                return null
            }
            output
        } catch (e: Exception) {
            null
        }
    }

    private fun execOneShotResult(su: String, vararg args: String): RootResult? {
        val output = execOneShot(su, *args) ?: return null
        return RootResult(output.trim(), 0)
    }

    private fun execInSession(cmd: String, timeoutMs: Long = 5000): RootResult? {
        val proc = suProcess ?: return null
        val out = suOut ?: return null
        val inp = suIn ?: return null

        // 清空残留缓冲
        drainBuffer(inp)

        val marker = "__RC_${System.nanoTime()}_END__"
        try {
            out.writeBytes("$cmd\necho $marker:\$?\n")
            out.flush()
        } catch (e: Exception) {
            Log.w(TAG, "su 写入失败: ${e.message}")
            return null
        }

        val sb = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        try {
            while (System.currentTimeMillis() < deadline) {
                val line = inp.readLine() ?: break
                if (line.startsWith(marker)) {
                    val codeStr = line.substringAfter("$marker:")
                    val code = codeStr.trim().toIntOrNull() ?: -1
                    return RootResult(sb.toString().trim(), code)
                }
                if (sb.isNotEmpty()) sb.append("\n")
                sb.append(line)
            }
        } catch (e: Exception) {
            Log.w(TAG, "su 读取失败: ${e.message}")
        }
        return null
    }

    private fun drainBuffer(inp: BufferedReader) {
        var drained = 0
        try {
            while (inp.ready()) {
                val line = inp.readLine() ?: break
                drained++
                if (line.startsWith("__RC_")) break
            }
        } catch (_: Exception) {
        }
        if (drained > 0) Log.d(TAG, "清空了 $drained 行残留缓冲")
    }

    private fun waitForProcessCompat(proc: Process, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                return proc.exitValue() != Int.MIN_VALUE
            } catch (_: IllegalThreadStateException) {
                Thread.sleep(50)
            }
        }
        return false
    }

    // ─── 释放 ───

    fun release() {
        synchronized(lock) { releaseLocked() }
    }

    private fun releaseLocked() {
        granted.set(false)
        fallbackSu.set(false)
        runCatching { suOut?.writeBytes("exit\n"); suOut?.flush() }
        runCatching { suProcess?.destroy() }
        val proc = suProcess
        if (proc != null) runCatching { waitForProcessCompat(proc, 2000) }
        suProcess = null
        suOut = null
        suIn = null
    }
}
