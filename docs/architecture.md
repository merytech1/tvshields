# Architecture and design

## 1. Intent

A general-purpose browser for Android TVs whose reason to exist is **Shields**: browsing without
ads or trackers, just like in Brave, on a low-resource TV and using only the remote.

**Success** = opening ad-heavy sites on the reference TV and seeing no ads, no gaps and no
"disable your ad blocker" prompts, without browsing feeling slower than the browser used today.

### Goals
- Shields 1.1, 1.2 and 1.3 equivalent to Brave's, with the same engine and the same lists.
- Small APK (≤ 100 MB, target ≤ 25 MB) installable on any Android 7+ TV.
- Comfortable to use with the TV remote.

### Non-goals
- Sync, crypto wallet, Brave Rewards, VPN, Tor, AI.
- Running on Tizen, webOS or Roku.
- Guaranteed blocking of ads stitched into the video stream itself (see [rules C7](rules.md)).
- Third-party extensions.

### Anti-goals (failure even if it "works")
- The TV stutters or browsing feels slow because of blocking.
- The app sends browsing data to any server (T9).
- Using the Brave brand (P7).

## 2. Evaluated approaches

| | A. WebView + `adblock-rust` ✅ **chosen** | B. GeckoView + uBlock Origin | C. Chromium / brave-core fork |
|---|---|---|---|
| Idea | System WebView; Brave's engine filters every request and injects CSS/scripts | Embedded Firefox engine with uBO as an extension | Build Brave's browser for TV |
| APK size | ~15–25 MB | ~80 MB | > 150 MB |
| RAM | Low (reuses the WebView already loaded by the system) | Medium-high | High |
| Fidelity to Brave | Same engine and same lists | Same capabilities, different engine | Total |
| Own code | More (JNI bridge, integration) | Less | Huge (Chromium patches) |
| License imposed | None (MPL is per-file) | GPLv3 for the whole project | BSD/MPL |
| Main risk | Depends on the TV's WebView version | Weight on 2 GB TVs | Build: > 100 GB of disk and hours |

**Why A:** it is the only option that comfortably meets the lightness requirement (T1, T7), and it
uses the actual engine behind Brave's Shields. B stays as a fallback plan should the WebView turn
out to be unworkable. C is discarded on cost.

## 3. Overview

```
┌──────────────────────────── tvshields APK ────────────────────────────┐
│                                                                       │
│  ui/            Compose for TV: browser, address bar, Shields panel,  │
│                 settings, virtual cursor                              │
│     │                                                                 │
│  browser/       TvWebView, ShieldsWebViewClient, tabs                 │
│     │  shouldInterceptRequest / onPageStarted / WebMessageListener    │
│     ▼                                                                 │
│  shields/       ShieldsManager ── SiteSettings (per domain)           │
│     │           RequestTypeGuesser, CosmeticInjector, Stats           │
│     │  JNI                                                            │
│     ▼                                                                 │
│  libtvshields_engine.so  (Rust)                                       │
│     └── adblock-rust  Engine                                          │
│                                                                       │
│  lists/         FilterListRepository, ListUpdateWorker                │
│                 (download → compile → serialize → reload)             │
│  search/        SearchEngines, text → URL resolution                  │
│  assets/js/     own scripts (generic cosmetic, procedural filters)    │
└───────────────────────────────────────────────────────────────────────┘
        ▲ internal storage: list .txt files, resources.json,
        │ engine.dat (serialized), settings (DataStore)
```

### 3.1 Architectural pattern

A **modular monolith**: one APK, one process, no backend. The only network dependencies are
static files (filter lists, `resources.json`) and the user's own browsing.

Inside the app, **MVVM with layers**, as recommended by Google for Android:

```
UI          Compose for TV screens. Render state, forward user events.
  ↕ observes StateFlow
ViewModel   Screen state and user actions. No Android View or WebView references.
  ↕
Domain      ShieldsManager, SearchResolver, RequestTypeGuesser: project rules, plain Kotlin.
  ↕
Data        FilterListRepository, SettingsRepository (DataStore), AdblockEngine (JNI).
```

- **Single Activity.** Screens are composables with Navigation Compose, not separate Activities.
- **Packages by feature** (`browser/`, `shields/`, `lists/`, `search/`, `settings/`), each with its
  own layers inside.
- **The native engine sits behind a Kotlin interface** (`AdblockEngine`). `NativeAdblockEngine`
  is the JNI implementation; tests use a fake, so domain logic is testable without the `.so`.
