package io.github.merytech1.tvshields.browser

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import io.github.merytech1.tvshields.shields.CosmeticFilter
import io.github.merytech1.tvshields.shields.CosmeticMessage
import io.github.merytech1.tvshields.shields.CosmeticResources
import io.github.merytech1.tvshields.shields.applyGenericCosmeticCss
import io.github.merytech1.tvshields.shields.cosmeticScript
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cosmetic filtering for one WebView (architecture §5.2).
 *
 * URL-specific selectors are registered before the load starts, so they apply before the first
 * paint. The generic script (`assets/js/cosmetic.js`) runs at document start too and keeps
 * reporting new classes and ids; [CosmeticFilter] asks the engine off the UI thread and the
 * matching selectors are appended to the page's own `<style>` element.
 *
 * Document-start scripts are optional (rules C4): the CSS is re-applied in `onPageStarted`, and
 * generic filtering is skipped when `WEB_MESSAGE_LISTENER` is unavailable.
 */
class CosmeticInjector(context: Context, private val filter: CosmeticFilter) {

    private val appContext = context.applicationContext

    /** Accessed from the UI thread; volatile only because [prepare] reads it from a background one. */
    @Volatile
    private var handler: ScriptHandler? = null

    /** URL that received a document-start script, so a duplicate preparation is ignored. */
    @Volatile
    private var preparedUrl: String? = null

    /** URL, engine exceptions and generation of the document the generic messages come from. */
    @Volatile
    private var currentPage: PageState? = null

    /** Selectors already appended to the page's generic `<style>` element. UI thread only. */
    private val appliedSelectors = HashSet<String>()

    /** Drops messages while the engine is already working; the script batches them anyway. */
    private val engineBusy = AtomicBoolean(false)

    /** Created in [attach] and cancelled in [dispose], together with the WebView lifetime. */
    private var scope: CoroutineScope? = null

    private var attached = false

    /** Incremented for each new document; UI thread only. */
    private var generation = 0L

    private val supportsDocumentStart =
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    private val supportsWebMessages =
        WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

    private val genericScript: String by lazy { readGenericScript() }
    private val log = Log.isLoggable(TAG, Log.DEBUG)

    private val messageListener = WebViewCompat.WebMessageListener { view, message, sourceOrigin, isMainFrame, _ ->
        onWebMessage(view, message, sourceOrigin, isMainFrame)
    }

    /** Starts generic filtering for [view]: a WebView receives the listener before any load. */
    fun attach(view: WebView) {
        if (!supportsWebMessages) {
            Log.i(TAG, "Generic cosmetic filtering off: WEB_MESSAGE_LISTENER unavailable")
            return
        }
        if (attached) return
        try {
            WebViewCompat.addWebMessageListener(view, JS_OBJECT, setOf("*"), messageListener)
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            attached = true
            if (log) Log.d(TAG, "Generic cosmetic listener attached")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Generic cosmetic listener rejected", e)
        } catch (e: UnsupportedOperationException) {
            Log.w(TAG, "Generic cosmetic listener unavailable", e)
        }
    }

    /** Removes the JS channel and stops pending work. Call before destroying the WebView. */
    fun dispose(view: WebView) {
        if (attached) {
            try {
                WebViewCompat.removeWebMessageListener(view, JS_OBJECT)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Generic cosmetic listener already removed", e)
            }
            attached = false
        }
        scope?.cancel()
        scope = null
        currentPage = null
        appliedSelectors.clear()
    }

    /**
     * Registers the cosmetic scripts for a main-frame request. Called from
     * `shouldInterceptRequest`, which runs on a WebView background thread; the registration itself
     * is a UI-thread call, so this method waits for it (a few milliseconds) before letting the
     * response through.
     */
    fun prepare(view: WebView, url: String) {
        if (!supportsDocumentStart) return
        if (preparedUrl == url && handler != null) return
        val resources = if (isHttp(url)) filter.resourcesFor(url) else null

        onUiThread(view) {
            // The load may be prepared twice (link override and request interception): keep the
            // first script, because only scripts registered before the load begins are executed.
            if (preparedUrl == url && handler != null) return@onUiThread
            // Drop the previous page's selectors: they do not apply to this URL.
            handler?.remove()
            handler = null
            preparedUrl = null
            if (resources == null) {
                currentPage = null
                appliedSelectors.clear()
                return@onUiThread
            }

            val page = PageState(url, resources.exceptions, ++generation)
            currentPage = page
            appliedSelectors.clear()
            val script = pageScript(resources)
            if (script.isEmpty()) return@onUiThread
            try {
                handler = WebViewCompat.addDocumentStartJavaScript(view, script, setOf(originRule(url)))
                preparedUrl = url
                if (log) Log.d(TAG, "Cosmetic script at document start for $url (${script.length} chars)")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Invalid origin rule for $url", e)
            } catch (e: UnsupportedOperationException) {
                Log.w(TAG, "Document-start scripts are not supported", e)
            }
        }
    }

