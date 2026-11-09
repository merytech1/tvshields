package io.github.merytech1.tvshields.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.merytech1.tvshields.ui.theme.LocalTvShieldsColors

/** Text button from design.md §9: primary is `accent` fill (one per screen), secondary `surface-raised`. */
@Composable
fun TextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val colors = LocalTvShieldsColors.current
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, tween(150), label = "focusScale")
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            // Focus is shown by the ring below, not by the default indication.
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .border(3.dp, if (focused) colors.accent else Color.Transparent, RoundedCornerShape(15.dp))
            .padding(5.dp)
            .background(if (primary) colors.accent else colors.surfaceRaised, RoundedCornerShape(10.dp))
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (primary) colors.onAccent else colors.textPrimary,
        )
    }
}
