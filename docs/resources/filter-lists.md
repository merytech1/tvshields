# Filter lists

The rules that say what to block. We use **the same catalog as Brave**, published at
[`brave/adblock-resources/filter_lists/list_catalog.json`](https://github.com/brave/adblock-resources/blob/master/filter_lists/list_catalog.json) (MPL-2.0).

## How we use them

- Downloaded **at runtime** from their original URLs. Never bundled in the APK (rules T6, L2, C9).
- Updated every 24 h with `ETag` / `If-Modified-Since`.
- The "About" screen lists each one with its author and license.
- The catalog marks which lists Brave enables by default (`default_enabled`). We mirror that selection.

## Enabled by default

Catalog entries with `default_enabled: true` (checked on 2026-11-07):

| Catalog entry | Main sources | Source licenses |
|---|---|---|
| Brave Default Adblock Filters | uBlock Origin (`filters*.txt`, `badware`, `unbreak`, `quick-fixes`…), EasyList, URLhaus (malware), and Brave's `brave-specific`, `brave-android-specific`, `brave-social`, `brave-unbreak` and **`brave-sugarcoat.txt`** lists (the rules that enable SugarCoat) | GPLv3 (uBO) / GPLv3 or CC BY-SA 3.0 (EasyList) / MPL-2.0 (Brave) / URLhaus's own |
| Brave Default Privacy Filters | EasyPrivacy, uBlock Origin `privacy.txt` | GPLv3 or CC BY-SA 3.0 / GPLv3 |
| Brave First Party Adblock Filters | `brave/adblock-lists` `brave-firstparty*.txt` | MPL-2.0 |
| Cookie notice blocker | Fanboy Cookiemonster, uBO `annoyances-cookies.txt`, Brave `brave-cookie-specific.txt` | CC BY 3.0 / GPLv3 / MPL-2.0 |
| Mobile app promo blocker | Fanboy mobile notifications | CC BY 3.0 |

**Excluded:** "Brave iOS-Specific Filters", which is iOS-only.

The exact URLs (30 sources) are pinned in `lists/DefaultLists.kt` from the catalog of
**2026-11-09** (`CATALOG_DATE`). The sources of each entry can change, so re-check the catalog
when updating them. On that date every source answered with an `ETag`; EasyList, EasyPrivacy,
URLhaus and the Fanboy lists also send `Last-Modified`.

## Storage

`FilterListRepository` keeps each source in `filesDir/lists/` as `<hash>.txt` (the list) and
`<hash>.meta` (its `ETag` / `Last-Modified`). The hash is a short SHA-256 of the URL. An update
sends `If-None-Match` / `If-Modified-Since`, so an unchanged list costs a 304 and no download.
A source that fails keeps its previous copy. Files are written to a temporary file and renamed, so
a list is never left half written. Responses over 16 MB are rejected.

## Optional (in Settings)

"Annoying distractions blocker", "Newsletter popup blocker", "AI suggestions blocker", the embed
exceptions (Facebook, X, LinkedIn) and the **regional lists**.

For Spanish, the catalog offers two lists (`langs: ["es"]`):

| Entry | Source |
|---|---|
| Spanish website ad blocker | EasyList Spanish (`easylistspanish.txt`) |
| Spanish and Portuguese website ad blocker | AdGuard Spanish/Portuguese (`filters.adtidy.org/…/9.txt`) |

Proposal: enable by default the regional lists that match the TV's language, as Brave does with the
browser language.

## Syntax (quick reference)

| Rule | Meaning |
|---|---|
| `||ads.example.com^` | Block that whole domain |
| `||cdn.example/ad.js$script,third-party` | Block that script when loaded from a third party |
| `@@||example.com/ads.js` | Exception: do not block |
| `example.com##.banner-ad` | Hide elements matching that selector on that site (1.2) |
| `##.ad-slot` | Hide on every site (generic cosmetic) |
| `example.com##+js(set-constant, x, false)` | Inject a scriptlet (1.3) |
| `||x.com/a.js$script,redirect=noopjs` | Replace with a fake resource (1.3) |

Syntax documentation: https://github.com/gorhill/uBlock/wiki/Static-filter-syntax