    /**
     * Re-applies the scripts after the page starts: the only injection without
     * `DOCUMENT_START_SCRIPT` (rules C4) and a safety net when document-start registration
     * happened too late. `cosmetic.js` ignores the second run.
     */
    fun injectFallback(view: WebView, url: String) {
        if (!isHttp(url)) return
        val resources = filter.resourcesFor(url) ?: return
        val previous = currentPage
        val sameOrigin = previous != null && originKey(previous.url) == originKey(url)
        currentPage = PageState(
            url = url,
            exceptions = resources.exceptions,
            generation = if (sameOrigin) previous.generation else ++generation,
        )
        appliedSelectors.clear()
        val script = pageScript(resources)
        if (script.isEmpty()) return
        if (log) Log.d(TAG, "Cosmetic script after page start for $url (${script.length} chars)")
        view.evaluateJavascript(script, null)
    }

    private fun pageScript(resources: CosmeticResources): String = buildString {
        resources.css?.let { append(cosmeticScript(it)) }
        if (!resources.generichide && supportsWebMessages && genericScript.isNotBlank()) {
            if (isNotEmpty()) append('\n')
            append(genericScript)
        }
    }

    /**
     * Receives the classes and ids seen by `cosmetic.js`. The message is untrusted: only the main
     * frame, only its own origin and only capped string lists are accepted (architecture §5.2b).
     */
    private fun onWebMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
    ) {
        if (!isMainFrame || message.type != WebMessageCompat.TYPE_STRING) return
        val page = currentPage ?: return
        val pageOrigin = originKey(page.url) ?: return
        if (originKey(sourceOrigin.toString()) != pageOrigin) return
        val data = message.data ?: return
        if (data.length > CosmeticMessage.MAX_MESSAGE_CHARS) return
        val tokens = CosmeticMessage.parse(data) ?: return
        if (tokens.classes.isEmpty() && tokens.ids.isEmpty()) return
        if (!engineBusy.compareAndSet(false, true)) return
        val scope = scope
        if (scope == null) {
            engineBusy.set(false)
            return
        }

        scope.launch {
            val selectors = try {
                filter.hiddenSelectors(tokens.classes, tokens.ids, page.exceptions)
            } finally {
                engineBusy.set(false)
            }
            if (selectors.isEmpty()) return@launch
            withContext(Dispatchers.Main) {
                val current = currentPage
                if (current == null || current.generation != page.generation) return@withContext
                val fresh = selectors.filter { appliedSelectors.add(it) }
                if (fresh.isEmpty()) return@withContext
                val css = fresh.joinToString(", ") + " { display: none !important; }"
                if (log) Log.d(TAG, "Generic cosmetic CSS for ${current.url} (${fresh.size} selectors)")
                view.evaluateJavascript(applyGenericCosmeticCss(css), null)
            }
        }
    }

    private fun readGenericScript(): String = try {
        appContext.assets.open(GENERIC_SCRIPT_ASSET).bufferedReader().use { it.readText() }
    } catch (e: IOException) {
        Log.e(TAG, "Missing $GENERIC_SCRIPT_ASSET: generic cosmetic filtering is off", e)
        ""
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

    private data class PageState(val url: String, val exceptions: List<String>, val generation: Long)

    private companion object {
        const val TAG = "tvshields.cosmetic"
        const val UI_TIMEOUT_MS = 100L
        const val JS_OBJECT = "tvshieldsCosmetic"
        const val GENERIC_SCRIPT_ASSET = "js/cosmetic.js"
    }
}

private fun isHttp(url: String) = url.startsWith("http://") || url.startsWith("https://")

/**
 * The page's origin in the format `addDocumentStartJavaScript` expects. Anything unparseable
 * matches every origin; the selector list is still URL-specific, so it only hides its own ads.
 */
internal fun originRule(url: String): String = originKey(url) ?: "*"

/**
 * Canonical origin for comparing the page URL with the origin WebView reports for a message.
 * Default ports are omitted, as WebView does. Null when [url] is not an absolute web URL.
 */
internal fun originKey(url: String): String? = try {
    val uri = URI(url)
    val scheme = uri.scheme?.lowercase()
    val host = uri.host?.lowercase()
    if (scheme == null || host == null) {
        null
    } else {
        val port = when {
            uri.port == -1 -> null
            scheme == "http" && uri.port == 80 -> null
            scheme == "https" && uri.port == 443 -> null
            else -> uri.port
        }
        if (port == null) "$scheme://$host" else "$scheme://$host:$port"
    }
} catch (e: URISyntaxException) {
    null
}
