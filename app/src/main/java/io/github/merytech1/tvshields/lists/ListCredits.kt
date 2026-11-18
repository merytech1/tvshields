package io.github.merytech1.tvshields.lists

/** Who makes a list file and under which license (rules L2). [license] uses SPDX names. */
data class ListCredit(val author: String, val license: String, val homepage: String)

/**
 * Credits for every source in [DefaultLists] and [OptionalLists], matched by URL prefix (the
 * most specific prefix first). Brave's catalog names the lists but not their authors or licenses;
 * these come from each list's header and its repository, checked on 2026-11-18.
 */
object ListCredits {
    private const val GH = "https://raw.githubusercontent.com"
    private const val EASYLIST_DUAL = "GPL-3.0 or CC-BY-SA-3.0"
    private val easyList = ListCredit("The EasyList authors", EASYLIST_DUAL, "https://easylist.to")
    private val fanboy = ListCredit("Fanboy (EasyList)", "CC-BY-3.0", "https://easylist.to")

    private val byPrefix: List<Pair<String, ListCredit>> = listOf(
        "$GH/uBlockOrigin/uAssets/" to ListCredit("uBlock Origin", "GPL-3.0", "https://github.com/uBlockOrigin/uAssets"),
        "$GH/brave/adblock-lists/" to ListCredit("Brave Software", "MPL-2.0", "https://github.com/brave/adblock-lists"),
        "https://easylist.to/" to easyList,
        "https://easylist-downloads.adblockplus.org/Liste_AR.txt" to
            ListCredit("Liste AR", "CC-BY-SA-3.0", "https://code.google.com/p/liste-ar-adblock/"),
        "https://easylist-downloads.adblockplus.org/advblock.txt" to
            ListCredit("RU AdList", "CC-BY-3.0", "https://forums.lanik.us/viewforum.php?f=102"),
        "https://easylist-downloads.adblockplus.org/" to easyList,
        "$GH/easylist/easylist/refs/heads/master/fanboy-addon/" to fanboy,
        "$GH/easylist/EasyListHebrew/" to ListCredit("The EasyList authors", EASYLIST_DUAL, "https://github.com/easylist/EasyListHebrew"),
        "$GH/easylist/ruadlist/" to ListCredit("RU AdList (EasyList)", EASYLIST_DUAL, "https://github.com/easylist/ruadlist"),
        "https://secure.fanboy.co.nz/" to fanboy,
        "https://malware-filter.gitlab.io/malware-filter/" to
            ListCredit("malware-filter, from URLhaus (abuse.ch)", "CC0-1.0 and MIT", "https://gitlab.com/malware-filter/urlhaus-filter"),
        "https://filters.adtidy.org/" to ListCredit("AdGuard", "GPL-3.0", "https://github.com/AdguardTeam/AdguardFilters"),
        "https://stanev.org/abp/" to ListCredit("Alex Stanev", "Not stated", "https://stanev.org/abp"),
        "$GH/tomasko126/easylistczechandslovak/" to
            ListCredit("tomasko126 and contributors", "CC-BY-SA-4.0", "https://github.com/tomasko126/easylistczechandslovak"),
        "https://ubo-et.lepik.io/" to ListCredit("sander85", "CC0-1.0", "https://github.com/sander85/uBO-et"),
        "$GH/finnish-easylist-addition/finnish-easylist-addition/" to
            ListCredit("Finnish EasyList addition", "Unlicense", "https://github.com/finnish-easylist-addition/finnish-easylist-addition"),
        "$GH/kargig/greek-adblockplus-filter/" to
            ListCredit("kargig", "CC-BY-SA-4.0", "https://github.com/kargig/greek-adblockplus-filter"),
        "https://cdn.jsdelivr.net/gh/hufilter/" to ListCredit("Hufilter contributors", "CC-BY-4.0", "https://hufilter.hu"),
        "$GH/heradhis/indonesianadblockrules/" to
            ListCredit("ABPindo", "GPL-3.0 or CC-BY-SA-3.0", "https://github.com/ABPindo/indonesianadblockrules"),
        "https://cdn.jsdelivr.net/npm/@filteringdev/filterslists-ko" to
            ListCredit("FilteringDev", "GPL-3.0", "https://github.com/FilteringDev/filterslists-KO"),
        "$GH/Latvian-List/adblock-latvian/" to
            ListCredit("Latvian List", "CC-BY-SA-4.0", "https://github.com/Latvian-List/adblock-latvian"),
        "$GH/EasyList-Lithuania/easylist_lithuania/" to
            ListCredit("EasyList Lithuania", "GPL-3.0", "https://github.com/EasyList-Lithuania/easylist_lithuania"),
        "$GH/DeepSpaceHarbor/Macedonian-adBlock-Filters/" to
            ListCredit("DeepSpaceHarbor", "MIT", "https://github.com/DeepSpaceHarbor/Macedonian-adBlock-Filters"),
        "$GH/DandelionSprout/adfilt/" to
            ListCredit("Dandelion Sprout", "Dandelicence 1.4", "https://github.com/DandelionSprout/adfilt"),
        "$GH/MasterKia/PersianBlocker/" to
            ListCredit("MasterKia", "AGPL-3.0", "https://github.com/MasterKia/PersianBlocker"),
        "$GH/MajkiIT/polish-ads-filter/" to
            ListCredit("Certyficate IT", "CC-BY-NC-SA-4.0", "https://github.com/MajkiIT/polish-ads-filter"),
        "$GH/tcptomato/ROad-Block/" to ListCredit("tcptomato", "MIT", "https://github.com/tcptomato/ROad-Block"),
        "$GH/betterwebleon/slovenian-list/" to
            ListCredit("betterwebleon", "Unlicense", "https://github.com/betterwebleon/slovenian-list"),
        "$GH/lassekongo83/Frellwits-filter-lists/" to
            ListCredit("Frellwit (lassekongo83)", "GPL-3.0", "https://github.com/lassekongo83/Frellwits-filter-lists"),
        "$GH/easylist-thailand/easylist-thailand/" to
            ListCredit("EasyList Thailand", "Apache-2.0", "https://github.com/easylist-thailand/easylist-thailand"),
        "$GH/abpvn/abpvn/" to ListCredit("ABPVN", "GPL-3.0", "https://github.com/abpvn/abpvn"),
    ).sortedByDescending { it.first.length }

    fun of(source: ListSource): ListCredit? = byPrefix.firstOrNull { source.url.startsWith(it.first) }?.second

    /** [list]'s sources grouped by credit, in list order. */
    fun grouped(list: FilterList): List<Pair<ListCredit, List<ListSource>>> =
        list.sources.groupBy { of(it) ?: ListCredit(it.url, "Not stated", it.url) }.toList()
}
