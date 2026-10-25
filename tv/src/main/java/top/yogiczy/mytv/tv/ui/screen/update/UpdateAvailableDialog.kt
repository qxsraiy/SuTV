package top.yogiczy.mytv.tv.ui.screen.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.utils.clickableNoIndication
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

/**
 * 发现新版本弹窗（与 App 其余界面同一套 tv-material3 样式）
 *
 * 只提示版本号，不展示更新日志。
 * 确定 = 立即下载更新；取消 = 关闭。
 */
@Composable
fun UpdateAvailableDialog(
    latestVersion: String,
    currentVersion: String,
    downloading: Boolean = false,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val confirmFocus = remember { FocusRequester() }
    val btnColors = ButtonDefaults.colors(
        containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 36.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "发现新版本",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "最新版本 v$latestVersion",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "当前版本 v$currentVersion",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f),
            )
            Spacer(Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    modifier = Modifier
                        .focusRequester(confirmFocus)
                        .handleKeyEvents(onSelect = { if (!downloading) onConfirm() })
                        .clickableNoIndication { if (!downloading) onConfirm() },
                    colors = btnColors,
                    onClick = { },
                ) {
                    Text(if (downloading) "下载中..." else "确定")
                }

                Button(
                    modifier = Modifier
                        .handleKeyEvents(onSelect = { if (!downloading) onCancel() })
                        .clickableNoIndication { if (!downloading) onCancel() },
                    colors = btnColors,
                    onClick = { },
                ) {
                    Text("取消")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { confirmFocus.requestFocus() }
    }
}
