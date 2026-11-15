package io.github.merytech1.tvshields.browser

import java.net.URI
import java.net.URISyntaxException

/** Short form of a URL for the address bar when it is not being edited: no scheme, no "www.". */
fun displayUrl(url: String): String =
    url.substringAfter("://", url)
        .removePrefix("www.")
        .removeSuffix("/")

/** The site a page belongs to, for the Shields panel: the host without "www.", or the short URL. */
fun displaySite(url: String): String {
    val host = try {
        URI(url).host
    } catch (e: URISyntaxException) {
        null
    }
    return host?.lowercase()?.removePrefix("www.") ?: displayUrl(url)
}
