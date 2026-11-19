# Testing on the reference TV

Phase 8 checks Shields on real sites, on the reference TV (architecture §10). This file holds the
test sites (roadmap 8.1). Later tasks add their results here: the inventory of ad requests per site
without blocking (8.2), the blocked / leaked comparison (8.3) and the performance measurements (8.4).

## 1. Test sites (roadmap 8.1)

The sites cover the three kinds the roadmap asks for (news, video, streaming) and the three Shields
capabilities (P4). Most are Spanish-language sites from Colombia and Spain, where the reference TV is
used, plus English sites with heavy ad stacks. All are free and legal, and need no account.

Every site was opened on the reference TV with tvshields on 2026-11-18. It loaded, and Shields
blocked requests on it. The counts below are the panel's counter for one load of the page.
Counts vary between loads; 8.2 and 8.3 measure them properly.

### News

| Site | URL | Why | Blocked (2026-11-18) |
|---|---|---|---|
| El País | `https://elpais.com` | Spanish news site with a heavy ad stack and a consent banner (Cookie notice blocker). Already measured in roadmap 4.4. | (4.4) |
| El Tiempo | `https://www.eltiempo.com` | The biggest Colombian newspaper. Display ads and trackers. | 24 |
| Marca | `https://www.marca.com` | Sports site with many ad slots. Shows a "get the app" banner (Mobile app promo blocker, 1.2). | 47 |
| The Verge | `https://www.theverge.com` | English site with many third-party requests. Already measured in roadmap 4.4. | (4.4) |
| CNN | `https://edition.cnn.com` | English news with video players and many trackers. | 4 |

### Video

| Site | URL | Why | Blocked (2026-11-18) |
|---|---|---|---|
| YouTube | `https://www.youtube.com/watch?v=kJQP7kiw5Fk` | Pre-roll and mid-roll ads. Only scriptlets (1.3) can remove some of them; ads served inside the stream cannot be cut (rules C7). The TV gets the mobile site (`m.youtube.com`). The home page is empty without a watch history, so a fixed, monetized video is used. | 5 (home page) |
| Dailymotion | `https://www.dailymotion.com` | Video ads (IMA) before the video. Open the first video of the home page. | 12 |

### Streaming

| Site | URL | Why | Blocked (2026-11-18) |
|---|---|---|---|
| Pluto TV | `https://pluto.tv` | Free live TV with ads. Redirects to `pluto.tv/latam` and starts a channel on load. Ads are mostly inserted in the stream on the server (C7), so this site shows what network blocking cannot do. | 16 |
| ViX | `https://vix.com` | Free Spanish-language streaming with ads (the free tier). | 12 |

### Controls

| Site | URL | Why | Blocked (2026-11-18) |
|---|---|---|---|
| Wikipedia | `https://es.wikipedia.org` | No ads. Anything blocked or broken here is a false positive. Baseline for performance (4.4). | (4.4) |
| Can You Block It | `https://canyoublockit.com` | Ad-blocker test pages: banners, pop-ups and video ads under "Tests". | 6 |

### Considered and left out

| Site | Reason |
|---|---|
| Twitch | Stays black on the reference TV even with Shields off for the site: a WebView incompatibility, not a Shields problem. Worth retrying after a WebView update. |
| Tubi | Not available in Colombia (redirects to `gdpr.tubi.tv`). |
| Infobae | Timed out from the test network. |
| Forbes | Rejects automated clients (HTTP 403), so REA captures would not be reliable. |
| Toolz Ad Block Test (`d3ward.github.io/toolz/adblock`) | The project is archived: the page only shows a notice and no longer runs the test (checked on the TV during 8.2). |
| Netflix, Prime Video, Disney+ | Need an account and DRM (Widevine). Ads, when present, are inside the stream (C7). Out of scope. |

## 2. How to test a site

1. **Build:** use a debug build for REA observation (it enables WebView debugging, see
   [resources/rea.md](resources/rea.md)). Use a release build for performance (8.4).
2. **TV state:** note the WebView version (`adb shell dumpsys package com.google.android.webview |
   grep versionName`; 157.0.8103.38 on 2026-11-18) and the date. Lists update by themselves, so
   results from different days can differ.
