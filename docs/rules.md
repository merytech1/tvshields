# Rules and constraints

Every design decision and every line of code must comply with these rules. Each rule has an ID
so it can be cited in issues, commits and reviews (e.g. "complies with T1").

At the end is the **conflict check**: the pairs of rules that could contradict each other and
how each case is resolved. If a new rule clashes with an existing one, it is added there before
anything is implemented.

## 1. Product (P)

| ID | Rule |
|---|---|
| P1 | **General-purpose** browser: blocking must work on any site, not be tuned for one in particular. |
| P2 | **Shields are on by default** after installation. |
| P3 | Shields can be **turned off per site** (domain), and that preference is remembered. |
| P4 | Shields cover three capabilities: **network blocking** (1.1), **cosmetic filtering** (1.2) and **script replacement** (1.3). |
| P5 | The search engine is **configurable**; DuckDuckGo by default. |
| P6 | Everything can be used **with the TV remote only** (arrows, OK, Back). No mouse or touchscreen. |
| P7 | **Own** name and logo. Using "Brave", its lion or its visual identity is forbidden. |

## 2. Technical (T)

| ID | Rule |
|---|---|
| T1 | The APK weighs **at most 100 MB** (hard limit). Target: ≤ 25 MB. |
| T2 | `minSdk 24` (Android 7.0). |
| T3 | Built for **`armeabi-v7a` and `arm64-v8a`**. Many TVs have a 64-bit CPU running 32-bit Android. `x86_64` is for the emulator only, never in the release APK. |
| T4 | Web engine: **Android System WebView**. Neither Chromium nor GeckoView is bundled. |
| T5 | Blocking engine: **unmodified `adblock-rust`**, as a crates.io dependency. If it is ever modified, those changes are published (MPL-2.0 requirement). |
| T6 | **Filter lists are not bundled in the APK**: they are downloaded from their original sources and update automatically. |
| T7 | **Low RAM and CPU usage**: reference TV with ~2 GB of RAM and a mid-range MediaTek SoC. Blocking must not be noticeable while browsing. |
| T8 | The compiled engine is **cached** (`Engine::serialize`) so lists are not reprocessed on every launch. |
| T9 | No request leaves the app other than the user's own browsing and list/resource downloads. **Zero telemetry or analytics.** |
| T10 | Any WebView feature that is not available in every version is **detected at runtime** (`WebViewFeature.isFeatureSupported`) and has a fallback. |

## 3. Legal (L)

| ID | Rule |
|---|---|
| L1 | The MPL-2.0 of `adblock-rust` and `adblock-resources` is respected: license notices are included in the APK and on the "About" screen. |
| L2 | Lists are downloaded at runtime (T6). Each one is credited with its name, author and license on the "About" screen. |
| L3 | No closed-source code is decompiled or copied. Everything we take from Brave is open source. REA is used to **observe** sites and test our own app, not to extract third-party code. |
| L4 | Blocking only acts on the user's own device. No third-party content is redistributed. |

## 4. Process (W)

| ID | Rule |
|---|---|
| W1 | **One task at a time**: it is finished, explained, a commit message is proposed, and approval is awaited. |
| W2 | Commits follow **Conventional Commits** (`feat:`, `fix:`, `docs:`, `chore:`, `test:`, `refactor:`…). |
| W3 | **No `Co-Authored-By`** or AI attribution in commits or PRs. |
| W4 | **`main` is not touched without explicit permission.** Work happens on branches (`feat/…`, `docs/…`) and lands through a PR. |
| W5 | Code, identifiers, file names, **code comments** and **repository documentation in English**. Comments are brief and only where the code doesn't speak for itself. A Spanish version of the documentation is produced at the end of the project (roadmap 9.5). |
| W6 | Every new architecture decision is documented in `docs/` in the same PR that introduces it. |
| W7 | Every task defines a verifiable **definition of done** (see the [roadmap](roadmap.md)). |

## 5. Conflict check

| # | Rules | Potential conflict | Resolution |
|---|---|---|---|
| C1 | T6 × P2 | If lists are downloaded rather than bundled, Shields have no rules on a **first launch without internet**. | The first launch downloads the lists before opening the first page, behind a progress screen. Without a connection, a "Shields have no lists" warning is shown, and pages do not load until lists are available or the user chooses to continue unprotected. |
| C2 | T7 × WebView | `adblock-rust` is faster with the `single-thread` feature, but `shouldInterceptRequest` is called **from several threads**. | Built **without** `single-thread` (the engine is `Send + Sync`), and a single instance is shared across threads. If phase 8 measurements show contention, a reader pool is evaluated. |
| C3 | T1 × T3 | Two ABIs mean two engine `.so` files inside the APK. | Estimated at 3–6 MB per ABI, far below 100 MB. Releases also publish one APK per ABI alongside the universal one. Measured on every release (phase 9). |
| C4 | T2 × T10 | With `minSdk 24`, the WebView may be old and lack `DOCUMENT_START_SCRIPT` or `WEB_MESSAGE_LISTENER`. | Detected at runtime. Without `DOCUMENT_START_SCRIPT`, injection happens in `onPageStarted` (less reliable for 1.3), and Settings suggests updating "Android System WebView". |
| C5 | P3 × T5 | The engine knows nothing about "sites with Shields off". | The per-site toggle is resolved in Kotlin **before** calling the engine; the engine is not modified. |
| C6 | T9 × T6 | Updating lists implies automatic requests. | Explicitly allowed by T9: only to the URLs in the list catalog, and they are shown on the "About" screen. Nothing else leaves without a user action. |
| C7 | P1 × T4 | Some ads (e.g. some YouTube ads) arrive **inside the same video stream** and cannot be cut at the network level. | Not guaranteed in scope: covered only as far as the lists' scriptlets cover it (1.3). Documented as a known limitation, not a bug. |
| C8 | P6 × T4 | The WebView is built for touch/mouse; many pages cannot be used with arrow keys. | A virtual cursor moved with the arrows that generates touch events on the WebView, plus a focus-navigation mode. See [architecture §6](architecture.md#6-tv-remote). |
| C9 | L2 × C1 | Bundling an "emergency" list for the first launch would reopen the licensing problem (GPL / CC BY-SA). | No list is bundled; C1 is solved by the initial download. |
| C10 | T9 × P5 | The search engine receives what is typed in the address bar. | Only when the user searches (explicit action). No live suggestions by default. |
| C11 | L2 × T6 × 1.3 | uBlock Origin scriptlets (GPLv3) are essential for 1.3, but bundling them in the APK would force the app to be licensed under the GPL. | They are not in the APK. A CI generator builds `resources.json` (uBO + Brave) and publishes it as a standalone file on GitHub Releases, with its GPLv3 license and a link to the uBO source. The app downloads it at runtime, like the lists (T6). |

## Pending decisions

Owned by the project lead:

1. **tvshields license.** Options compatible with what we use: MPL-2.0 (same as Brave), MIT or Apache-2.0.
2. **App display name** on the TV. `tvshields` is the repository name.
3. **Confirm DuckDuckGo** as the default search engine (P5).
