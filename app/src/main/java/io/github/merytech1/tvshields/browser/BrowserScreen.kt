package io.github.merytech1.tvshields.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import io.github.merytech1.tvshields.browser.cursor.CursorController
import io.github.merytech1.tvshields.browser.cursor.CursorOverlay
import io.github.merytech1.tvshields.browser.cursor.Direction
import io.github.merytech1.tvshields.browser.cursor.Scroll
import io.github.merytech1.tvshields.browser.cursor.WebViewInput
import io.github.merytech1.tvshields.R
import io.github.merytech1.tvshields.settings.SettingsScreen
import io.github.merytech1.tvshields.settings.SettingsViewModel
import io.github.merytech1.tvshields.shields.CosmeticFilter
import io.github.merytech1.tvshields.shields.ShieldsManager
import io.github.merytech1.tvshields.ui.theme.LocalTvShieldsColors

@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel,
    shields: ShieldsManager,
    cosmetics: CosmeticFilter,
    engineReady: Boolean,
    settings: SettingsViewModel,
    window: Window,
) {
    val state by viewModel.state.collectAsState()
    val colors = LocalTvShieldsColors.current
    val context = LocalContext.current
    val addressFocus = remember { FocusRequester() }
    val cursor = remember { CursorController() }
    val okPress = remember { OkPress() }
    var cursorPosition by remember { mutableStateOf(Offset.Zero) }
    var moveRequests by remember { mutableIntStateOf(0) }
    var webViewFocused by remember { mutableStateOf(false) }
    val badgeFocus = remember { FocusRequester() }
    var panelOpen by remember { mutableStateOf(false) }
    val settingsFocus = remember { FocusRequester() }
    var settingsOpen by remember { mutableStateOf(false) }
    val shieldsStatus = ShieldsStatus.of(state.shieldsOffForSite, engineReady)

    val injector = remember(context) { CosmeticInjector(context, cosmetics) }
    val fullscreenView = remember { FullscreenView(context) }
    val webView = remember {
        createWebView(context, viewModel, shields, injector, fullscreenView, colors.background.toArgb())
    }

    fun inputView(): View = if (fullscreenView.isShowing) fullscreenView else webView

    fun updateCursorBounds(size: IntSize) {
        cursor.setViewport(size.width, size.height)
        cursorPosition = Offset(cursor.x, cursor.y)
    }

    fun exitFullscreen() {
        if (fullscreenView.dismiss()) webView.requestFocus()
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

    fun moveCursor(scroll: Scroll) {
        val target = inputView()
        cursorPosition = Offset(cursor.x, cursor.y)
        WebViewInput.hover(target, cursor.x, cursor.y)
        if (scroll.isZero) return
        if (target == webView && scroll.dy < 0 && !webView.canScrollVertically(-1)) leaveToAddressBar()
        else WebViewInput.scroll(target, cursor.x, cursor.y, scroll.dx, scroll.dy)
    }

    BackHandler(enabled = fullscreenView.isShowing, onBack = ::exitFullscreen)
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
        val handleRemoteKey: (KeyEvent) -> Boolean = { event ->
            handleKey(event.keyCode, event, cursor, okPress,
                onMove = { scroll ->
                    moveCursor(scroll)
                    moveRequests++
                },
                onClick = { WebViewInput.tap(inputView(), cursor.x, cursor.y) },
                onLeave = {
                    if (fullscreenView.isShowing) exitFullscreen() else leaveToAddressBar()
                },
                onBack = {
                    when {
                        fullscreenView.isShowing -> exitFullscreen()
                        webView.canGoBack() -> goBack()
                        else -> leaveToAddressBar()
                    }
                },
            )
        }
        webView.keyHandler = handleRemoteKey
        fullscreenView.keyHandler = handleRemoteKey
        onDispose {
            webView.keyHandler = null
            fullscreenView.keyHandler = null
            fullscreenView.dismiss()
            injector.dispose(webView)
            webView.destroy()
        }
    }
    LaunchedEffect(Unit) {
        addressFocus.requestFocus()
    }
    LaunchedEffect(fullscreenView.isShowing) {
        cursor.releaseAll()
        val target = inputView()
        if (target.isLaidOut) updateCursorBounds(IntSize(target.width, target.height))
    }
    // Light or dark mode changed in Settings: blank and loading pages follow it.
    LaunchedEffect(colors.background) {
        webView.setBackgroundColor(colors.background.toArgb())
    }
    LaunchedEffect(webView) {
        viewModel.loads.collect { url ->
            if (url != HOME_URL) injector.prepareInBackground(webView, url)
            webView.loadUrl(url)
            // The start page has nothing to point at: focus goes to the address bar.
            if (url == HOME_URL) addressFocus.requestFocus() else webView.requestFocus()
        }
    }
    // Shields were turned off or on for this site: register the page's scripts again and reload.
    LaunchedEffect(webView) {
        viewModel.reloads.collect {
            injector.invalidate()
            webView.url?.let { injector.prepare(webView, it) }
            webView.reload()
        }
    }
    // Moves the cursor once per frame while an arrow is held; each new press restarts the loop.
    LaunchedEffect(moveRequests) {
        var last = withFrameNanos { it }
        while (cursor.isMoving) {
            val now = withFrameNanos { it }
            val scroll = cursor.step((now - last) / 1_000_000f)
            last = now
            moveCursor(scroll)
        }
    }

    Box(Modifier.fillMaxSize()) {
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
                shieldsStatus = shieldsStatus,
                panelOpen = panelOpen,
                badgeFocus = badgeFocus,
                onShields = { panelOpen = !panelOpen },
                settingsFocus = settingsFocus,
                onSettings = {
                    panelOpen = false
                    settingsOpen = true
                },
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
                        if (!fullscreenView.isShowing) updateCursorBounds(it)
                    },
            ) {
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
                if (state.currentUrl.isEmpty()) StartPage()
                // Below the top bar, at its right edge, so the badge stays visible (design.md §14).
                if (panelOpen && state.currentUrl.isNotEmpty()) {
                    ShieldsPanel(
                        site = displaySite(state.currentUrl),
                        shields = state.shields,
                        status = shieldsStatus,
                        onToggle = viewModel::onSiteShieldsToggled,
                        onClose = {
                            panelOpen = false
                            badgeFocus.requestFocus()
                        },
                        onFocusLost = { panelOpen = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 48.dp)
                            .fillMaxWidth(PANEL_WIDTH),
                    )
                }
                if (webViewFocused && state.currentUrl.isNotEmpty()) {
                    CursorOverlay(position = cursorPosition, modifier = Modifier.fillMaxSize())
                }
            }
        }
        // Over the whole browser, which stays composed: the page keeps its state (design.md §14).
        if (settingsOpen) {
            val settingsState by settings.state.collectAsState()
            SettingsScreen(
                state = settingsState,
                onUiMode = settings::onUiMode,
                onAccent = settings::onAccent,
                onListToggled = settings::onListToggled,
                onUpdateLists = settings::onUpdateLists,
                onSearchEngine = settings::onSearchEngine,
                onClose = {
                    settingsOpen = false
                    settingsFocus.requestFocus()
                },
                modifier = Modifier.fillMaxSize().zIndex(2f),
            )
        }
        if (fullscreenView.isShowing) {
            FullscreenSystemBars(window)
            Box(Modifier.fillMaxSize().zIndex(3f).background(Color.Black)) {
                AndroidView(
                    factory = { fullscreenView },
                    modifier = Modifier.fillMaxSize().onSizeChanged(::updateCursorBounds),
                )
                CursorOverlay(position = cursorPosition, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

/** Shields panel width as a fraction of the screen (design.md mockups: 36 % of the frame). */
private const val PANEL_WIDTH = 0.36f

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
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBack()
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
    fullscreenView: FullscreenView,
    backgroundColor: Int,
): TvWebView = TvWebView(context).apply {
    // Debug builds only: lets chrome://inspect (or the DevTools protocol over adb) check what
    // Shields did inside a page, e.g. that a redirect resource ran.
    if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
        WebView.setWebContentsDebuggingEnabled(true)
    }
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

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            fullscreenView.show(view, callback)
        }

        override fun onHideCustomView() {
            if (fullscreenView.hide()) this@apply.requestFocus()
        }
    }
}
