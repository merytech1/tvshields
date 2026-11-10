package io.github.merytech1.tvshields.shields

import io.github.merytech1.tvshields.engine.AdblockEngine
import org.json.JSONObject

/**
 * URL-specific cosmetic resources (architecture §5.2a), plus the generic selectors that hide
 * classes and ids as they appear on the page (architecture §5.2b).
 *
 * Never throws: cosmetic filtering must not break browsing.
 */
class CosmeticFilter(private val engine: () -> AdblockEngine?) {

    /**
     * What the engine knows about [url]. [css] is null when there is nothing URL-specific to
     * hide; [generichide] disables generic filtering, and [exceptions] must be passed back to
     * [hiddenSelectors] for the filters to respect `#@#` rules.
     */
    fun resourcesFor(url: String): CosmeticResources? {
        val json = try {
            engine()?.cosmeticResources(url)
        } catch (e: Exception) {
            null
        } ?: return null

        val resources = try {
            JSONObject(json)
        } catch (e: Exception) {
            return null
        }

        return CosmeticResources(
            css = css(resources.stringList("hide_selectors")),
            generichide = resources.optBoolean("generichide", false),
            exceptions = resources.stringList("exceptions"),
        )
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
    }
}

/** Cosmetic resources for one page, as returned by the engine. */
data class CosmeticResources(
    val css: String?,
    val generichide: Boolean,
    val exceptions: List<String>,
)
