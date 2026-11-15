package io.github.merytech1.tvshields.browser

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.merytech1.tvshields.engine.AdblockEngine
import io.github.merytech1.tvshields.engine.NativeAdblockEngine
import io.github.merytech1.tvshields.engine.NetworkDecision
import io.github.merytech1.tvshields.lists.StoredList
import io.github.merytech1.tvshields.shields.CosmeticFilter
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Roadmap 5.3 on the device: a page with a classic ad-block detector, loaded in a real WebView
 * through [CosmeticInjector]. The bait script is blocked, so the page shows its warning, unless
 * the `+js(set, …)` scriptlet runs at document start. Needs the app to have downloaded
 * `resources.json` once (open it with a network before running this test).
 */
@RunWith(AndroidJUnit4::class)
class ScriptletInjectionTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private val resources: String by lazy {
        val file = File(context.filesDir, "lists/resources.json")
        check(file.isFile) { "No ${file.path}: open the app with a network first" }
        file.readText()
    }

    @Test
    fun detectorShowsItsWarningWithoutTheScriptlet() {
        val result = load(BLOCK_BAIT)
        assertEquals(false, result.getBoolean("hidden"))
        assertEquals(0, result.getInt("guards"))
    }

    @Test
    fun scriptletDefeatsTheDetector() {
        val result = load("$BLOCK_BAIT\n$HOST##+js(set, canRunAds, true)")
        assertEquals(true, result.getBoolean("hidden"))
        // Document start and the onPageStarted fallback both carry it; it must run once.
        assertEquals(1, result.getInt("guards"))
    }

    /** Loads the test page with an engine built from [rules] and reads the warning's state. */
    private fun load(rules: String): JSONObject {
        val engine = NativeAdblockEngine.fromLists(listOf(StoredList(rules, trusted = true)))
        engine.useResources(resources)
        val finished = CountDownLatch(1)
        val answer = CountDownLatch(1)
        var json = ""
        var view: WebView? = null
        try {
            instrumentation.runOnMainSync {
                val web = WebView(context)
                view = web
                web.settings.javaScriptEnabled = true
                val injector = CosmeticInjector(context, CosmeticFilter { engine })
                web.webViewClient = TestClient(engine, injector, finished)
                // As BrowserScreen does: the script is registered before the load begins.
                injector.prepare(web, PAGE)
                web.loadUrl(PAGE)
            }
            check(finished.await(TIMEOUT_S, TimeUnit.SECONDS)) { "Test page did not finish loading" }
            instrumentation.runOnMainSync {
                view!!.evaluateJavascript(READ_STATE) { value ->
                    json = value
                    answer.countDown()
                }
            }
            check(answer.await(TIMEOUT_S, TimeUnit.SECONDS)) { "No answer from the page" }
        } finally {
            instrumentation.runOnMainSync { view?.destroy() }
            engine.close()
        }
        // evaluateJavascript returns the string result JSON-encoded.
        return JSONObject(JSONObject("{\"v\":$json}").getString("v"))
    }

    /** The parts of `ShieldsWebViewClient` this test needs, serving the page from memory. */
    private class TestClient(
        private val engine: AdblockEngine,
        private val injector: CosmeticInjector,
        private val finished: CountDownLatch,
    ) : WebViewClient() {

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url.toString()
            return when {
                request.isForMainFrame -> {
                    injector.prepare(view, url)
                    response("text/html", HTML)
                }
                url == BAIT -> {
                    val blocked = engine.checkRequest(url, PAGE, "script") == NetworkDecision.Block
                    if (blocked) WebResourceResponse("text/javascript", "utf-8", 204, "No Content", emptyMap(), null)
                    else response("text/javascript", "window.canRunAds = true;")
                }
                else -> WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), null)
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            injector.injectFallback(view, url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            finished.countDown()
        }

        private fun response(mime: String, body: String) =
            WebResourceResponse(mime, "utf-8", 200, "OK", emptyMap(), body.byteInputStream())
    }

    private companion object {
        const val HOST = "detector.test"
        const val PAGE = "https://$HOST/"
        const val BAIT = "https://$HOST/ads.js"
        const val BLOCK_BAIT = "||$HOST/ads.js^"
        const val TIMEOUT_S = 20L

        /** The classic detector: a bait script named like an ad sets a flag the page checks. */
        val HTML = """
            <!doctype html>
            <html><head><title>detector</title><script src="/ads.js"></script></head>
            <body>
              <p id="warning" hidden>Please disable your ad blocker</p>
              <script>
                if (window.canRunAds !== true) document.getElementById('warning').hidden = false;
              </script>
            </body></html>
        """.trimIndent()

        const val READ_STATE = """JSON.stringify({
            hidden: document.getElementById('warning').hidden,
            canRunAds: String(window.canRunAds),
            guards: Object.getOwnPropertyNames(window).filter(function (n) { return n.indexOf('tvshields_') === 0; }).length
        })"""
    }
}
