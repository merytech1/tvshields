package io.github.merytech1.tvshields.search

/** Search providers from architecture §7. `%s` is replaced by the encoded query. */
enum class SearchEngine(val displayName: String, val domain: String, val template: String) {
    BRAVE("Brave Search", "search.brave.com", "https://search.brave.com/search?q=%s"),
    DUCKDUCKGO("DuckDuckGo", "duckduckgo.com", "https://duckduckgo.com/?q=%s"),
    STARTPAGE("Startpage", "startpage.com", "https://www.startpage.com/do/search?q=%s"),
    GOOGLE("Google", "google.com", "https://www.google.com/search?q=%s"),
}