- **Exception: the WebView hot path.** `shouldInterceptRequest` calls `ShieldsManager` directly,
  without going through a ViewModel, because it runs on background threads for every request.
  ViewModels only receive aggregated results (e.g. the blocked counter).
- **Dependency injection by hand** (an `AppContainer` created in `Application`). No Hilt/Dagger,
  to keep the APK and the build light (T1, T7).
- **No Docker.** The app runs on Android, and the build uses the local toolchain (roadmap 0.4)
  or GitHub Actions runners.

## 4. Repository layout

```
tvshields/
├── README.md
├── docs/
│   ├── rules.md
│   ├── architecture.md
│   ├── roadmap.md
│   └── resources/             # one page per external resource
├── engine/                    # Rust crate: tvshields-engine
│   ├── Cargo.toml
│   └── src/
│       ├── lib.rs             # exported JNI functions
│       └── engine.rs          # wrapper around adblock::Engine
├── tools/
│   └── resources/             # resources.json generator (Node.js, CI only)
├── app/                       # Android module
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/js/
│       ├── jniLibs/           # generated by cargo-ndk (not versioned)
│       └── java/…/tvshields/
│           ├── ui/
│           ├── browser/
│           ├── shields/
│           ├── lists/
│           ├── search/
│           └── settings/
├── settings.gradle.kts
└── .github/workflows/         # CI: Rust + APK build, tests, size check
```

## 5. Shields: flows

### 5.1 Network blocking (1.1)

```
WebView requests a resource
   │
   ▼
ShieldsWebViewClient.shouldInterceptRequest(view, request)   [background thread]
   │
   ├─ Shields off for the page's domain? ──► null (continue normally)
   │
   ├─ type = RequestTypeGuesser(request)        # "document", "script", "image", …
   ├─ source = URL of this WebView's main document
   │
   ▼  JNI
engine.check_network_request(Request::new(url, source, type, method))
   │
   ├─ redirect = Some(data:…)    ──► WebResourceResponse with that content  (1.3)
   ├─ should_block()             ──► empty WebResourceResponse (204) + Stats++
   └─ otherwise                  ──► null
```

**Request type.** The WebView does not report it. `RequestTypeGuesser` infers it, in this order:
1. `request.isForMainFrame` → `document`.
2. `Accept` header: `text/css` → `stylesheet`, `image/*` → `image`, `text/html` → `sub_frame`.
3. URL extension: `.js`/`.mjs` → `script`, `.css`, images, `.mp4/.m3u8/.mpd/.ts` → `media`, fonts → `font`.
4. If nothing matches → `other`.

Known limitation: XHR/fetch cannot be reliably distinguished (`xmlhttprequest` → `other`).

**Source URL (`source`).** The URL of the main document, recorded in
`onPageStarted`/`doUpdateVisitedHistory`. Iframe resources are evaluated against the main
page (accepted limitation).

### 5.2 Cosmetic filtering (1.2)

Two parts, as in Brave:

**a) URL-specific**, at the start of every page:
```
engine.url_cosmetic_resources(url) → UrlSpecificResources {
    hide_selectors,       # → <style>sel1, sel2 { display:none !important }</style>
    procedural_actions,   # → JSON for our own procedural script (assets/js)
    exceptions,           # → kept for part (b)
    injected_script,      # → 1.3
    generichide,          # → if true, disables part (b)
}
```
Injected with `WebViewCompat.addDocumentStartJavaScript` (before any page code runs).
Fallback if unavailable: `evaluateJavascript` in `onPageStarted` (rules C4).

**b) Generic**, as the page changes:
```
assets/js/cosmetic.js (in the page)
   MutationObserver collects new class and id values
   │  postMessage (WebMessageListener, not addJavascriptInterface)
   ▼
CosmeticInjector → engine.hidden_class_id_selectors(classes, ids, exceptions)
   │
   ▼
injects a <style> with the returned selectors
```
Batched (debounce ~100 ms) to avoid overloading the TV (T7).

**Security of the JS → Kotlin channel.** `WebMessageListener` is used because it allows
validating the message origin. Messages only accept lists of short strings (class/id) with a
size cap. Anything else is dropped.

### 5.3 Script replacement (1.3)

Two engine mechanisms:

| Mechanism | Typical rule | What we do |
|---|---|---|
| **Redirect** | `||ads.example/ad.js$script,redirect=noopjs` | The engine returns the fake resource (`BlockerResult.redirect`) and we serve it as the response. |
| **Scriptlet** | `example.com##+js(set-constant, adBlockDetected, false)` | Arrives in `injected_script` and is injected at document start. |

**Where resources come from** (rules only name them; the engine needs their content):

