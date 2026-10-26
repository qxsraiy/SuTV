package top.yogiczy.mytv.tv.utlis

import android.os.SystemClock
import android.util.Log

/**
 * 启动耗时打点：从进程启动到直播首帧的关键节点。
 *
 * 用法：
 *   StartupTrace.start()            在 Application / MainActivity.onCreate 最开始调用一次
 *   StartupTrace.mark("xxx")        在关键节点调用
 *
 * 日志 TAG = Startup，形如：
 *   Startup: +142ms  activity.onCreate
 *   Startup: +980ms  channelList.ready (groups=11, channels=179)
 */
object StartupTrace {
    private const val TAG = "Startup"

    @Volatile
    private var origin = 0L

    @Volatile
    private var started = false

    /** 记录起点（只生效一次） */
    fun start() {
        if (started) return
        origin = SystemClock.elapsedRealtime()
        started = true
        Log.i(TAG, "+0ms  process.start")
    }

    /** 打点；未调用 start() 时自动初始化 */
    fun mark(name: String, detail: String? = null) {
        if (!started) start()
        val elapsed = SystemClock.elapsedRealtime() - origin
        val suffix = if (detail.isNullOrBlank()) "" else " ($detail)"
        Log.i(TAG, "+${elapsed}ms  $name$suffix")
    }
}
