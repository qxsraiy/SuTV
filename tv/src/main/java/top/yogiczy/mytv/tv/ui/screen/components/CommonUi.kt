package top.yogiczy.mytv.tv.ui.screen.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * 全局文案统一入口：提示语、错误文案集中管理，避免各处硬编码导致风格不一致。
 */
object UiText {
    const val EMPTY_DATA = "暂无数据"
    const val EMPTY_FAVORITE = "暂无收藏"
    const val EMPTY_HISTORY = "暂无观看记录"
    const val EMPTY_SEARCH = "没有找到相关内容"
    const val LOAD_FAILED = "加载失败"

    const val SOURCE_BACKUP_FULL = "备用源已满 3 个，请先取消一个备用源"
    const val SOURCE_DEFAULT_CLEARED = "已取消默认源，播放时将使用内置咪咕"

    fun sourceDefaultExists(name: String) = "已有默认源「$name」，请先将它改为停用或备用"
    fun sourceSetDefault(name: String) = "已设为默认源：$name"
    fun sourceSetBackup(name: String) = "已设为备用源：$name"
    fun sourceUnsetBackup(name: String) = "已取消备用源：$name"
    fun latestVersion(version: String) = "当前已是最新版本 v$version"
    const val IS_LATEST = "当前已是最新版本"
    const val CHECKING_UPDATE = "正在检查更新..."
    fun foundNewVersion(version: String) = "发现新版本: v$version"
    const val PRESS_AGAIN_TO_EXIT = "再按一次退出"
}

/**
 * 统一空状态 / 错误占位：标题 + 可选提示，居中显示，颜色走主题。
 */
@Composable
fun EmptyState(
    modifier: Modifier = Modifier,
    title: String = UiText.EMPTY_DATA,
    hint: String? = null,
    fillMaxSize: Boolean = true,
) {
    Column(
        modifier = modifier
            .then(if (fillMaxSize) Modifier.fillMaxSize() else Modifier)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!hint.isNullOrBlank()) {
            Text(
                hint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** 统一圆角：与 App 其它组件保持一致 */
val AppCornerShape = RoundedCornerShape(8.dp)
