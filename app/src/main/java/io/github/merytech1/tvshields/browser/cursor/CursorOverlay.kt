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

/** Pointer height. The cursor stays this far from the right and bottom edges so it is always visible. */
val CursorSize = 24.dp

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
        val unit = 1.2f * density // 20 units = 24 dp
        translate(position.x, position.y) {
            scale(unit, unit, pivot = Offset.Zero) {
                drawPath(arrow, outline, style = Stroke(width = 4f * density / unit, join = StrokeJoin.Round))
                drawPath(arrow, Color.White)
            }
        }
    }
}
