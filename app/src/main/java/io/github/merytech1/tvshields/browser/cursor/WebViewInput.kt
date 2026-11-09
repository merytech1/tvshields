package io.github.merytech1.tvshields.browser.cursor

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.view.ViewConfigurationCompat

/** Synthetic input the virtual cursor sends to the WebView, in the view's own pixels. */
object WebViewInput {

    /** A tap: touch down and up at the same point. */
    fun tap(view: View, x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        touch(view, down, down, MotionEvent.ACTION_DOWN, x, y)
        touch(view, down, down + 50, MotionEvent.ACTION_UP, x, y)
    }

    /** Mouse hover, so pages show hover states and menus under the cursor. */
    fun hover(view: View, x: Float, y: Float) {
        pointerEvent(view, MotionEvent.ACTION_HOVER_MOVE, x, y, 0f, 0f)
    }

    /** Mouse-wheel scroll at the cursor: also scrolls inner scrollable elements, not just the page. */
    fun scroll(view: View, x: Float, y: Float, dx: Float, dy: Float) {
        val config = ViewConfiguration.get(view.context)
        val vFactor = ViewConfigurationCompat.getScaledVerticalScrollFactor(config, view.context)
        val hFactor = ViewConfigurationCompat.getScaledHorizontalScrollFactor(config, view.context)
        // AXIS_VSCROLL is positive for "wheel up", the opposite of a positive dy.
        pointerEvent(view, MotionEvent.ACTION_SCROLL, x, y, dx / hFactor, -dy / vFactor)
    }

    private fun touch(view: View, downTime: Long, time: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, time, action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        view.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun pointerEvent(view: View, action: Int, x: Float, y: Float, hScroll: Float, vScroll: Float) {
        val now = SystemClock.uptimeMillis()
        val properties = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
        val coords = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            setAxisValue(MotionEvent.AXIS_HSCROLL, hScroll)
            setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll)
        }
        val event = MotionEvent.obtain(
            now, now, action, 1, arrayOf(properties), arrayOf(coords),
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0,
        )
        view.dispatchGenericMotionEvent(event)
        event.recycle()
    }
}
