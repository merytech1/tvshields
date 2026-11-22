package io.github.merytech1.tvshields.browser.cursor

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp

private val outline = Color(0xFF0F1217)

private val arrowHeight = 24.dp
private val arrowWidth = 15.6.dp
private val outlineHalfWidth = 2.dp

/** Arrow pointer, 20 units tall, tip at (0, 0). */
private val arrow = Path().apply {
    moveTo(0f, 0f)
    lineTo(0f, 17f)
    lineTo(4.5f, 13f)
    lineTo(7.5f, 20f)
    lineTo(10.5f, 18.7f)
    lineTo(7.5f, 12f)
    lineTo(13f, 12f)
    close()
}

/** The virtual cursor: a white arrow with a 2 dp dark outline, readable on any page (design.md §3.4). */
@Composable
fun CursorOverlay(position: Offset, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val unit = arrowHeight.toPx() / 20f
        // Volteamos la flecha hacia dentro sin desplazar el punto donde pulsa.
        val scaleX = if (position.x + (arrowWidth + outlineHalfWidth).toPx() > size.width) -unit else unit
        val scaleY = if (position.y + (arrowHeight + outlineHalfWidth).toPx() > size.height) -unit else unit
        translate(position.x, position.y) {
            scale(scaleX, scaleY, pivot = Offset.Zero) {
                drawPath(
                    arrow,
                    outline,
                    style = Stroke(width = outlineHalfWidth.toPx() * 2f / unit, join = StrokeJoin.Round),
                )
                drawPath(arrow, Color.White)
            }
        }
    }
}
