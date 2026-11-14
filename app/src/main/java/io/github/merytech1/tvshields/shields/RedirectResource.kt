package io.github.merytech1.tvshields.shields

import kotlin.io.encoding.Base64

/**
 * A `redirect=` resource from the engine (`noop.js`, `1x1.gif`, …), served instead of the real
 * response (architecture §5.3, roadmap 5.2).
 */
class RedirectResource(val mimeType: String, val bytes: ByteArray) {

    /** Charset for text types; WebView expects null for binary ones. */
    val encoding: String? get() = if (isText(mimeType)) "utf-8" else null

    companion object {
        /**
         * Parses the engine's `data:<mime>;base64,<payload>` URL (adblock-rust always encodes
         * resources this way). Null for anything else, so the caller can fall back to blocking.
         */
        fun fromDataUrl(url: String): RedirectResource? {
            if (!url.startsWith(PREFIX)) return null
            val comma = url.indexOf(',')
            if (comma < 0) return null
            val header = url.substring(PREFIX.length, comma)
            if (!header.endsWith(BASE64)) return null
            val mimeType = header.removeSuffix(BASE64).ifEmpty { return null }
            val bytes = try {
                Base64.decode(url, startIndex = comma + 1)
            } catch (e: IllegalArgumentException) {
                return null
            }
            return RedirectResource(mimeType, bytes)
        }

        private const val PREFIX = "data:"
        private const val BASE64 = ";base64"

        private fun isText(mimeType: String) =
            mimeType.startsWith("text/") || mimeType == "application/javascript" || mimeType == "application/json"
    }
}
