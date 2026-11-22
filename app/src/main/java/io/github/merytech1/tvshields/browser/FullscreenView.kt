package io.github.merytech1.tvshields.browser

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.Window
import android.webkit.WebChromeClient.CustomViewCallback
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.WindowInsetsControllerCompat

internal class FullscreenView(context: Context) : FrameLayout(context) {
    private var callback by mutableStateOf<CustomViewCallback?>(null)
    val isShowing get() = callback != null
    var keyHandler: ((KeyEvent) -> Boolean)? = null

    init {
        isFocusableInTouchMode = true
        isClickable = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        keyHandler?.invoke(event) == true || super.dispatchKeyEvent(event)

    fun show(view: View, callback: CustomViewCallback) {
        if (isShowing) {
            callback.onCustomViewHidden()
            return
        }
        addView(view, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        this.callback = callback
    }

    fun hide(): Boolean {
        if (!isShowing) return false
        callback = null
        removeAllViews()
        return true
    }

    fun dismiss(): Boolean {
        val callback = callback ?: return false
        hide()
        callback.onCustomViewHidden()
        return true
    }
}

@Composable
internal fun FullscreenSystemBars(window: Window) {
    DisposableEffect(window) {
        val decor = window.decorView
        val controller = WindowCompat.getInsetsController(window, decor)
        val insets = ViewCompat.getRootWindowInsets(decor)
        val bars = Type.statusBars() or Type.navigationBars()
        val visibleBars = listOf(Type.statusBars(), Type.navigationBars())
            .filter { insets?.isVisible(it) != false }
            .fold(0, Int::or)
        val behavior = controller.systemBarsBehavior
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(bars)
        onDispose {
            controller.systemBarsBehavior = behavior
            controller.hide(bars and visibleBars.inv())
            controller.show(visibleBars)
        }
    }
}
