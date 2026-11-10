package io.github.merytech1.tvshields.browser

import android.os.Looper
import android.util.Log
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.merytech1.tvshields.shields.CosmeticFilter
import io.github.merytech1.tvshields.shields.cosmeticScript
import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Injects URL-specific cosmetic CSS before the page runs any code (architecture §5.2a).
 *
 * With `DOCUMENT_START_SCRIPT` the script is registered before the load starts, so the ads are
 * hidden before the first paint. The CSS is also re-applied in `onPageStarted` as a safety net;
 * without the feature (rules C4) that is the only injection and ads can flash briefly.
 *
 * One instance per WebView.
 */
class CosmeticInjector(private val filter: CosmeticFilter) {

    /** Accessed from the UI thread; volatile only because [prepare] reads it from a background one. */
    @Volatile
    private var handler: ScriptHandler? = null

    /** URL that received a document-start script, so the fallback does not inject it twice. */
    @Volatile
    private var preparedUrl: String? = null

    private val supportsDocumentStart =
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    private val log = Log.isLoggable(TAG, Log.DEBUG)

    /**
     * Registers the cosmetic script for a main-frame request. Called from
     * `shouldInterceptRequest`, which runs on a WebView background thread; the registration itself
     * is a UI-thread call, so this method waits for it (a few milliseconds) before letting the
     * response through.
     */
    fun prepare(view: WebView, url: String) {
        if (!supportsDocumentStart) return
        if (preparedUrl == url && handler != null) return
        val css = if (isHttp(url)) filter.cssFor(url) else null
        if (css == null && handler == null) return

        onUiThread(view) {
            // The load may be prepared twice (link override and request interception): keep the
            // first script, because only scripts registered before the load begins are executed.
            if (preparedUrl == url && handler != null) return@onUiThread
            // Drop the previous page's selectors: they do not apply to this URL.
            handler?.remove()
            handler = null
            preparedUrl = null
            if (css == null) return@onUiThread
            try {
                handler = WebViewCompat.addDocumentStartJavaScript(
                    view,
                    cosmeticScript(css),
                    setOf(originRule(url)),
                )
                preparedUrl = url
                if (log) Log.d(TAG, "Cosmetic CSS at document start for $url (${css.length} chars)")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Invalid origin rule for $url", e)
            } catch (e: UnsupportedOperationException) {
                Log.w(TAG, "Document-start scripts are not supported", e)
            }
        }
    }

    /**
     * Re-applies the CSS after the page starts: the only injection without `DOCUMENT_START_SCRIPT`
     * (rules C4) and a safety net when document-start registration happened too late.
     */
    fun injectFallback(view: WebView, url: String) {
        if (!isHttp(url)) return
        val css = filter.cssFor(url) ?: return
        if (log) Log.d(TAG, "Cosmetic CSS after page start for $url (${css.length} chars)")
        view.evaluateJavascript(cosmeticScript(css), null)
    }

    private fun onUiThread(view: WebView, block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
            return
        }
        val done = CountDownLatch(1)
        if (!view.post { try { block() } finally { done.countDown() } }) return
        try {
            // The request thread must not hang if the UI thread is busy: the fallback covers it.
            done.await(UI_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private companion object {
        const val TAG = "tvshields.cosmetic"
        const val UI_TIMEOUT_MS = 100L
    }
}

private fun isHttp(url: String) = url.startsWith("http://") || url.startsWith("https://")

/**
 * The page's origin in the format `addDocumentStartJavaScript` expects. Anything unparseable
 * matches every origin; the selector list is still URL-specific, so it only hides its own ads.
 */
internal fun originRule(url: String): String = try {
    val uri = URI(url)
    val scheme = uri.scheme
    val host = uri.host
    if (scheme == null || host == null) {
        "*"
    } else {
        buildString {
            append(scheme).append("://").append(host)
            if (uri.port != -1) append(':').append(uri.port)
        }
    }
} catch (e: URISyntaxException) {
    "*"
}
