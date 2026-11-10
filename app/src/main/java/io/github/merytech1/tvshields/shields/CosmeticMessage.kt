package io.github.merytech1.tvshields.shields

import org.json.JSONObject

/** Class and id values the page's generic cosmetic script has seen. */
data class CosmeticTokens(val classes: List<String>, val ids: List<String>)

/**
 * Parses the generic cosmetic messages (`assets/js/cosmetic.js`). The page controls them, so only
 * capped lists of short strings are accepted; anything else is dropped (architecture §5.2b).
 */
object CosmeticMessage {
    /** Longer messages are not produced by our script. */
    const val MAX_MESSAGE_CHARS = 16 * 1024

    /** Matches the cap in `cosmetic.js`. */
    const val MAX_TOKENS = 500

    const val MAX_TOKEN_LENGTH = 128

    fun parse(json: String): CosmeticTokens? {
        if (json.isEmpty() || json.length > MAX_MESSAGE_CHARS) return null
        val message = try {
            JSONObject(json)
        } catch (e: Exception) {
            return null
        }
        return CosmeticTokens(
            classes = message.stringList("classes"),
            ids = message.stringList("ids"),
        )
    }

    private fun JSONObject.stringList(name: String): List<String> {
        val array = optJSONArray(name) ?: return emptyList()
        val limit = minOf(array.length(), MAX_TOKENS)
        return buildList(limit) {
            for (i in 0 until limit) {
                val value = array.opt(i) as? String ?: continue
                if (value.isBlank() || value.length > MAX_TOKEN_LENGTH) continue
                add(value)
            }
        }.distinct()
    }
}
