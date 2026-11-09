package io.github.merytech1.tvshields.shields

/** Request types as adblock-rust names them (filter options such as `$script`, `$image`). */
enum class RequestType(val wireName: String) {
    DOCUMENT("document"),
    SUB_FRAME("sub_frame"),
    STYLESHEET("stylesheet"),
    SCRIPT("script"),
    IMAGE("image"),
    FONT("font"),
    MEDIA("media"),
    OTHER("other"),
}

/**
 * Infers a request's type, which the WebView does not report (architecture §5.1). Rules, in order:
 * 1. Main frame → document.
 * 2. First type in the `Accept` header: `text/css`, `image/…`, `text/html` (an iframe).
 *    Only the first one counts: a page's `Accept` also lists image types further on.
 * 3. Extension of the URL path.
 * 4. Otherwise → other. XHR/fetch cannot be told apart reliably, so they end up here too.
 */
object RequestTypeGuesser {

    fun guess(url: String, isForMainFrame: Boolean, accept: String?): RequestType {
        if (isForMainFrame) return RequestType.DOCUMENT
        fromAccept(accept)?.let { return it }
        return EXTENSIONS[extension(url)] ?: RequestType.OTHER
    }

    private fun fromAccept(accept: String?): RequestType? {
        val first = accept?.substringBefore(',')?.substringBefore(';')?.trim()?.lowercase() ?: return null
        return when {
            first == "text/css" -> RequestType.STYLESHEET
            first.startsWith("image/") -> RequestType.IMAGE
            first == "text/html" -> RequestType.SUB_FRAME
            else -> null
        }
    }

    /** Lowercase extension of the last path segment, or "" (the host's ".com" never counts). */
    internal fun extension(url: String): String {
        val afterScheme = url.substringAfter("://", url)
        val path = afterScheme.substringBefore('?').substringBefore('#')
        if (!path.contains('/')) return ""
        val segment = path.substringAfterLast('/')
        if (!segment.contains('.')) return ""
        return segment.substringAfterLast('.').lowercase()
    }

    private val EXTENSIONS: Map<String, RequestType> = buildMap {
        listOf("js", "mjs").forEach { put(it, RequestType.SCRIPT) }
        put("css", RequestType.STYLESHEET)
        listOf("png", "jpg", "jpeg", "gif", "webp", "avif", "svg", "ico", "bmp").forEach { put(it, RequestType.IMAGE) }
        listOf("mp4", "m4s", "webm", "m3u8", "mpd", "ts", "mp3", "m4a", "aac").forEach { put(it, RequestType.MEDIA) }
        listOf("woff", "woff2", "ttf", "otf", "eot").forEach { put(it, RequestType.FONT) }
    }
}
