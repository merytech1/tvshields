package io.github.merytech1.tvshields.browser

import android.graphics.Bitmap
import android.text.TextUtils
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.merytech1.tvshields.engine.NetworkDecision
import io.github.merytech1.tvshields.shields.RedirectResource
import io.github.merytech1.tvshields.shields.ShieldsManager
import java.io.ByteArrayInputStream

/**
 * Sends every request through Shields (architecture §5.1) and reports page events to the
 * ViewModel. [shouldInterceptRequest] runs on WebView background threads.
 *
 * @param blockedPageText shown instead of a blocked page; `%s` is the page URL.
 */
class ShieldsWebViewClient(
    private val shields: ShieldsManager,
    private val cosmetics: CosmeticInjector,
    private val viewModel: BrowserViewModel,
    private val blockedPageText: String,
) : WebViewClient() {

    /** The main document's URL: the source of every other request (iframes included). */
    @Volatile
    private var pageUrl = ""

    /** `adb shell setprop log.tag.tvshields.block DEBUG` logs every blocked or redirected request (read at start). */
    private val logBlocked = Log.isLoggable(TAG, Log.DEBUG)

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val url = request.url.toString()
        val mainFrame = request.isForMainFrame
        if (mainFrame) {
            pageUrl = url
            shields.perf.pageStarted()
        }
        val accept = request.requestHeaders.entries.firstOrNull { it.key.equals("Accept", ignoreCase = true) }?.value

        val decision = shields.check(url, pageUrl, mainFrame, accept, request.method)
        // The main-frame request is the last point before the document starts loading.
        if (mainFrame && (decision == NetworkDecision.Allow || decision is NetworkDecision.Rewrite)) {
            cosmetics.prepare(view, url)
        }

        return when (decision) {
            NetworkDecision.Allow, is NetworkDecision.Rewrite -> null
            NetworkDecision.Block -> {
                if (logBlocked) Log.d(TAG, "Blocked $url")
                if (mainFrame) blockedPage(url) else emptyResponse()
            }
            // A page is never replaced by a resource; a subresource gets the harmless stand-in.
            is NetworkDecision.Redirect -> {
                val resource = if (mainFrame) null else RedirectResource.fromDataUrl(decision.dataUrl)
                if (logBlocked) Log.d(TAG, "${if (resource != null) "Redirected (${resource.mimeType})" else "Blocked"} $url")
                when {
                    mainFrame -> blockedPage(url)
                    resource != null -> redirectResponse(resource)
                    else -> emptyResponse()
                }
            }
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        // Runs on the UI thread before the new document begins loading: the right moment to
        // register the document-start script for link clicks.
        if (request.isForMainFrame) cosmetics.prepare(view, request.url.toString())
        return false
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        pageUrl = url
        cosmetics.injectFallback(view, url)
        viewModel.onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        shields.perf.pageFinished(url)
        viewModel.onPageFinished(url)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        // Also catches in-page navigation (history.pushState), which starts no new page.
        pageUrl = url
        viewModel.onHistoryChanged(view.canGoBack())
    }

    private fun blockedPage(url: String): WebResourceResponse {
        val text = TextUtils.htmlEncode(blockedPageText.format(url))
        val html = "<!doctype html><meta name=viewport content='width=device-width'>" +
            "<body style='font-family:sans-serif;padding:48px'><p>$text</p>"
        return WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(html.toByteArray()))
    }

    private companion object {
        const val TAG = "tvshields.block"

        /**
         * The resource stands in for a script or file the page may load cross-origin (with
         * `crossorigin` or through `fetch`), so it must pass the CORS check.
         */
        fun redirectResponse(resource: RedirectResource) = WebResourceResponse(
            resource.mimeType, resource.encoding, 200, "OK",
            mapOf("Access-Control-Allow-Origin" to "*"), ByteArrayInputStream(resource.bytes),
        )

        /** What a blocked resource receives: an empty "204 No Content". */
        fun emptyResponse() = WebResourceResponse(
            "text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)),
        )
    }
}
