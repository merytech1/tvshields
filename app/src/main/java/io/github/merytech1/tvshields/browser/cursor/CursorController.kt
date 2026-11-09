package io.github.merytech1.tvshields.browser.cursor

import kotlin.math.min

enum class Direction(val dx: Int, val dy: Int) {
    LEFT(-1, 0), RIGHT(1, 0), UP(0, -1), DOWN(0, 1),
}

/** How far the page must scroll because the cursor pushed against an edge, in pixels. */
data class Scroll(val dx: Float, val dy: Float) {
    val isZero get() = dx == 0f && dy == 0f

    companion object {
        val NONE = Scroll(0f, 0f)
    }
}

/**
 * Virtual cursor movement (architecture §6). Plain Kotlin, driven by key presses and frame ticks:
 * held arrows move the cursor, speeding up from [minSpeed] to [maxSpeed] over [accelMs];
 * pushing against an edge turns the movement into page scroll.
 */
class CursorController(
    private val minSpeed: Float = 450f, // px/s
    private val maxSpeed: Float = 1800f, // px/s
    private val accelMs: Float = 700f,
    /** Immediate move on each press, so short taps adjust the cursor precisely. */
    private val nudge: Float = 16f, // px
    /** Page scroll for a press against an edge. */
    private val edgeScroll: Float = 160f, // px
) {
    var x = 0f
        private set
    var y = 0f
        private set
    private var width = 0f
    private var height = 0f
    private var centered = false

    private val held = linkedSetOf<Direction>()
    private var heldMs = 0f

    val isMoving get() = held.isNotEmpty()

    /** Sets the area the cursor lives in. The first call centers the cursor. */
    fun setBounds(width: Float, height: Float) {
        this.width = width
        this.height = height
        if (!centered && width > 0f && height > 0f) {
            x = width / 2
            y = height / 2
            centered = true
        }
        x = x.coerceIn(0f, width)
        y = y.coerceIn(0f, height)
    }

    /** Starts moving; returns the scroll for a press against an edge. */
    fun press(direction: Direction): Scroll {
        if (held.isEmpty()) heldMs = 0f
        held += direction
        val wantedX = x + direction.dx * nudge
        val wantedY = y + direction.dy * nudge
        x = wantedX.coerceIn(0f, width)
        y = wantedY.coerceIn(0f, height)
        return Scroll(
            dx = if (wantedX != x) direction.dx * edgeScroll else 0f,
            dy = if (wantedY != y) direction.dy * edgeScroll else 0f,
        )
    }

    fun release(direction: Direction) {
        held -= direction
    }

    fun releaseAll() = held.clear()

    /** Advances [dtMs] milliseconds; returns the scroll caused by pushing against an edge. */
    fun step(dtMs: Float): Scroll {
        if (held.isEmpty() || dtMs <= 0f) return Scroll.NONE
        heldMs += dtMs
        val speed = minSpeed + (maxSpeed - minSpeed) * min(1f, heldMs / accelMs)
        val distance = speed * dtMs / 1000f
        val dirX = held.sumOf { it.dx }.coerceIn(-1, 1)
        val dirY = held.sumOf { it.dy }.coerceIn(-1, 1)

        val wantedX = x + dirX * distance
        val wantedY = y + dirY * distance
        x = wantedX.coerceIn(0f, width)
        y = wantedY.coerceIn(0f, height)
        return Scroll(wantedX - x, wantedY - y)
    }
}
