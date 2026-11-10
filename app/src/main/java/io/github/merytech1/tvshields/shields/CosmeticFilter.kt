package io.github.merytech1.tvshields.shields

import io.github.merytech1.tvshields.engine.AdblockEngine
import org.json.JSONObject

/**
 * URL-specific cosmetic filtering (architecture §5.2a): asks the engine which selectors hide the
 * page's ads and turns them into CSS. Generic selectors are roadmap 4.2.
 *
 * Never throws: cosmetic filtering must not break browsing.
 */
class CosmeticFilter(private val engine: () -> AdblockEngine?) {

    /** CSS that hides the page's ad elements, or null when there is nothing to hide. */
    fun cssFor(url: String): String? {
        val json = try {
            engine()?.cosmeticResources(url)
        } catch (e: RuntimeException) {
            null
        } ?: return null

        val selectors = try {
            JSONObject(json).optJSONArray("hide_selectors")
        } catch (e: Exception) {
            null
        } ?: return null

        val css = (0 until selectors.length())
            .mapNotNull { selectors.optString(it).takeIf(String::isNotBlank) }
            .joinToString(", ")
        return css.takeIf { it.isNotEmpty() }?.let { "$it { display: none !important; }" }
    }
}
