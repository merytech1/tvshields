package io.github.merytech1.tvshields.shields

import io.github.merytech1.tvshields.engine.AdblockEngine
import org.json.JSONArray
import org.json.JSONObject

/**
 * URL-specific cosmetic resources (architecture §5.2a), the generic selectors that hide
 * classes and ids as they appear on the page (architecture §5.2b), and the procedural filters
 * evaluated by `assets/js/procedural.js` (architecture §5.2c).
 *
 * Never throws: cosmetic filtering must not break browsing.
 */
class CosmeticFilter(
    private val perf: ShieldsPerf = ShieldsPerf.Disabled,
    private val engine: () -> AdblockEngine?,
) {

    /**
     * What the engine knows about [url]. [css] is null when there is nothing URL-specific to
     * hide; [procedural] is the JSON array of procedural filters for `assets/js/procedural.js`;
     * [generichide] disables generic filtering, and [exceptions] must be passed back to
     * [hiddenSelectors] for the filters to respect `#@#` rules.
     */
    fun resourcesFor(url: String): CosmeticResources? = perf.measure(ShieldsPerf.Op.COSMETIC) {
        val current = engine() ?: return@measure null
        cached(current, url)?.let { return@measure it.resources }
        val resources = resources(current, url)
        // A failure is not kept: the next request tries again.
        if (resources != null) remember(current, url, resources)
        resources
    }

    /**
     * One page asks several times (document-start registration, the `onPageStarted` fallback,
     * redirects), sometimes on the UI thread. Building the answer takes tens of milliseconds on
     * the reference TV, so the last few are kept (rules T7). A new engine drops them.
     */
    private class Cached(val engine: AdblockEngine, val resources: CosmeticResources)

    private val cache = object : LinkedHashMap<String, Cached>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Cached>) = size > CACHE_SIZE
    }

    private fun cached(current: AdblockEngine, url: String): Cached? = synchronized(cache) {
        cache[url]?.takeIf { it.engine === current }
    }

    private fun remember(current: AdblockEngine, url: String, resources: CosmeticResources) = synchronized(cache) {
        if (cache.values.any { it.engine !== current }) cache.clear()
        cache[url] = Cached(current, resources)
    }

    private fun resources(current: AdblockEngine, url: String): CosmeticResources? {
        val json = try {
            current.cosmeticResources(url)
        } catch (e: Exception) {
            return null
        }

        val resources = try {
            JSONObject(json)
        } catch (e: Exception) {
            return null
        }

        // Selectors with procedural syntax are not valid CSS: keep them out of the stylesheet or
        // they would invalidate every other selector in the same rule. They are evaluated by the
        // page script instead (nested `:has(...:has-text(...))` arrives here this way).
        val (raw, plain) = resources.stringList("hide_selectors").partition(ProceduralSelector::looksProcedural)

        return CosmeticResources(
            css = css(plain),
            generichide = resources.optBoolean("generichide", false),
            exceptions = resources.stringList("exceptions"),
            procedural = procedural(resources, raw),
        )
    }

    /**
     * Validated filters for the page script, or null when there is nothing procedural. The cap
     * bounds both the script size and the work done on every mutation (rules T7).
     */
    private fun procedural(resources: JSONObject, rawSelectors: List<String>): String? {
        val filters = JSONArray()
        for (action in resources.stringList("procedural_actions")) {
            if (filters.length() >= MAX_FILTERS) break
            ProceduralSelector.validate(action)?.let(filters::put)
        }
        for (selector in rawSelectors) {
            if (filters.length() >= MAX_FILTERS) break
            ProceduralSelector.parse(selector)?.let(filters::put)
        }
        return filters.takeIf { it.length() > 0 }?.toString()
    }

    /**
     * Generic hide selectors matching the classes and ids seen on the page. Empty when the engine
     * is loading, fails, or a page exception disables every match.
     */
    fun hiddenSelectors(
        classes: List<String>,
        ids: List<String>,
        exceptions: List<String>,
    ): List<String> {
        if (classes.isEmpty() && ids.isEmpty()) return emptyList()
        return perf.measure(ShieldsPerf.Op.GENERIC) { selectors(classes, ids, exceptions) }
    }

    private fun selectors(classes: List<String>, ids: List<String>, exceptions: List<String>): List<String> {
        return try {
            engine()
                ?.hiddenClassIdSelectors(classes, ids, exceptions)
                ?.filter(String::isNotBlank)
                ?.distinct()
                ?.take(MAX_SELECTORS)
                .orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun css(selectors: List<String>): String? =
        selectors.joinToString(", ").takeIf { it.isNotEmpty() }?.let { "$it { display: none !important; }" }

    private fun JSONObject.stringList(name: String): List<String> {
        val array = optJSONArray(name) ?: return emptyList()
        return (0 until array.length()).mapNotNull { (array.opt(it) as? String)?.takeIf(String::isNotBlank) }
    }

    private companion object {
        /** Listed selectors are applied in one CSS rule; the cap keeps the rule small (rules T7). */
        const val MAX_SELECTORS = 500

        /** Procedural rules are re-evaluated on mutations; the cap bounds that work (rules T7). */
        const val MAX_FILTERS = 32

        const val CACHE_SIZE = 4
    }
}

/** Cosmetic resources for one page, as returned by the engine. */
data class CosmeticResources(
    val css: String?,
    val generichide: Boolean,
    val exceptions: List<String>,
    val procedural: String? = null,
)
