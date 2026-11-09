package io.github.merytech1.tvshields.browser

import android.content.Context
import android.view.KeyEvent
import android.webkit.WebView

/**
 * WebView that offers remote keys to [keyHandler] first. An OnKeyListener is not enough:
 * WebView handles arrows itself (scrolling, focus) before listeners see them.
 */
class TvWebView(context: Context) : WebView(context) {

    var keyHandler: ((KeyEvent) -> Boolean)? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        keyHandler?.invoke(event) == true || super.dispatchKeyEvent(event)
}
