package io.github.merytech1.tvshields.search

import java.net.URLEncoder

/** Turns what the user typed in the address bar into a URL to open (architecture §7). */
class SearchResolver(private val engine: () -> SearchEngine) {

    fun resolve(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        if (SCHEME.containsMatchIn(text)) return text
        if (!text.any(Char::isWhitespace)) {
            if (LOCAL_HOST.matches(text)) return "http://$text"
            if (HOST.matches(text)) return "https://$text"
        }
        return engine().template.replace("%s", URLEncoder.encode(text, "UTF-8"))
    }

    private companion object {
        // Only web schemes open directly; anything else (javascript:, file:, intent:) is searched.
        val SCHEME = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)
        // localhost or an IPv4 address, optional port and path: local devices usually lack TLS.
        val LOCAL_HOST = Regex("^(localhost|\\d{1,3}(\\.\\d{1,3}){3})(:\\d{1,5})?(/.*)?$", RegexOption.IGNORE_CASE)
        // name.tld with a letters-only TLD, optional port and path.
        val HOST = Regex("^([a-z0-9-]+\\.)+[a-z]{2,}(:\\d{1,5})?([/?#].*)?$", RegexOption.IGNORE_CASE)
    }
}