3. **Without blocking:** turn Shields off for the site in the Shields panel (P3) and load the page.
4. **With blocking:** turn Shields back on and load the page again.
5. **Clean up:** leave Shields on for every test site when you finish.

Sites change their ads often. Before each round, open every site once and replace any that stopped
working, with the reason, in "Considered and left out".

## 3. Ad inventory without blocking (roadmap 8.2)

What each site requests when Shields are off, recorded on the reference TV on 2026-11-18.

### How it was recorded

- **Recording:** a debug build with the WebView's DevTools forwarded over `adb` (see
  [resources/rea.md](resources/rea.md)). `tools/capture/requests.mjs` recorded every request of the
  page with the browser cache disabled.
- **Load:** for each site, the page was opened with Shields on, then Shields were turned off for the
  site in the panel. The reload that follows is the recorded load. Recording ran for 45 s (60 s for
  Pluto TV). El País was recorded on a later load, opened directly with Shields already off.
- **No interaction:**
  - Consent banners were left unanswered, so sites that wait for consent load fewer ads.
  - Videos only played when the site starts them by itself (YouTube and Pluto TV start muted).
- **Classification:** `tools/capture/inventory.mjs` groups requests by domain and sorts third-party
  services into ads, tracking and consent with a hand-made table (`tools/capture/classify.mjs`).
  The table is independent of the filter lists, so 8.3 can compare Shields against it. Third-party requests not in the table (CDNs,
  players, paywalls) are counted as "other" and not shown here.
- **Raw captures:** not kept in the repository, because they contain per-device identifiers and
  consent strings.

### Results

Requests in the recorded load; in parentheses, the number of distinct domains.

| Site | Requests | Ads | Tracking | Consent | Main ad domains (requests) |
|---|---|---|---|---|---|
| El País | 98 | 17 (4) | 21 (8) | 6 (3) | google.com (14), amazon-adsystem.com (1), doubleclick.net (1) |
| El Tiempo | 217 | 85 (16) | 48 (12) | 0 | google.com (27), doubleclick.net (23), mathilde-ads.com (6) |
| Marca | 140 | 17 (9) | 6 (5) | 4 (1) | smartadserver.com (6), adnxs.com (2), rubiconproject.com (2) |
| The Verge | 232 | 24 (11) | 13 (8) | 15 (2) | doubleclick.net (8), concert.io (5), google.com (3) |
| CNN | 404 | 329 (2) | 0 | 0 | brightline.tv (328), imasdk.googleapis.com (1) |
| YouTube (video page) | 67 | 4 (2) | 12 (1) | 0 | doubleclick.net (3), google.com (1) |
| Dailymotion (video page) | 114 | 4 (3) | 0 | 0 | imasdk.googleapis.com (2), pbstck.com (1), doubleclick.net (1) |
| Pluto TV | 227 | 7 (4) | 14 (1) | 9 (2) | doubleclick.net (4), imasdk.googleapis.com (1), googlesyndication.com (1) |
| ViX | 192 | 0 | 5 (4) | 9 (2) | none |
| Wikipedia | 30 | 0 | 0 | 0 | none |
| Can You Block It | 86 | 3 (3) | 4 (3) | 4 (1) | googlesyndication.com (1), doubleclick.net (1), google.com.co (1) |

### Findings

- **El Tiempo has the heaviest ad stack:**
  - 16 ad domains. Most are header-bidding partners: Rich Audience, Mathilde, Smart, Criteo,
    Rubicon, Teads and Amazon.
  - Google remarketing and conversion calls (`google.com/rmkt`, `/ccm`, `/pagead`).
  - 12 tracking services.
- **CNN requests one Brightline ad configuration 328 times in 45 s** (`cdn-media.brightline.tv/config`),
  a loop from its video ad technology. Blocking it should cut most of CNN's network activity on the
  TV.
- **Ads and trackers load before consent:**
  - Consent banners were never answered, yet El País made 17 ad and 21 tracking requests, and
    Marca and The Verge also loaded ads.
  - With Shields on, the Cookie notice blocker hides these banners without answering them. Shields
    must block these requests: an unanswered banner does not stop them.
- **Video ads come from the player:**
  - YouTube asks `youtube.com/pagead` and `doubleclick.net` itself.
  - Dailymotion and Pluto TV load Google's IMA SDK (`imasdk.googleapis.com`).
