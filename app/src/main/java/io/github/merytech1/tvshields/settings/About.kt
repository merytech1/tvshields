package io.github.merytech1.tvshields.settings

import io.github.merytech1.tvshields.lists.DefaultLists
import io.github.merytech1.tvshields.lists.FilterList
import java.net.URI

/** A piece of software credited on the About screen. [license] uses SPDX names. */
data class Credit(val name: String, val author: String, val license: String, val url: String)

/** What the About screen credits (roadmap 6.4, rules L1, L2 and C6). */
object About {
    const val SOURCE_URL = "https://github.com/merytech1/tvshields"

    /** Must match engine/Cargo.lock (AboutTest). */
    const val ADBLOCK_VERSION = "0.13.5"

    /** Built by tools/licenses/generate.mjs: full notices of everything inside the APK. */
    const val LICENSES_ASSET = "third_party_licenses.txt"

    val engine = Credit("adblock-rust $ADBLOCK_VERSION", "Brave Software", "MPL-2.0", "https://github.com/brave/adblock-rust")

    /** Inside resources.json, downloaded with the lists (rules C11). */
    val resources = listOf(
        Credit("adblock-resources", "Brave Software", "MPL-2.0", "https://github.com/brave/adblock-resources"),
        Credit("uBlock Origin scriptlets and redirects", "Raymond Hill and contributors", "GPL-3.0", "https://github.com/gorhill/uBlock"),
    )

    /** The only servers list updates contact (rules C6): the hosts of [lists], then the resources'. */
    fun updateHosts(lists: List<FilterList>, resourcesUrl: String? = DefaultLists.RESOURCES_URL): List<String> =
        (lists.flatMap { list -> list.sources.map { it.url } } + listOfNotNull(resourcesUrl))
            .mapNotNull { URI(it).host }
            .distinct()
}