| Source | Contents | License | Format |
|---|---|---|---|
| `brave/adblock-resources` → `dist/resources.json` | 17 Brave-specific resources, including the `async-sugarcoat-*.js` files (actual SugarCoat output) and YouTube fixes | MPL-2.0 | Already a `Vec<Resource>` in JSON |
| uBlock Origin (`gorhill/uBlock`: `src/js/resources/`, `src/web_accessible_resources/`) | All the standard scriptlets and redirects the lists use | GPLv3 | ES modules: **must be transformed** into `Resource` with a JS script (as `adblock-rust` recommends) |

That is why the project includes a **resource generator** (`tools/resources/`, Node.js) that
runs in CI. It fetches both sources at pinned versions, transforms the uBO resources, merges
them and publishes a single `resources.json` as a GitHub Releases asset. The app downloads it
together with the lists and loads it with `engine.use_resources(...)`. See [rules C11](rules.md).

Anti-adblock detection is handled mostly by uBO scriptlets. SugarCoat covers a few tracking
scripts that cannot be blocked without breaking the page. See
[resources/replacement-resources.md](resources/replacement-resources.md).

### 5.4 List lifecycle

```
Launch
  ├─ compatible engine.dat exists? ──yes──► Engine::deserialize (fast)
  └─ no ──► download lists + resources ──► FilterSet ──► Engine::new_with_filter_set
                                           └─► serialize → engine.dat

ListUpdateWorker (WorkManager, every 24 h, requires network)
  download with ETag/If-Modified-Since ──► if changed: recompile in the background
  ──► atomic instance swap ──► serialize
```

**Default lists** (those Brave enables by default, taken from its `list_catalog.json`):
Brave Default Adblock Filters (uBO + EasyList + Brave lists incl. SugarCoat), Brave Default
Privacy Filters (EasyPrivacy + uBO privacy), Brave First Party Filters, Cookie notice blocker,
Mobile app promo blocker. Regional and "annoyances" lists are optional in Settings. Details in
[resources/filter-lists.md](resources/filter-lists.md).

## 6. TV remote

- **Virtual cursor**: an overlay on top of the WebView draws a pointer. The arrows move it (with
  acceleration) and OK generates a touch `MotionEvent` at that position. At the edges, the cursor
  scrolls the page.
  - Keys reach the cursor through `TvWebView.dispatchKeyEvent`: the WebView consumes arrows
    before any `OnKeyListener`.
  - Each press moves the cursor a fixed step (16 px), so short taps aim precisely; held keys
    accelerate. A press against an edge scrolls 160 px.
  - Scrolling sends mouse-wheel `MotionEvent`s (`ACTION_SCROLL`), which also scroll inner panels.
  - Pushing up when the page cannot scroll further up goes to the address bar; Down goes back
    to the page.
  - The cursor stays a pointer's size away from the right and bottom edges, so it never hides
    off screen.
  - OK goes to the page as a tap on release. A held OK (the remote repeats it) goes to the address
    bar. The press duration alone is not enough: `adb input keyevent --longpress` reports a zero
    duration.
- **Address bar focus**: focus only shows the URL (read-only, no text input session). OK starts
  editing and opens the keyboard. Otherwise the system keyboard reappears every time the bar gets
  focus from the page. When editing starts, the URL is selected again after a short delay, because
  Gboard moves the cursor to the end at session start and typing would append to the URL.
  - The movement logic (`CursorController`) is plain Kotlin with unit tests.
- **Shortcuts**: Back = previous page (or exit the current mode), Menu/long-press OK = address bar
  and Shields panel.
- **On-screen keyboard**: the system IME of the TV, for the address bar.
- All of our own UI (Compose for TV) is focus-navigable.

## 7. Search

Brave Search is the default (P5). In Settings the user can pick a different default
search engine. Each provider is a name, an icon and a URL template:

| Provider | Template | Results come from | |
|---|---|---|---|
| Brave Search | `https://search.brave.com/search?q=%s` | Own index | **default** |
| DuckDuckGo | `https://duckduckgo.com/?q=%s` | Mostly Bing | selectable |
| Startpage | `https://www.startpage.com/do/search?q=%s` | Google, without tracking | selectable |
| Google | `https://www.google.com/search?q=%s` | Google | selectable |

`search/` resolves what is typed in the address bar:
- If it looks like a URL (`domain.tld`, `http…`) → it is opened.
- Otherwise → the chosen provider's template.

No live suggestions (rules C10).

## 8. Stored data

