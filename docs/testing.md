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
  services into ads, tracking and consent with a hand-made table. The table is independent of the
  filter lists, so 8.3 can compare Shields against it. Third-party requests not in the table (CDNs,
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
