package io.github.merytech1.tvshields.shields

import java.net.URI
import java.net.URISyntaxException

/**
 * The per-site switch (rules P3, C5). It is resolved here, before the engine is asked: a page on
 * a site with Shields off gets no blocking, no cosmetic filtering and no scriptlets.
 */
interface SiteShieldsStore {
    /** Whether Shields are off for the site [url] belongs to. Called for every request: no I/O. */
    fun isOff(url: String): Boolean

    /** Turns Shields off (or back on) for [site], as returned by [SiteShields.siteOf]. */
    fun setOff(site: String, off: Boolean)
}

object SiteShields {

    /** The key a page is stored under: its host, lowercase, without "www.". Null for non-web URLs. */
    fun siteOf(url: String): String? {
        val uri = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return null
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        return uri.host?.lowercase()?.removePrefix("www.")?.takeIf { it.isNotEmpty() }
    }

    /**
     * Whether [url] belongs to one of [offSites]: the site itself or one of its subdomains, so
     * turning Shields off on `example.com` also covers `m.example.com`.
     */
    fun isOff(offSites: Set<String>, url: String): Boolean {
        if (offSites.isEmpty()) return false
        var host = siteOf(url) ?: return false
        while (true) {
            if (host in offSites) return true
            val dot = host.indexOf('.')
            if (dot < 0) return false
            host = host.substring(dot + 1)
        }
    }
}
