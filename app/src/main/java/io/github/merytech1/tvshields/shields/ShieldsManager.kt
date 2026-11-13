package io.github.merytech1.tvshields.shields

import io.github.merytech1.tvshields.engine.AdblockEngine
import io.github.merytech1.tvshields.engine.NetworkDecision

/**
 * Decides what happens to each request the WebView makes (architecture §5.1). Runs on WebView
 * background threads, for every request, so it stays small and never throws.
 *
 * [engine] returns the current engine, or null while it is loading: requests are then allowed.
 */
class ShieldsManager(
    val perf: ShieldsPerf = ShieldsPerf.Disabled,
    private val engine: () -> AdblockEngine?,
) {

    /**
     * @param sourceUrl URL of the page that made the request (the main document).
     * @param accept the request's `Accept` header, used to guess its type.
     */
    fun check(url: String, sourceUrl: String, isForMainFrame: Boolean, accept: String?, method: String): NetworkDecision {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return NetworkDecision.Allow
        val current = engine() ?: return NetworkDecision.Allow
        return perf.measure(ShieldsPerf.Op.REQUEST) {
            val type = RequestTypeGuesser.guess(url, isForMainFrame, accept)
            try {
                current.checkRequest(url, sourceUrl, type.wireName, method)
            } catch (e: RuntimeException) {
                // A failing engine must never break browsing.
                NetworkDecision.Allow
            }
        }
    }
}
