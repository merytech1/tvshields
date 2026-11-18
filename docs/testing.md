# Testing on the reference TV

Phase 8 checks Shields on real sites, on the reference TV (architecture §10). This file holds the
test sites (roadmap 8.1). Later tasks add their results here: the inventory of ad requests per site
without blocking (8.2), the blocked / leaked comparison (8.3) and the performance measurements (8.4).

## 1. Test sites (roadmap 8.1)

The sites cover the three kinds the roadmap asks for (news, video, streaming) and the three Shields
capabilities (P4). Most are Spanish-language sites from Colombia and Spain, where the reference TV is
used, plus English sites with heavy ad stacks. All are free and legal, and need no account.

Every site was opened on the reference TV with tvshields on 2026-11-18. It loaded, and Shields
blocked requests on it. The counts below are the panel's counter for one load of the home page.
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
| Toolz Ad Block Test | `https://d3ward.github.io/toolz/adblock` | Requests known ad and tracker hosts and gives a score. | Not opened on the TV (see below) |

`adb shell input text` drops `/` with the TV's keyboard, so the Toolz page could not be typed over
`adb`. It loads from a PC on the same network. On the TV, search for "d3ward toolz adblock" and open
the first result.

### Considered and left out

| Site | Reason |
|---|---|
| Twitch | Stays black on the reference TV even with Shields off for the site: a WebView incompatibility, not a Shields problem. Worth retrying after a WebView update. |
| Tubi | Not available in Colombia (redirects to `gdpr.tubi.tv`). |
| Infobae | Timed out from the test network. |
| Forbes | Rejects automated clients (HTTP 403), so REA captures would not be reliable. |
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
