package top.yogiczy.mytv.tv.ui.screen.loading

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.screen.main.MainUiState

@Composable
fun LoadingScreen(
    modifier: Modifier = Modifier,
    mainUiState: MainUiState,
    toDashboardScreen: () -> Unit = {},
    toSettingsScreen: () -> Unit = {},
    onBackPressed: () -> Unit = {},
) {
    var visible by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    // 渐显
    LaunchedEffect(Unit) {
        visible = true
    }

    // 加载完成 → 渐隐 → 跳转
    LaunchedEffect(mainUiState) {
        if (mainUiState is MainUiState.Ready) {
            leaving = true
            kotlinx.coroutines.delay(600)
            toDashboardScreen()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0D1117), Color(0xFF161B22), Color(0xFF0D1117))
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible && !leaving,
            enter = fadeIn(tween(800)),
            exit = fadeOut(tween(500)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // 呼吸动画的标题
                val breathe by rememberInfiniteTransition(label = "breathe").animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
                    label = "breatheAlpha",
                )
                Text(
                    "天光云影",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alpha(breathe),
                )
                Spacer(Modifier.height(24.dp))
                // 细线加载条
                val progress by rememberInfiniteTransition(label = "progress").animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
                    label = "progressBar",
                )
                Box(
                    modifier = Modifier
                        .size(width = 120.dp, height = 2.dp)
                        .background(Color(0xFF333333)),
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 120.dp * progress, height = 2.dp)
                            .background(Color(0xFF6E9CFF)),
                    )
                }
            }
        }

        // 错误状态
        if (mainUiState is MainUiState.Error) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(400)),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "加载失败",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 20.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        mainUiState.message ?: "",
                        color = Color(0xFF888888),
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}
