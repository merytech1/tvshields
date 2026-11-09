package io.github.merytech1.tvshields.browser

/** Short form of a URL for the address bar when it is not being edited: no scheme, no "www.". */
fun displayUrl(url: String): String =
    url.substringAfter("://", url)
        .removePrefix("www.")
        .removeSuffix("/")