- **Ads inside the stream (C7):**
  - ViX's home page makes no third-party ad request, so its ads come with the video.
  - Most of Pluto TV's ads are inserted in the stream on the server.
  - Network blocking cannot remove either.
- **Wikipedia makes no ad or tracking request**, as expected from the control.
- **Can You Block It's home page has few ads.** Its tests are on the pages under "Tests".
- **Bot checks:**
  - The first El País recording got an HTTP 403 and a captcha page from its bot protection, after
    the site had been loaded several times in a row.
  - It worked when recorded again later.
  - Avoid loading the same site many times in quick succession, and never solve captchas from the
    test scripts.

Limits: one load of one page per site; counts change from load to load and as sites change their
ads. Only services in the classification table are counted as ads or tracking.

## 4. Blocked and leaked requests (roadmap 8.3)

The same sites with Shields on, recorded on the reference TV on 2026-11-18, right after 8.2. Each
request is compared with the app's own block log.

### How it was recorded

- **Block log:**
  - Run `adb shell setprop log.tag.tvshields.block DEBUG`, then restart the app.
  - The app then logs every blocked or redirected request under the `tvshields.block` tag (see
    [architecture.md](architecture.md)).
  - After a restart, the WebView only answers DevTools once a page has loaded, so open any page
    from the address bar first.
- **Load:**
  - For each site, with Shields on:
    1. A first load (15 s), then `about:blank`.
    2. Clear the log, then record a second load through DevTools with `tools/capture/requests.mjs`
       (45 s; 60 s for Pluto TV).
    3. Dump the log with `adb logcat -d -v raw -s tvshields.block:D`.
  - No interaction, as in 8.2.
- **Comparison:** `tools/capture/compare.mjs` matches the recorded requests with the log by URL:
  - The log keeps the URL fragment (`#…`) and DevTools drops it, so fragments are ignored.
  - A blocked request can appear several times in the recording (for example a blocked `fetch`
    that the page retries), so URLs are matched as a set.
  - An ad or tracking request (same table as 8.2) that is not in the log reached its server: it
    **leaked**.
