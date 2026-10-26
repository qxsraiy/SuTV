package top.yogiczy.mytv.tv.ui.screen.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.theme.colors
import top.yogiczy.mytv.tv.ui.utils.clickableNoIndication
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

/**
 * 统一按钮/卡片：聚焦态背景反色（与 App 自带 Card 一致），
 * 触控（clickableNoIndication）与遥控（handleKeyEvents）都能触发。
 *
 * 全工程替换各处手写的 `primary.copy(alpha = …)` 聚焦样式。
 */
@Composable
fun AppButton(
    modifier: Modifier = Modifier,
    text: String,
    onClick: () -> Unit = {},
    imageVector: ImageVector? = null,
    enabled: Boolean = true,
    fillMaxWidth: Boolean = true,
    compact: Boolean = false,
    contentDescription: String? = null,
) {
    Card(
        onClick = { },
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .handleKeyEvents(onSelect = if (enabled) onClick else null)
            .then(if (enabled) Modifier.clickableNoIndication { onClick() } else Modifier),
        colors = CardDefaults.colors(
            containerColor = MaterialTheme.colors.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = CardDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            ),
        ),
    ) {
        Row(
            modifier = Modifier
                .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
                .padding(
                    horizontal = if (compact) 14.dp else 18.dp,
                    vertical = if (compact) 7.dp else 11.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (imageVector != null) {
                Icon(
                    imageVector,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(if (compact) 18.dp else 22.dp),
                    tint = LocalContentColor.current,
                )
            }
            Text(
                text,
                style = if (compact) MaterialTheme.typography.bodyMedium
                else MaterialTheme.typography.bodyLarge,
                color = LocalContentColor.current,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 统一圆角（与 AppButton 一致） */
val AppShape = RoundedCornerShape(8.dp)
