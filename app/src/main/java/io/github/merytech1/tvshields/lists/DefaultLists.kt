package io.github.merytech1.tvshields.lists

/** One downloadable filter list file. */
data class ListSource(val url: String, val title: String)

/**
 * A catalog entry: a named group of sources that is enabled or disabled as a whole.
 * [trusted] lists may inject uBO's `requiresTrust` scriptlets (`trusted-set-cookie`, …); Brave's
 * catalog marks them with `permission_mask: 1`.
 */
data class FilterList(
    val id: String,
    val title: String,
    val sources: List<ListSource>,
    val trusted: Boolean = false,
)

/**
 * The lists Brave enables by default, pinned from its catalog
 * (brave/adblock-resources `filter_lists/list_catalog.json`, MPL-2.0) on [CATALOG_DATE].
 * "Brave iOS-Specific Filters" is left out (iOS only). See docs/resources/filter-lists.md.
 * These URLs and [RESOURCES_URL] are the only ones list updates may contact (rules C6).
 * All five are trusted, as in Brave's catalog.
 */
object DefaultLists {
    const val CATALOG_DATE = "2026-11-09"

    /** Scriptlets and redirect resources built by `tools/resources` (roadmap 5.0, rules C11). */
    const val RESOURCES_URL = "https://github.com/merytech1/tvshields/releases/download/resources/resources.json"

    private const val UBO = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters"
    private const val BRAVE = "https://raw.githubusercontent.com/brave/adblock-lists/master"

    val all: List<FilterList> = listOf(
        FilterList(
            id = "default",
            title = "Brave Default Adblock Filters",
            sources = listOf(
                ListSource("$UBO/filters.txt", "uBlock Origin Filters"),
                ListSource("$UBO/filters-2020.txt", "uBlock Origin 2020 Filters"),
                ListSource("$UBO/filters-2021.txt", "uBlock Origin 2021 Filters"),
                ListSource("$UBO/filters-2022.txt", "uBlock Origin 2022 Filters"),
                ListSource("$UBO/filters-2023.txt", "uBlock Origin 2023 Filters"),
                ListSource("$UBO/filters-2024.txt", "uBlock Origin 2024 Filters"),
                ListSource("$UBO/filters-2025.txt", "uBlock Origin 2025 Filters"),
                ListSource("$UBO/filters-2026.txt", "uBlock Origin 2026 Filters"),
                ListSource("$UBO/filters-general.txt", "uBlock Origin filters - General"),
                ListSource("$UBO/badware.txt", "uBlock Origin filters - Badware risks"),
                ListSource("$UBO/resource-abuse.txt", "uBlock Origin filters - Resource abuse"),
                ListSource("$UBO/unbreak.txt", "uBlock Origin filters - Unbreak"),
                ListSource("$UBO/quick-fixes.txt", "uBlock Origin filters - Quick Fixes"),
                ListSource("$UBO/ubo-link-shorteners.txt", "uBlock Origin filters - Link shorteners"),
                ListSource("https://easylist.to/easylist/easylist.txt", "EasyList"),
                ListSource(
                    "https://malware-filter.gitlab.io/malware-filter/urlhaus-filter-agh-online.txt",
                    "URLhaus Malicious URL Blocklist",
                ),
                ListSource("$BRAVE/brave-unbreak.txt", "Brave Unbreak"),
                ListSource("$BRAVE/brave-lists/brave-specific.txt", "Brave Specific"),
                ListSource("$BRAVE/brave-lists/brave-social.txt", "Brave Social"),
                ListSource("$BRAVE/brave-lists/brave-unbreak.txt", "Brave Unbreak"),
                ListSource("$BRAVE/brave-lists/brave-android-specific.txt", "Brave Android-Specific Rules"),
                ListSource("$BRAVE/brave-lists/brave-sugarcoat.txt", "SugarCoat Rules"),
            ),
            trusted = true,
        ),
        FilterList(
            id = "4D715457-307C-4383-8873-73A2FD263F71",
            title = "Brave Default Privacy Filters",
            sources = listOf(
                ListSource("https://easylist.to/easylist/easyprivacy.txt", "EasyPrivacy"),
                ListSource("$UBO/privacy.txt", "uBlock Origin filters - Privacy"),
            ),
            trusted = true,
        ),
        FilterList(
            id = "E99CBD02-FFD1-4651-9BDD-6A9ED7B87819",
            title = "Brave First Party Adblock Filters",
            sources = listOf(
                ListSource("$BRAVE/brave-lists/brave-firstparty.txt", "Brave First-Party Specific Filters"),
                ListSource("$BRAVE/brave-lists/brave-firstparty-regional.txt", "Brave First-Party Regional Filters"),
            ),
            trusted = true,
        ),
        FilterList(
            id = "AC023D22-AE88-4060-A978-4FEEEC4221693",
            title = "Cookie notice blocker",
            sources = listOf(
                ListSource("https://secure.fanboy.co.nz/fanboy-cookiemonster_ubo.txt", "EasyList-Cookie"),
                ListSource("$UBO/annoyances-cookies.txt", "uBlock filters - Cookie Notices"),
                ListSource("$BRAVE/brave-lists/brave-cookie-specific.txt", "Brave-specific cookie additions"),
            ),
            trusted = true,
        ),
        FilterList(
            id = "2F3DCE16-A19A-493C-A88F-2E110FBD37D6",
            title = "Mobile app promo blocker",
            sources = listOf(
                ListSource("https://secure.fanboy.co.nz/fanboy-mobile-notifications.txt", "Fanboy mobile notifications"),
            ),
            trusted = true,
        ),
    )
}
