package io.github.merytech1.tvshields.browser

import android.annotation.SuppressLint
import android.content.Context
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import io.github.merytech1.tvshields.browser.cursor.CursorController
import io.github.merytech1.tvshields.browser.cursor.CursorOverlay
import io.github.merytech1.tvshields.browser.cursor.CursorSize
import io.github.merytech1.tvshields.browser.cursor.Direction
import io.github.merytech1.tvshields.browser.cursor.Scroll
import io.github.merytech1.tvshields.browser.cursor.WebViewInput
import io.github.merytech1.tvshields.R
import io.github.merytech1.tvshields.shields.CosmeticFilter
import io.github.merytech1.tvshields.shields.ShieldsManager
import io.github.merytech1.tvshields.ui.theme.LocalTvShieldsColors

@Composable
fun BrowserScreen(viewModel: BrowserViewModel, shields: ShieldsManager, cosmetics: CosmeticFilter) {
    val state by viewModel.state.collectAsState()
    val colors = LocalTvShieldsColors.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val addressFocus = remember { FocusRequester() }
    val cursor = remember { CursorController() }
    val okPress = remember { OkPress() }
    var cursorPosition by remember { mutableStateOf(Offset.Zero) }
    var moveRequests by remember { mutableIntStateOf(0) }
    var webViewFocused by remember { mutableStateOf(false) }

    val injector = remember(context) { CosmeticInjector(context, cosmetics) }
    val webView = remember {
        createWebView(context, viewModel, shields, injector, colors.background.toArgb())
    }

    fun leaveToAddressBar() {
        cursor.releaseAll()
        addressFocus.requestFocus()
    }

    // The cosmetic script must be registered before the target document begins loading.
    fun goBack() {
        val history = webView.copyBackForwardList()
        history.getItemAtIndex(history.currentIndex - 1)?.url?.let { injector.prepare(webView, it) }
        webView.goBack()
    }

    // Pushing up at the top of the page goes back to the address bar.
    fun scrollOrLeave(scroll: Scroll) {
        if (scroll.isZero) return
        if (scroll.dy < 0 && !webView.canScrollVertically(-1)) leaveToAddressBar()
        else WebViewInput.scroll(webView, cursor.x, cursor.y, scroll.dx, scroll.dy)
    }

    DisposableEffect(webView) {
        webView.setOnFocusChangeListener { _, focused ->
            webViewFocused = focused
            if (!focused) {
                cursor.releaseAll()
                // A keyboard opened by a page field would otherwise stay over the top bar.
                context.getSystemService(InputMethodManager::class.java)
                    .hideSoftInputFromWindow(webView.windowToken, 0)
            }
        }
        webView.keyHandler = { event ->
            handleKey(event.keyCode, event, cursor, webView, okPress,
                onMove = { scroll ->
                    // Show the press nudge right away; the frame loop handles held keys.
                    cursorPosition = Offset(cursor.x, cursor.y)
                    WebViewInput.hover(webView, cursor.x, cursor.y)
                    scrollOrLeave(scroll)
                    moveRequests++
                },
                onClick = { WebViewInput.tap(webView, cursor.x, cursor.y) },
                onLeave = ::leaveToAddressBar,
                onBack = ::goBack,
            )
        }
        onDispose {
            webView.keyHandler = null
            injector.dispose(webView)
            webView.destroy()
        }
    }
    LaunchedEffect(Unit) {
        addressFocus.requestFocus()
    }
    LaunchedEffect(webView) {
        viewModel.loads.collect { url ->
            if (url != HOME_URL) injector.prepareInBackground(webView, url)
            webView.loadUrl(url)
            // The start page has nothing to point at: focus goes to the address bar.
            if (url == HOME_URL) addressFocus.requestFocus() else webView.requestFocus()
        }
    }
    // Moves the cursor once per frame while an arrow is held; each new press restarts the loop.
    LaunchedEffect(moveRequests) {
        var last = withFrameNanos { it }
        while (cursor.isMoving) {
            val now = withFrameNanos { it }
            val scroll = cursor.step((now - last) / 1_000_000f)
            last = now
            cursorPosition = Offset(cursor.x, cursor.y)
            WebViewInput.hover(webView, cursor.x, cursor.y)
            scrollOrLeave(scroll)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        TopBar(
            state = state,
            addressFocus = addressFocus,
            onSubmit = viewModel::onAddressSubmit,
            onBack = { if (webView.canGoBack()) goBack() },
            onReload = {
                webView.url?.let { injector.prepare(webView, it) }
                webView.reload()
            },
            onHome = viewModel::onHome,
            // Overscan safe area: 48 dp sides, 27 dp top (design.md §5). Drawn after the WebView
            // (zIndex): on the reference TV the WebView paints its background outside its bounds.
            modifier = Modifier
                .zIndex(1f)
                // Down goes from the bar back to the page. Up is swallowed: nothing is above the
                // bar, and the text field would open the keyboard (e.g. a held Up that left the page).
                .onPreviewKeyEvent {
                    when (it.key) {
                        Key.DirectionUp -> true
                        // Back still means the previous page; with no history it leaves the app.
                        Key.Back -> state.canGoBack.also { back ->
                            if (back && it.type == KeyEventType.KeyUp) webView.goBack()
                        }
                        Key.DirectionDown -> it.type == KeyEventType.KeyDown &&
                            state.currentUrl.isNotEmpty() && webView.requestFocus()
                        else -> false
                    }
                }
                .fillMaxWidth()
                .background(colors.background)
                .padding(start = 48.dp, end = 48.dp, top = 27.dp, bottom = 16.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged {
                    val margin = with(density) { CursorSize.toPx() }
                    cursor.setBounds(it.width - margin, it.height - margin)
                    cursorPosition = Offset(cursor.x, cursor.y)
                },
        ) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            if (state.currentUrl.isEmpty()) StartPage()
            if (webViewFocused && state.currentUrl.isNotEmpty()) {
                CursorOverlay(position = cursorPosition, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** Whether the OK key currently held has become a long press. */
private class OkPress {
    var long = false
}

private fun directionOf(keyCode: Int): Direction? = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_UP -> Direction.UP
    KeyEvent.KEYCODE_DPAD_DOWN -> Direction.DOWN
    KeyEvent.KEYCODE_DPAD_LEFT -> Direction.LEFT
    KeyEvent.KEYCODE_DPAD_RIGHT -> Direction.RIGHT
    else -> null
}

/**
 * Remote keys on the WebView (architecture §6): arrows move the cursor, OK taps under it,
 * long-press OK or Menu goes to the address bar, Back goes to the previous page.
 */
private fun handleKey(
    keyCode: Int,
    event: KeyEvent,
    cursor: CursorController,
    webView: WebView,
    okPress: OkPress,
    onMove: (Scroll) -> Unit,
    onClick: () -> Unit,
    onLeave: () -> Unit,
    onBack: () -> Unit,
): Boolean {
    val direction = directionOf(keyCode)
    if (direction != null) {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) onMove(cursor.press(direction))
            KeyEvent.ACTION_UP -> cursor.release(direction)
        }
        return true
    }
    return when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
            when (event.action) {
                // Remotes repeat a held key; a repeat (or the long-press flag) marks a long press.
                KeyEvent.ACTION_DOWN -> okPress.long = event.repeatCount > 0 || event.isLongPress
                KeyEvent.ACTION_UP -> {
                    val held = event.eventTime - event.downTime
                    if (okPress.long || held >= ViewConfiguration.getLongPressTimeout()) onLeave() else onClick()
                    okPress.long = false
                }
            }
            true
        }
        KeyEvent.KEYCODE_BACK -> {
            if (event.action == KeyEvent.ACTION_UP) {
                if (webView.canGoBack()) onBack() else onLeave()
            }
            true
        }
        KeyEvent.KEYCODE_MENU -> {
            if (event.action == KeyEvent.ACTION_UP) onLeave()
            true
        }
        else -> false
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: Context,
    viewModel: BrowserViewModel,
    shields: ShieldsManager,
    cosmetics: CosmeticInjector,
    backgroundColor: Int,
): TvWebView = TvWebView(context).apply {
    setBackgroundColor(backgroundColor)
    isFocusable = true
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true

    webViewClient = ShieldsWebViewClient(
        shields,
        cosmetics,
        viewModel,
        context.getString(R.string.shields_blocked_page),
    )
    // Generic filtering needs the JS channel before the first document loads.
    cosmetics.attach(this)
    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) = viewModel.onProgress(newProgress)
    }
}
