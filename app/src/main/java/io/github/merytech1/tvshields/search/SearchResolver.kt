package io.github.merytech1.tvshields.search

import java.net.IDN
import java.net.URLEncoder

/**
 * Turns what the user typed in the address bar into a URL to open (architecture §7).
 *
 * - `http://` or `https://` URLs open as typed. Other schemes (`javascript:`, `file:`) are searched.
 * - A public domain (its top-level domain is in [tlds]) opens over https: `example.com`,
 *   `foo.bar`, `españa.com`. `file.txt` and `node.js` are searched.
 * - Local addresses open over http, since local devices rarely have TLS: `localhost`, IPv4 and
 *   IPv6 literals, `*.local`, `*.localhost` and `*.home.arpa`. Other single names (`router`) are
 *   searched, as `AC/DC` must be: type `http://router` to open one.
 * - Text with spaces, or starting with `?`, is searched with the chosen engine.
 */
class SearchResolver(
    private val tlds: () -> Set<String> = TopLevelDomains::all,
    private val engine: () -> SearchEngine,
) {

    fun resolve(input: String): String? {
        val text = input.trim()
        if (text.startsWith("?")) return search(text.drop(1).trim())
        if (WEB_URL.matches(text)) return text
        if (text.none(Char::isWhitespace)) address(text)?.let { return it }
        return search(text)
    }

    private fun search(text: String): String? =
        if (text.isEmpty()) null else engine().template.replace("%s", URLEncoder.encode(text, "UTF-8"))

    private fun address(text: String): String? {
        val match = ADDRESS.matchEntire(text) ?: return null
        val (host, port) = match.destructured
        if (port.isNotEmpty() && port.toInt() !in 1..65535) return null
        return when {
            isLocal(host) -> "http://$text"
            isPublicDomain(host.removeSuffix(".")) -> "https://$text"
            else -> null
        }
    }

    private fun isLocal(host: String): Boolean {
        val name = host.lowercase().removeSuffix(".")
        return name == "localhost" ||
            LOCAL_SUFFIXES.any { name.endsWith(it) && isPublicLabels(name.removeSuffix(it)) } ||
            isIpv4(name) ||
            IPV6.matches(name)
    }

    private fun isIpv4(host: String): Boolean {
        val parts = host.split('.')
        return parts.size == 4 && parts.all { it.length in 1..3 && it.all(Char::isDigit) && it.toInt() <= 255 }
    }

    private fun isPublicDomain(host: String): Boolean {
        val labels = host.split('.')
        if (labels.size < 2 || !isPublicLabels(labels.dropLast(1).joinToString("."))) return false
        val tld = try {
            IDN.toASCII(labels.last()).lowercase()
        } catch (_: IllegalArgumentException) {
            return false
        }
        return tld in tlds()
    }

    /** Dot-separated labels of letters (any script), digits and inner hyphens, 1 to 63 long. */
    private fun isPublicLabels(name: String) = name.split('.').all { LABEL.matches(it) }

    private companion object {
        val WEB_URL = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)

        // host (a name, or an IPv6 literal in brackets), optional port, optional path / query / fragment.
        val ADDRESS = Regex("^(\\[[0-9a-fA-F:.]+]|[^/?#:@\\[\\]]+)(?::(\\d{1,5}))?([/?#].*)?$")
        val LABEL = Regex("^[\\p{L}\\p{N}](?:[\\p{L}\\p{N}-]{0,61}[\\p{L}\\p{N}])?$")
        val IPV6 = Regex("^\\[[0-9a-f]*:[0-9a-f:.]*]$")
        val LOCAL_SUFFIXES = listOf(".localhost", ".local", ".home.arpa")
    }
}

/** IANA's list of top-level domains, bundled as `tlds.txt` next to this class (see its header). */
object TopLevelDomains {
    val all: Set<String> by lazy {
        val stream = checkNotNull(TopLevelDomains::class.java.getResourceAsStream("/io/github/merytech1/tvshields/search/tlds.txt")) { "tlds.txt is missing" }
        stream.bufferedReader().useLines { lines ->
            lines.map { it.trim().lowercase() }.filter { it.isNotEmpty() && !it.startsWith("#") }.toSet()
        }
    }
}
