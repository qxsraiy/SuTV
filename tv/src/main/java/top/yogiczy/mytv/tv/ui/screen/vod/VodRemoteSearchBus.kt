package top.yogiczy.mytv.tv.ui.screen.vod

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 手机扫码搜索 - 跨组件总线。
 *
 * 语义：
 *  - 只在「点播主界面」处于前台时激活（active = true）。
 *    进入剧集详情、退出点播页时置为 false，此时手机端提交会被拒绝（断开）。
 *  - 手机网页提交的关键词放在 [pending]，点播页观察后立即执行搜索并 [consume]。
 */
object VodRemoteSearchBus {
    @Volatile
    var active: Boolean = false
        private set

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /** 点播主界面进入/离开时调用 */
    fun setActive(value: Boolean) {
        active = value
        if (!value) {
            // 断开时丢弃未消费的请求
            _pending.value = null
        }
    }

    /**
     * 手机端提交关键词。
     * @return true 表示电视端当前在点播页，已受理；false 表示已断开
     */
    fun submit(keyword: String): Boolean {
        val q = keyword.trim()
        if (!active || q.isEmpty()) return false
        _pending.value = q
        return true
    }

    /** 电视端消费掉待搜索关键词 */
    fun consume() {
        _pending.value = null
    }
}
