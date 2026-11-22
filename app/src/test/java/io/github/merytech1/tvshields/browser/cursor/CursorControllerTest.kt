package io.github.merytech1.tvshields.browser.cursor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorControllerTest {

    private fun controller(nudge: Float = 0f) =
        CursorController(minSpeed = 500f, maxSpeed = 1500f, accelMs = 1000f, nudge = nudge)
            .apply { setViewport(1000, 600) }

    @Test
    fun `each press nudges the cursor`() {
        val c = controller(nudge = 16f)
        c.press(Direction.RIGHT)
        c.release(Direction.RIGHT)
        c.press(Direction.UP)
        assertEquals(516f, c.x, 0f)
        assertEquals(284f, c.y, 0f)
    }

    @Test
    fun `starts centered`() {
        val c = controller()
        assertEquals(500f, c.x, 0f)
        assertEquals(300f, c.y, 0f)
    }

    @Test
    fun `does not move without a held key`() {
        val c = controller()
        assertTrue(c.step(100f).isZero)
        assertEquals(500f, c.x, 0f)
    }

    @Test
    fun `moves at min speed at first, faster while held`() {
        val c = controller()
        c.press(Direction.RIGHT)
        c.step(100f) // speed after 100 ms: 500 + 1000 * 0.1 = 600 px/s -> 60 px
        assertEquals(560f, c.x, 0.01f)
        c.step(900f) // reaches max speed: 1500 px/s -> but clamped at the right edge
        assertEquals(999f, c.x, 0f)
    }

    @Test
    fun `pushing against an edge scrolls instead`() {
        val c = controller()
        c.press(Direction.DOWN)
        c.step(1000f) // wants 300 + 1500 = 1800, can only reach 600
        assertEquals(599f, c.y, 0f)
        val scroll = c.step(100f) // 1500 px/s * 0.1 s = 150 px, all of it scroll
        assertEquals(0f, scroll.dx, 0f)
        assertEquals(150f, scroll.dy, 0.01f)
    }

    @Test
    fun `diagonal and release`() {
        val c = controller()
        c.press(Direction.UP)
        c.press(Direction.LEFT)
        c.step(100f)
        assertEquals(440f, c.x, 0.01f)
        assertEquals(240f, c.y, 0.01f)
        c.release(Direction.UP)
        c.release(Direction.LEFT)
        assertFalse(c.isMoving)
    }

    @Test
    fun `acceleration restarts on a new press`() {
        val c = controller()
        c.press(Direction.RIGHT)
        c.step(1000f)
        c.release(Direction.RIGHT)
        c.press(Direction.LEFT)
        val before = c.x
        c.step(100f)
        assertEquals(before - 60f, c.x, 0.01f)
    }

    @Test
    fun `a press against an edge scrolls`() {
        val c = controller(nudge = 16f)
        c.setViewport(1000, 300)
        assertTrue(c.press(Direction.UP).isZero)
        c.release(Direction.UP)
        c.press(Direction.DOWN)
        c.release(Direction.DOWN)
        val scroll = c.press(Direction.DOWN)
        assertEquals(160f, scroll.dy, 0f)
        assertEquals(0f, scroll.dx, 0f)
    }

    @Test
    fun `smaller bounds pull the cursor inside`() {
        val c = controller()
        c.setViewport(200, 100)
        assertEquals(199f, c.x, 0f)
        assertEquals(99f, c.y, 0f)
    }

    @Test
    fun `held arrows reach the last clickable pixel in the bottom right corner`() {
        val c = controller()
        c.press(Direction.RIGHT)
        c.press(Direction.DOWN)
        c.step(1000f)
        assertEquals(999f, c.x, 0f)
        assertEquals(599f, c.y, 0f)
    }
}
