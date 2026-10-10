package top.yogiczy.mytv.tv.ui.screensold.quickop.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

@Composable
fun QuickOpBtn(
    modifier: Modifier = Modifier,
    title: String,
    imageVector: ImageVector? = null,
    onSelect: () -> Unit = {},
    onLongSelect: () -> Unit = {},
) {
    var isFocused by remember { mutableStateOf(false) }

    val colorScheme = MaterialTheme.colorScheme
    val containerColor = remember(isFocused) {
        if (isFocused) colorScheme.inverseSurface
        else colorScheme.onSurface.copy(0.1f)
    }
    val contentColor = remember(isFocused) {
        if (isFocused) colorScheme.inverseOnSurface
        else colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .handleKeyEvents(onSelect = onSelect, onLongSelect = onLongSelect)
            .onFocusChanged { isFocused = it.hasFocus || it.isFocused }
            .focusable()
            .background(containerColor, MaterialTheme.shapes.medium),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor){
                imageVector?.let {
                    Icon(it, null, Modifier.size(20.dp))
                }

                Text(text = title, modifier = Modifier.animateContentSize())
            }
        }
    }
}
