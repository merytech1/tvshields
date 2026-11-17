package io.github.merytech1.tvshields.lists

/**
 * The lists the user can enable in Settings, pinned from Brave's catalog on
 * [DefaultLists.CATALOG_DATE] (docs/resources/filter-lists.md): three annoyance lists, the embed
 * exceptions (hidden in Brave's catalog, offered here) and every regional list (entries with
 * `langs`). Their URLs join [DefaultLists] as the only ones list updates may contact (rules C6).
 * Generated from the catalog: `trusted` mirrors `permission_mask: 1`.
 */
object OptionalLists {

    val annoyances: List<FilterList> = listOf(
        FilterList(
            id = "67E792D4-AE03-4D1A-9EDE-80E01C81F9B8",
            title = "Annoying distractions blocker",
            sources = listOf(
                ListSource("https://secure.fanboy.co.nz/fanboy-annoyance_ubo.txt", "Annoying distractions blocker"),
                ListSource("https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/annoyances-cookies.txt", "uBlock filters - Cookie Notices"),
                ListSource("https://raw.githubusercontent.com/brave/adblock-lists/master/brave-lists/brave-cookie-specific.txt", "Brave-specific cookie additions"),
                ListSource("https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/annoyances-others.txt", "uBO Annoyances list"),
            ),
            trusted = true,
        ),
        FilterList(
            id = "690FF3B4-8B6B-4709-8505-FEC6643D7BD9",
            title = "Newsletter popup blocker",
            sources = listOf(
                ListSource("https://secure.fanboy.co.nz/fanboy-newsletter.txt", "Newsletter popup blocker"),
            ),
            trusted = true,
        ),
        FilterList(
            id = "6b91e355-1421-4c03-9a30-911b4d0fb277",
            title = "AI suggestions blocker",
            sources = listOf(
                ListSource("https://raw.githubusercontent.com/easylist/easylist/refs/heads/master/fanboy-addon/fanboy_ai_suggestions.txt", "AI suggestions blocker"),
            ),
            trusted = true,
        ),
    )

    val embeds: List<FilterList> = listOf(
        FilterList(
            id = "A5E6EC21-F01F-4547-9F0A-1EE1C3F2AE8D",
            title = "Allow Facebook Embeds",
            sources = listOf(
                ListSource("https://raw.githubusercontent.com/brave/adblock-lists/master/brave-lists/embed-exceptions-facebook.txt", "Allow Facebook Embeds"),
            ),
        ),
        FilterList(
            id = "84960ADD-1CC1-419F-81FC-9F116F5205CC",
            title = "Allow X/Twitter Embeds",
            sources = listOf(
                ListSource("https://raw.githubusercontent.com/brave/adblock-lists/master/brave-lists/embed-exceptions-twitter.txt", "Allow X/Twitter Embeds"),
            ),
        ),
        FilterList(
            id = "FB626316-6CC8-4447-884B-F5A37C29B0AE",
            title = "Allow LinkedIn Embeds",
            sources = listOf(
                ListSource("https://raw.githubusercontent.com/brave/adblock-lists/master/brave-lists/embed-exceptions-linkedin.txt", "Allow LinkedIn Embeds"),
            ),
        ),
    )