- **Reference:** the "Shields off" numbers are the 8.2 load of the same page
  ([§3](#3-ad-inventory-without-blocking-roadmap-82)).

### Results

"Off" is the 8.2 load; "on" is the load with Shields on. For ads and tracking: requests with
Shields off, then requests blocked / leaked with Shields on.

| Site | Requests off → on | Blocked (log) | Ads off → blocked / leaked | Tracking off → blocked / leaked | What leaked |
|---|---|---|---|---|---|
| El País | 98 → 64 | 6 | 17 → 2 / 0 | 21 → 4 / 1 | Adobe Launch script (`assets.adobedtm.com`) |
| El Tiempo | 217 → 118 | 27 | 85 → 8 / 0 | 48 → 15 / 4 | Tealium tag manager scripts (`tags.tiqcdn.com`) |
| Marca | 140 → 151 | 28 | 17 → 17 / 0 | 6 → 9 / 2 | Tealium tag manager scripts (`tags.tiqcdn.com`) |
| The Verge | 232 → 194 | 13 | 24 → 8 / 2 | 13 → 1 / 0 | Concert ad library scripts (`cdn.concert.io`) |
| CNN | 404 → 74 | 4 | 329 → 1 / 0 | 0 → 0 / 0 | none |
| YouTube (video page) | 67 → 62 | 12 | 4 → 4 / 0 | 12 → 5 / 6 | Playback statistics (`youtube.com/api/stats`) |
| Dailymotion (video page) | 114 → 129 | 34 | 4 → 1 / 0 | 0 → 0 / 0 | none |
| Pluto TV | 227 → 228 | 24 | 7 → 11 / 1 | 14 → 12 / 0 | Google PAL loader (`imasdk.googleapis.com/pal`) |
| ViX | 192 → 187 | 12 | 0 → 0 / 0 | 5 → 4 / 1 | Segment settings (`cdn.segment.com`) |
| Wikipedia | 30 → 30 | 2 | 0 → 0 / 0 | 0 → 0 / 0 | none |
| Can You Block It | 86 → 79 | 6 | 3 → 1 / 0 | 4 → 2 / 0 | none |
| **Total** | **1807 → 1316** | **168** | **490 → 53 / 3** | **123 → 52 / 14** | |

### Findings

- **Blocking a loader stops what it would load:**
  - With Shields on, pages make far fewer ad requests: 56 instead of 490. Only 3 of them reached
    a server.
  - CNN's Brightline loop (328 requests in 8.2) does not start: its loader is blocked.
  - El Tiempo makes 8 ad requests instead of 85.
- **What leaks is mostly the loader itself:**
  - Tag managers and ad libraries that the default lists do not block reach their servers:
    - Tealium on El Tiempo and Marca.
    - Adobe Launch on El País.
    - Concert on The Verge.
    - Google PAL on Pluto TV.
  - Most of the requests they make afterwards are blocked:
    - El País: Adobe's measurement modules, `demdex.net` and `omtrdc.net`.
    - The Verge: Concert's `fins/segment` and `lookup` calls.
- **YouTube's playback statistics are not blocked** (`api/stats/playback`, `watchtime`, `atr`).
  The default lists leave them. Its `log_event` calls and ad requests are blocked.
- **Blocked requests can be retried:** Pluto TV made 11 ad requests with Shields on and 7 without.
  The page repeats requests that fail. Pluto's in-stream ads (C7) are unaffected either way.
- **First-party tracking is blocked too, with no visible breakage:**
  - The Verge's own tag manager and event endpoints.
  - YouTube's `log_event`.
  - CNN's Adobe Launch on `lightning.cnn.com`.
  - Marca's pixel counter.
  - Wikipedia's banner impression beacon and Wikimedia's event intake.
- **Dailymotion:**
  - 28 of its 34 blocks are its own event tracking (`dm-event.net`).
  - 4 are video manifest requests that carry consent and ad parameters
    (`dmxleo.dailymotion.com/cdn/manifest`).
  - The video still played. Watch this site when the lists change: a broader rule there would stop
    playback.
- **ViX:** the 6 "other" blocks are bot-detection collectors (PerimeterX). The page worked.
- **CORS preflights bypass the block log:**
  - Preflight (`OPTIONS`) requests do not go through `shouldInterceptRequest`, and the WebView API
    has no hook for them.
  - 7 preflights to blocked URLs got a response: 4 on El Tiempo, 1 on The Verge, 2 on Pluto TV.
  - A preflight carries no cookies or body, and the real request after it was blocked.
- **No breakage seen:**
  - Every page loaded with Shields on.
  - The videos on YouTube, Dailymotion and Pluto TV played.
  - The consent banners of El País and ViX were hidden.
- **Cosmetic leak:**
  - On this load Marca showed its "get the app" banner (Google Play) above the page. It was not
    there on the load after 8.2.
  - Cosmetic filtering is not measured here. Check this banner when cosmetic filtering is tested
    again.

Limits:
- One load per site. The Shields-on and Shields-off loads were made at different times, so the
  page and its ads can differ between them.
- Requests made outside the page's DevTools session are not in the recording. The log of every
  site matched the recording, so none were seen.
- The table only counts ads and tracking from the 8.2 classification. The "Blocked" column counts
  everything in the log.

## 5. Performance (roadmap 8.4)

Measured on the reference TV with a **release** build (R8, `opt-level = 3` engine), signed
locally with the debug key so it installs over the debug build. Universal APK: 7.4 MB. Launch was
measured on 2026-11-18; pages and memory on 2026-11-19, on a stable network (0 % packet loss in
20 pings to the TV, 154 ms average). A first page run on 2026-11-18, on unstable Wi-Fi (15–40 %
loss), is replaced by these results.

### How it was measured

- **Launch:** 10 cold starts. Each one: `am force-stop`, then `am start -W`, which gives
  `TotalTime` (until the first frame). The engine load comes from the app's own log line
  (`Engine cache in … ms`). Memory is `dumpsys meminfo` (total PSS) 10 s after launch, on the
  start page.
- **Pages:**
  - Turn on the perf log: `adb shell setprop log.tag.tvshields.perf DEBUG`, then restart the app
    ([architecture §5.5](architecture.md#55-cost-on-the-reference-tv-t7)).
  - Open the site from the address bar, then reload it 5 times with the reload button, 20 s
    apart. `ShieldsPerf` logs each load: load time (page started to page finished) and engine
    work.
  - Turn Shields off for the site in the panel and repeat. With Shields off the engine is not
    asked (`requests n=0`), which confirms the state of every load. Shields are turned back on
    and checked at the end.
  - Memory: total PSS of the app and of the WebView renderer (`sandboxed_process`) after each set
    of 5 loads.

### Launch

10 cold starts; the first one after installing is left out of the medians.

| Measure | Median | Range |
|---|---|---|
| Time to first frame (`TotalTime`) | 1,210 ms | 1,160–1,572 ms (first after install: 2,857 ms) |
| Engine load from the cache | 387 ms | 365–423 ms (first after install: 638 ms) |
| App memory on the start page (PSS) | 92 MB | 90–94 MB |

### Page load time

Median of 5 reloads, range in parentheses.

| Site | Shields on | Shields off | Change with Shields |
|---|---|---|---|
| Wikipedia | 780 ms (696–832) | 794 ms (729–833) | −2 % |
| El País | 4,838 ms (4,628–7,204) | 6,104 ms (5,532–7,194) | −21 % |
| The Verge | 1,996 ms (1,481–2,340) | 1,762 ms (1,705–2,004) | +13 % |
| El Tiempo | 4,700 ms (4,535–5,017) | 13,069 ms (12,167–16,760) | −64 % |

### Engine work per load (Shields on)

| Site | Requests checked | Total time, median (range) | Slowest single request | UI thread |
|---|---|---|---|---|
| Wikipedia | 23 | 13 ms (10–22) | 5.6 ms | 0 ms |
| El País | 29 | 24 ms (12–55) | 19.2 ms | 0 ms |
| The Verge | 56–158 | 50 ms (32–93) | 7.8 ms | 0 ms |
| El Tiempo | 63–73 | 43 ms (42–58) | 5.7 ms | 0 ms |

Cosmetic filtering adds under 1 ms per load on the UI thread and up to 13 ms of generic batches
off it. On The Verge each load also logs two extra page-finished events with no page start; their
requests are counted in the row above.

### Memory with the page open

PSS after 5 loads, app + WebView renderer (1 MB = 1,024 kB).

| Site | Shields on | Shields off |
|---|---|---|
| Wikipedia | 124 + 105 = 229 MB | 125 + 92 = 217 MB |
| El País | 129 + 110 = 239 MB | 119 + 151 = 270 MB |
| The Verge | 136 + 198 = 334 MB | 148 + 301 = 449 MB |
| El Tiempo | 166 + 228 = 394 MB | 151 + 238 = 389 MB |

### Findings

- **The engine's cost is small and never on the UI thread.** It spends 10–93 ms per page in
  total, on WebView's network threads, in parallel with the downloads. The slowest single check
  was 19 ms.
- **Shields make ad-heavy pages load faster.** El Tiempo loads in 4.7 s instead of 13.1 s and El
  País in 4.8 s instead of 6.1 s: blocked ads and trackers are never downloaded or run.
- **Wikipedia** (no ads) loads in the same time with and without Shields.
- **The Verge** is the only site where loads with Shields are slower in the median (+234 ms). The
  ranges overlap and the number of checked requests varies from 56 to 158 between loads (its ads
  change on every load), so 5 loads do not show whether the difference comes from Shields. The
  engine work, 32–93 ms off the UI thread, is smaller than the difference.
- **Memory:** with Shields the WebView renderer uses less memory on the pages with many ads
  (−103 MB on The Verge, −41 MB on El País). The app itself uses 124–166 MB with a page open.
  The largest total, 394 MB on El Tiempo, is about a fifth of the TV's 1.8 GB.
- **Launch:** the first frame shows in about 1.2 s; the engine loads from the cache in about
  0.4 s, in the background.

**Verdict: meets T7.** Blocking is not noticeable while browsing: it never runs on the UI thread,
it costs at most tens of milliseconds per page, and on pages with ads it makes them load faster
and use less memory.

### Limits

- One TV and 5 loads per state. The sites' ads change between loads, so single loads are not
  compared.
- Memory is one PSS reading per state; it varies with garbage collection.
- Load time ends when WebView reports the page finished, not when the page looks complete.
