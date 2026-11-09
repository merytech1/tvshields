package io.github.merytech1.tvshields.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.KeyEvent
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import io.github.merytech1.tvshields.ui.theme.LocalTvShieldsColors

@Composable
fun BrowserScreen(viewModel: BrowserViewModel) {
    val state by viewModel.state.collectAsState()
    val colors = LocalTvShieldsColors.current
    val context = LocalContext.current
    val addressFocus = remember { FocusRequester() }
    val webView = remember {
        createWebView(context, viewModel, colors.background.toArgb(), onLeave = { addressFocus.requestFocus() })
    }

    DisposableEffect(webView) {
        onDispose { webView.destroy() }
    }
    LaunchedEffect(Unit) {
        addressFocus.requestFocus()
    }
    LaunchedEffect(webView) {
        viewModel.loads.collect { url ->
            webView.loadUrl(url)
            webView.requestFocus()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.background)) {
        AddressBar(
            currentUrl = state.currentUrl,
            progress = state.progress,
            focusRequester = addressFocus,
            onSubmit = viewModel::onAddressSubmit,
            // Overscan safe area: 48 dp sides, 27 dp top (design.md §5). Drawn after the WebView
            // (zIndex): on the reference TV the WebView paints its background outside its bounds.
            modifier = Modifier
                .zIndex(1f)
                .fillMaxWidth()
                .background(colors.background)
                .padding(start = 48.dp, end = 48.dp, top = 27.dp, bottom = 16.dp),
        )
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            if (state.currentUrl.isEmpty()) StartPage()
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createWebView(
    context: Context,
    viewModel: BrowserViewModel,
    backgroundColor: Int,
    onLeave: () -> Unit,
): WebView = WebView(context).apply {
    setBackgroundColor(backgroundColor)
    isFocusable = true
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true

    webViewClient = object : WebViewClient() {
        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = viewModel.onPageStarted(url)
        override fun onPageFinished(view: WebView, url: String) = viewModel.onPageFinished(url)
    }
    webChromeClient = object : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) = viewModel.onProgress(newProgress)
    }

    // Back = previous page, or back to the address bar; Menu = address bar (architecture §6).
    setOnKeyListener { _, keyCode, event ->
        if (keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_MENU) return@setOnKeyListener false
        if (event.action == KeyEvent.ACTION_UP) {
            if (keyCode == KeyEvent.KEYCODE_BACK && canGoBack()) goBack() else onLeave()
        }
        true
    }
}