    val regional: List<FilterList> = listOf(
        FilterList(
            id = "9FCEECEC-52B4-4487-8E57-8781E82C91D0",
            title = "Arabic website ad blocker",
            sources = listOf(
                ListSource("https://easylist-downloads.adblockplus.org/Liste_AR.txt", "Arabic website ad blocker"),
            ),
        ),
        FilterList(
            id = "FD176DD1-F9A0-4469-B43E-B1764893DD5C",
            title = "Bulgarian website ad blocker",
            sources = listOf(
                ListSource("https://stanev.org/abp/adblock_bg.txt", "Bulgarian website ad blocker"),
            ),
        ),
        FilterList(
            id = "92AA0D3B-34AC-4657-9A5C-DBAD339AF8E2",
            title = "Chinese website ad blocker",
            sources = listOf(
                ListSource("https://filters.adtidy.org/extension/ublock/filters/224.txt", "Chinese website ad blocker"),
            ),
        ),
        FilterList(
            id = "7CCB6921-7FDA-4A9B-B70A-12DD0A8F08EA",
            title = "Czech and Slovak website ad blocker",
            sources = listOf(
                ListSource("https://raw.githubusercontent.com/tomasko126/easylistczechandslovak/master/filters.txt", "Czech and Slovak website ad blocker"),
            ),
        ),
        FilterList(
            id = "9D644676-4784-4982-B94D-C9AB19098D2A",
            title = "Dutch website ad blocker",
            sources = listOf(
                ListSource("https://filters.adtidy.org/extension/ublock/filters/8.txt", "Dutch website ad blocker"),
            ),
        ),
        FilterList(
            id = "0783DBFD-B5E0-4982-9B4A-711BDDB925B7",
            title = "Estonian website ad blocker",
            sources = listOf(
                ListSource("https://ubo-et.lepik.io/list.txt", "Estonian website ad blocker"),
            ),
        ),
        FilterList(
            id = "1C6D8556-3400-4358-B9AD-72689D7B2C46",
            title = "Finnish website ad blocker",
            sources = listOf(
                ListSource("https://raw.githubusercontent.com/finnish-easylist-addition/finnish-easylist-addition/gh-pages/Finland_adb.txt", "Finnish website ad blocker"),
            ),
        ),
        FilterList(
            id = "9852EFC4-99E4-4F2D-A915-9C3196C7A1DE",
            title = "French website ad blocker",
            sources = listOf(
                ListSource("https://filters.adtidy.org/extension/ublock/filters/16.txt", "French website ad blocker"),
            ),
        ),
        FilterList(
            id = "E71426E7-E898-401C-A195-177945415F38",
            title = "German website ad blocker",
            sources = listOf(
                ListSource("https://easylist-downloads.adblockplus.org/easylistgermany.txt", "German website ad blocker"),
            ),
        ),
        FilterList(
            id = "6C0F4C7F-969B-48A0-897A-14583015A587",
            title = "Greek website ad blocker",
            sources = listOf(
                ListSource("https://raw.githubusercontent.com/kargig/greek-adblockplus-filter/master/void-gr-filters.txt", "Greek website ad blocker"),
            ),
        ),
        FilterList(
            id = "85F65E06-D7DA-4144-B6A5-E1AA965D1E47",
            title = "Hebrew website ad blocker",
            sources = listOf(
                ListSource("https://raw.githubusercontent.com/easylist/EasyListHebrew/master/EasyListHebrew.txt", "Hebrew website ad blocker"),
                ListSource("https://raw.githubusercontent.com/easylist/EasyListHebrew/refs/heads/master/EasyListHebrew-uBO.txt", "Hebrew website ad blocker"),
            ),
        ),
        FilterList(
            id = "4C07DB6B-6377-4347-836D-68702CF1494A",
            title = "Hindi website ad blocker",
            sources = listOf(
                ListSource("https://easylist-downloads.adblockplus.org/indianlist.txt", "Hindi website ad blocker"),
            ),
        ),
        FilterList(
            id = "EDEEE15A-6FA9-4FAC-8CA8-3565508EAAC3",
            title = "Hungarian website ad blocker",
            sources = listOf(
                ListSource("https://cdn.jsdelivr.net/gh/hufilter/hufilter@gh-pages/hufilter-ublock.txt", "Hungarian website ad blocker"),
            ),
        ),
        FilterList(
            id = "48796273-E783-431E-B864-44D3DCEA66DC",
            title = "Icelandic website ad blocker",
            sources = listOf(
                ListSource("https://raw.githubusercontent.com/brave/adblock-lists/master/custom/is.txt", "Icelandic website ad blocker"),
            ),
        ),
        FilterList(
            id = "93123971-5AE6-47BA-93EA-BE1E4682E2B6",
            title = "Indonesian website ad blocker",
            sources = listOf(
                ListSource("https://raw.githubusercontent.com/heradhis/indonesianadblockrules/master/subscriptions/abpindo.txt", "Indonesian website ad blocker"),
            ),
        ),
        FilterList(
            id = "AB1A661D-E946-4F29-B47F-CA3885F6A9F7",
            title = "Italian website ad blocker",
            sources = listOf(
                ListSource("https://easylist-downloads.adblockplus.org/easylistitaly.txt", "Italian website ad blocker"),
            ),
        ),
        FilterList(
            id = "03F91310-9244-40FA-BCF6-DA31B832F34D",
            title = "Japanese website ad blocker",
            sources = listOf(
                ListSource("https://filters.adtidy.org/extension/ublock/filters/7.txt", "Japanese website ad blocker"),
            ),
        ),
        FilterList(
            id = "45B3ED40-C607-454F-A623-195FDD084637",
            title = "Korean website ad blocker",
            sources = listOf(
                ListSource("https://cdn.jsdelivr.net/npm/@filteringdev/filterslists-ko@latest/dist/filterslist-uBlockOrigin-classic.txt", "Korean website ad blocker"),
            ),
        ),
        FilterList(
            id = "15B64333-BAF9-4B77-ADC8-935433CD6F4C",
            title = "Latvian website ad blocker",
            sources = listOf(
                ListSource("https://raw.githubusercontent.com/Latvian-List/adblock-latvian/master/lists/latvian-list.txt", "Latvian website ad blocker"),
            ),
        ),
        FilterList(
            id = "4E8B1A63-DEBE-4B8B-AD78-3811C632B353",
            title = "Lithuanian website ad blocker",
            sources = listOf(
                ListSource("https://raw.githubusercontent.com/EasyList-Lithuania/easylist_lithuania/master/easylistlithuania.txt", "Lithuanian website ad blocker"),
            ),
        ),
        FilterList(
            id = "7BC951C6-B0B8-4223-97FC-3C22605734FC",
            title = "Macedonian website ad blocker",
            sources = listOf(
                ListSource("https://raw.githubusercontent.com/DeepSpaceHarbor/Macedonian-adBlock-Filters/master/Filters", "Macedonian website ad blocker"),
            ),
        ),
        FilterList(
            id = "8BEDBAA8-4FE2-4FEA-82F2-81B8124A4A74",
            title = "Nordic website ad blocker",
            sources = listOf(
                ListSource("https://raw.githubusercontent.com/DandelionSprout/adfilt/master/NorwegianList.txt", "Nordic website ad blocker"),
            ),
        ),
        FilterList(
            id = "C3C2F394-D7BB-4BC2-9793-E0F13B2B5971",
            title = "Persian website ad blocker",
            sources = listOf(
                ListSource("https://raw.githubusercontent.com/MasterKia/PersianBlocker/main/PersianBlocker-Deprecated.txt", "Persian website ad blocker"),
                ListSource("https://raw.githubusercontent.com/MasterKia/PersianBlocker/main/PersianBlockerAds.txt", "Persian website ad blocker"),
                ListSource("https://raw.githubusercontent.com/MasterKia/PersianBlocker/main/PersianBlockerTrackers.txt", "Persian website ad blocker"),
                ListSource("https://raw.githubusercontent.com/MasterKia/PersianBlocker/main/PersianBlockerCensor.txt", "Persian website ad blocker"),
                ListSource("https://raw.githubusercontent.com/MasterKia/PersianBlocker/main/PersianBlockerHalfPrice.txt", "Persian website ad blocker"),
                ListSource("https://raw.githubusercontent.com/MasterKia/PersianBlocker/main/PersianBlockerMalware.txt", "Persian website ad blocker"),
                ListSource("https://raw.githubusercontent.com/MasterKia/PersianBlocker/main/PersianBlockerPhishing.txt", "Persian website ad blocker"),
                ListSource("https://raw.githubusercontent.com/MasterKia/PersianBlocker/main/PersianBlockerMobile.txt", "Persian website ad blocker"),
            ),
        ),
        FilterList(
            id = "BF9234EB-4CB7-4CED-9FCB-F1FD31B0666C",
            title = "Polish website ad blocker",
            sources = listOf(
                ListSource("https://raw.githubusercontent.com/MajkiIT/polish-ads-filter/master/polish-adblock-filters/adblock.txt", "Polish website ad blocker"),
            ),
        ),
        FilterList(
            id = "AD3E8454-F376-11E8-8EB2-F2801F1B9FD1",
            title = "Romanian website ad blocker",
            sources = listOf(
                ListSource("https://raw.githubusercontent.com/tcptomato/ROad-Block/master/road-block-filters-light.txt", "Romanian website ad blocker"),
                ListSource("https://raw.githubusercontent.com/tcptomato/ROad-Block/master/road-ubo.txt", "Romanian website ad blocker"),
            ),
        ),
        FilterList(
            id = "80470EEC-970F-4F2C-BF6B-4810520C72E6",
            title = "Russian website ad blocker - RU AdList",
            sources = listOf(
                ListSource("https://easylist-downloads.adblockplus.org/advblock.txt", "Russian website ad blocker - RU AdList"),
                ListSource("https://raw.githubusercontent.com/easylist/ruadlist/master/js-fixes-experimental.txt", "Russian website ad blocker - RU AdList"),
            ),
        ),
        FilterList(
            id = "418D293D-72A8-4A28-8718-A1EE40A45AAF",
            title = "Slovenian website ad blocker",
            sources = listOf(
                ListSource("https://raw.githubusercontent.com/betterwebleon/slovenian-list/master/filters.txt", "Slovenian website ad blocker"),
            ),
        ),
        FilterList(
            id = "AE657374-1851-4DC4-892B-9212B13B15A7",
            title = "Spanish website ad blocker",
            sources = listOf(
                ListSource("https://easylist-downloads.adblockplus.org/easylistspanish.txt", "Spanish website ad blocker"),
            ),
        ),
        FilterList(
            id = "1FEAF960-F377-11E8-8EB2-F2801F1B9FD1",
            title = "Spanish and Portuguese website ad blocker",
            sources = listOf(
                ListSource("https://filters.adtidy.org/extension/ublock/filters/9.txt", "Spanish and Portuguese website ad blocker"),
            ),
        ),
        FilterList(
            id = "7DC2AC80-5BBC-49B8-B473-A31A1145CAC1",
            title = "Swedish website ad blocker",
            sources = listOf(
                ListSource("https://raw.githubusercontent.com/lassekongo83/Frellwits-filter-lists/master/Frellwits-Swedish-Filter.txt", "Swedish website ad blocker"),
            ),
        ),
        FilterList(
            id = "658F092A-F377-11E8-8EB2-F2801F1B9FD1",
            title = "Thai website ad blocker",
            sources = listOf(
                ListSource("https://raw.githubusercontent.com/easylist-thailand/easylist-thailand/master/subscription/easylist-thailand.txt", "Thai website ad blocker"),
            ),
        ),
        FilterList(
            id = "1BE19EFD-9191-4560-878E-30ECA72B5B3C",
            title = "Turkish website ad blocker",
            sources = listOf(
                ListSource("https://filters.adtidy.org/extension/ublock/filters/13.txt", "Turkish website ad blocker"),
            ),
        ),
        FilterList(
            id = "6A0209AC-9869-4FD6-A9DF-039B4200D52C",
            title = "Vietnamese website ad blocker",
            sources = listOf(
                ListSource("https://raw.githubusercontent.com/abpvn/abpvn/master/filter/abpvn_ublock.txt", "Vietnamese website ad blocker"),
            ),
        ),
    )

    val all: List<FilterList> = annoyances + embeds + regional

    private val byId = all.associateBy { it.id }

    /** The default lists plus the enabled optional ones, in catalog order. Unknown ids are ignored. */
    fun withEnabled(enabled: Set<String>): List<FilterList> =
        DefaultLists.all + all.filter { it.id in enabled }

    fun byId(id: String): FilterList? = byId[id]
}
