package top.yogiczy.mytv.tv.ui.screen.agreement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.tv.ui.rememberChildPadding
import top.yogiczy.mytv.tv.ui.screen.components.AppScreen
import top.yogiczy.mytv.tv.ui.theme.MyTvTheme
import top.yogiczy.mytv.tv.ui.tooling.PreviewWithLayoutGrids
import top.yogiczy.mytv.tv.ui.utils.clickableNoIndication
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunched
import top.yogiczy.mytv.tv.ui.utils.gridColumns
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

@Composable
fun AgreementScreen(
    modifier: Modifier = Modifier,
    onAgree: () -> Unit = {},
    onDisagree: () -> Unit = {},
    onTouchTested: () -> Unit = {},
) {
    val childPadding = rememberChildPadding()

    val messages = listOf(
        "欢迎使用${Constants.APP_TITLE}，请在使用前仔细阅读以下内容：",

        "【一、软件性质】",
        "1. 本软件为开源播放器工具，仅提供播放能力与界面聚合。",
        "2. 本软件不提供、不制作、不存储、不分发、不托管任何音视频内容、直播信号或节目源。",

        "【二、内容来源】",
        "3. 本软件不自带任何可用的直播或点播内容。",
        "4. 所有直播源、点播源、节目单均来自第三方公开网络，或由用户自行添加与配置。",
        "5. 内置示例源仅用于演示“如何配置”，其可用性、合法性、安全性均与本软件作者无关。",
        "6. 本软件与咪咕视频、各 IPTV 运营商、各内容版权方无任何隶属、合作或授权关系。",

        "【三、使用限制】",
        "7. 仅限个人学习、研究与技术交流，禁止任何商业用途、经营活动或盈利目的。",
        "8. 不得利用本软件获取或传播违法、侵权、色情、暴力等违反当地法律法规的内容。",
        "9. 不得用于破解、绕过任何付费墙、数字版权管理（DRM）或访问控制措施。",
        "10. 不得用于侵犯他人知识产权、商业秘密或其他合法权益。",

        "【四、责任承担】",
        "11. 您应自行确认所在地区法律法规的允许范围，并自行承担全部使用后果。",
        "12. 因违反法律法规或本须知导致的任何损失、行政处罚、民事或刑事责任，均由您自行承担，与本软件作者无关。",

        "【五、无担保与责任限制】",
        "13. 本软件按“现状”（AS IS）提供，不附带任何明示或暗示的担保。",
        "14. 使用本软件可能导致的设备损坏、数据丢失、服务中断、账号封禁等风险，由您自行承担。",
        "15. 在法律允许的最大范围内，作者与贡献者对因使用或无法使用本软件产生的任何直接、间接、附带或后果性损害不承担责任。",

        "【六、版权投诉】",
        "16. 若您认为本软件中的示例配置侵犯了您的合法权益，请通过 GitHub Issues 联系，核实后将于合理时间内移除。",

        "【七、条款变更】",
        "17. 本须知可能随时更新，继续使用即视为接受更新后的内容。",

        "如您继续使用本软件，即代表您已完整阅读、理解并同意上述全部内容。若不同意，请立即退出并卸载本软件。",
    )

    AppScreen(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = childPadding.top),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("使用须知", style = MaterialTheme.typography.headlineMedium)

            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodyLarge
            ) {
                // 正文可滚动，按钮固定在底部，避免内容变长后按钮被挤出屏幕
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .width(8.gridColumns()),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(messages) { Text(it) }
                }

                val btnColors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = childPadding.bottom),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Button(
                        modifier = Modifier
                            .focusOnLaunched()
                            .handleKeyEvents(onSelect = onAgree)
                            .clickableNoIndication {
                                onTouchTested()
                                onAgree()
                            },
                        colors = btnColors,
                        onClick = { },
                    ) {
                        Text("已阅读并同意")
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        modifier = Modifier
                            .handleKeyEvents(onSelect = onDisagree)
                            .clickableNoIndication { onDisagree() },
                        colors = btnColors,
                        onClick = { },
                    ) {
                        Text("退出应用")
                    }
                }
            }
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun AgreementScreenPreview() {
    MyTvTheme {
        AgreementScreen()
        PreviewWithLayoutGrids { }
    }
}