| Data | Where |
|---|---|
| General settings, chosen search provider, sites with Shields off | Jetpack DataStore |
| Downloaded lists, `resources.json` | `filesDir/lists/` |
| Serialized engine | `filesDir/engine.dat` (includes the crate version; regenerated if it changes) |
| Block counters | In memory per tab. The cumulative total goes to DataStore. |

No synced history and no accounts.

## 9. Technologies

| Piece | Choice | Why |
|---|---|---|
| App language | Kotlin | Android standard |
| UI | Jetpack Compose for TV (`androidx.tv`) | Focus-aware components for the remote |
| Web | Android System WebView + `androidx.webkit` | Lightweight (T4). Provides `addDocumentStartJavaScript` and `WebMessageListener` |
| Blocking engine | `adblock` (crates.io), version 0.13.x | Brave's engine (T5) |
| Crate features | `default-features = false` + `embedded-domain-resolver`, `full-regex-handling` (i.e. the defaults **minus** `single-thread`) | Threads (C2). `resource-assembler` is not needed: resources arrive as JSON |
| Bridge | `jni` crate (hand-written JNI) | Per-request calls: direct JNI is faster than JNA/UniFFI |
| Native build | `cargo-ndk` + Android NDK | Produces `jniLibs/<abi>/*.so` |
| Periodic tasks | WorkManager | List updates |
| Settings | Jetpack DataStore | Simple and asynchronous |
| Tests | JUnit + Robolectric (Kotlin), `cargo test` (Rust), instrumented tests on the reference TV | |
| Real-device verification | REA + `adb` on the reference TV | See [resources/rea.md](resources/rea.md) |
| CI | GitHub Actions | Build, tests and APK size check (T1) |

## 10. Reference device

JVC "COL 4K SMART TV" (manufactured by KTC), Android TV 11, MediaTek SoC, November 2023
security patch. All performance measurements (T7) are taken on it.

Read over `adb` on 2026-11-08 (roadmap 0.5):

| Property | Value |
|---|---|
| Product / device | `KTC_JVC_4K_COL_21V2` / `zhongshan` |
| Android | 11 (API 30), build `RTM7.230903.080`, security patch 2023-11-01 |
| SoC | MediaTek `m7632`, 4 × Cortex-A55 |
| ABI | **`armeabi-v7a` only** (32-bit kernel and userspace; no `arm64-v8a`) |
| RAM | 1.8 GB total, ~730 MB available at idle |
| Free storage | ~770 MB on `/data` (4 GB partition) |
| UI resolution | 1920×1080 at 320 dpi (`xhdpi`) |
| WebView | `com.google.android.webview` 156.0.8078.47 (updated through Play Store) |
| Features | `leanback`, `leanback_only`, `type.television`; no touchscreen |

Consequences: the `armeabi-v7a` build is the one tested on this TV; the tight free storage makes
the APK size goal (T1) and the size of cached lists matter.

Quirk: the WebView paints its background over Compose content laid out *before* it, even outside
its own bounds. UI that sits next to the WebView (the top bar) is drawn after it (`zIndex`).

Connection: enable *Developer options → USB debugging* on the TV, then
`adb connect <TV IP>:5555` and accept the authorization prompt on screen.

## 11. Left to the implementer

- Internal class and package names, as long as they follow the layout in §4.
- The cursor acceleration curve and the exact debounce timings.
- Implementation details of the UI, once the mockups (§12) are approved.

## 12. UI design

The UI is **our own**: the WebView only renders web pages, and everything around it (address bar,
Shields panel, settings, cursor) is designed and built by the project. Brave's UI is not copied
(P7) and Brave has no TV UI anyway; its *organization* (a shield icon next to the address bar that
opens a panel with the counter and the toggle) is a reference.

**Design system:** [design.md](design.md) defines the philosophy, themes (dark and light), the
purple accent, typography, spacing, focus, motion, icons, components and copy. It is the source
of truth for every mockup and screen.

**Process** (roadmap 2.0):
1. Mockups are made in **Claude Design** before any UI code, using the tokens in
   [design.md](design.md). **shadcn/ui** may be used there as the component base for the mockups.
2. The approved mockups (screens) are added to [design.md](design.md).
3. The UI is then implemented in **Compose for TV** following those mockups. shadcn/ui is a web
   (React) library: it is a visual reference only and never ships in the APK.

**TV constraints the mockups must meet:**
- Readable from 3 m away: text ≥ 18 sp.
- An always-visible focus indicator; every element reachable with the arrows.
- ~5 % overscan margin at the edges.
- 1920×1080 layout (the TV renders the UI at 1080p and scales it to 4K).
- No Brave branding (P7).
