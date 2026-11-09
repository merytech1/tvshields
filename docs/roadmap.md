# Roadmap: phases and tasks

Each task is a unit of work that is done, explained, approved and committed on its own
(rules W1). Status is updated in this file when each task is closed.

Legend: ⬜ pending · 🔄 in progress · ✅ done

## Phase 0 — Foundations

| # | Task | Done when | Status |
|---|---|---|---|
| 0.1 | Create the repo, description and topics | Public repo with an initial commit | ✅ |
| 0.2 | Base documentation: README, rules, architecture, roadmap, resources | Approved and merged into `main` | ✅ |
| 0.3 | Settle pending decisions (license, name, search engine) | Recorded in `rules.md` and `LICENSE` created | ✅ |
| 0.4 | Local toolchain on Windows, installed on `D:`: JDK 17+, Android SDK/NDK, Rust (`windows-gnu`) + Android targets, `cargo-ndk`, `gh` | `cargo ndk --version` and `./gradlew --version` work; versions recorded in `docs/resources/toolchain.md` | ✅ |
| 0.5 | Connect the TV over `adb` (network debugging) | `adb devices` shows the TV; REA lists its packages | ✅ |

## Phase 1 — Native engine

| # | Task | Done when | Status |
|---|---|---|---|
| 1.1 | `engine/` crate wrapping `adblock::Engine` (create, check request, cosmetic, resources, serialize) | `cargo test` covers block, exception, redirect, cosmetic and serialization | ✅ |
| 1.2 | Export JNI functions | Rust tests for the type-conversion layer | ✅ |
| 1.3 | Build for `armeabi-v7a` and `arm64-v8a` with `cargo-ndk` | `.so` files produced and their size recorded (C3) | ✅ |

## Phase 2 — Minimal browser

| # | Task | Done when | Status |
|---|---|---|---|
| 2.0 | UI mockups in Claude Design (shadcn/ui allowed): browser, address bar, Shields panel, settings, search picker | Design system in `docs/design.md` (done); prompts in `docs/mockup-prompts.md` (done); mockups approved, saved in `docs/mockups/` and referenced from `design.md` | ✅ |
| 2.1 | Android project (Kotlin, Compose for TV, MVVM, minSdk 24, Leanback launcher + banner) | The app shows up on the TV home screen and opens a screen | ✅ |
| 2.2 | Full-screen WebView + address bar | Navigates to a URL typed with the TV keyboard | ✅ |
| 2.3 | Virtual cursor and arrow-key scrolling | Links on any page can be clicked using only the remote | ✅ |
| 2.4 | Back, reload, home | Basic navigation fully usable with the remote | ✅ |
| 2.5 | Load the `.so` from Kotlin (JNI smoke test) | An instrumented test calls the engine and gets a result | ✅ |

## Phase 3 — Shields: network blocking (1.1)

| # | Task | Done when | Status |
|---|---|---|---|
| 3.1 | `FilterListRepository`: download the default catalog, storage, ETag | Test with a fake server: downloads, does not re-download if unchanged | ✅ |
| 3.2 | Engine build + `engine.dat` (T8) | The second launch loads from cache; time measured on the TV | ✅ |
| 3.3 | `RequestTypeGuesser` | Unit tests for every rule in architecture §5.1 | ⬜ |
| 3.4 | `shouldInterceptRequest` → engine → block/allow | Test page with known ads: requests blocked | ⬜ |
| 3.5 | First launch and offline case (C1) | Behaves as described in C1 | ⬜ |
| 3.6 | `ListUpdateWorker` every 24 h with atomic swap | Test: update without interrupting ongoing browsing | ⬜ |

## Phase 4 — Shields: cosmetic filtering (1.2)

| # | Task | Done when | Status |
|---|---|---|---|
| 4.1 | Inject `hide_selectors` at document start (with C4 fallback) | Ad gaps disappear on the test pages | ⬜ |
| 4.2 | `cosmetic.js` + `WebMessageListener` + `hidden_class_id_selectors` | Ads that appear after load are hidden too | ⬜ |
| 4.3 | Procedural filters (`procedural_actions`) in our own JS | `:has-text`, `:upward`, etc. rules from the lists work on test cases | ⬜ |
| 4.4 | Measure impact (T7) | No noticeable stutter while browsing on the reference TV | ⬜ |

## Phase 5 — Shields: script replacement (1.3)

| # | Task | Done when | Status |
|---|---|---|---|
| 5.0 | `tools/resources/` generator: uBO (transformed) + Brave → `resources.json`, pinned versions | The resulting JSON loads into `adblock-rust` without errors (Rust test) | ⬜ |
| 5.1 | Download and load `resources.json` (`use_resources`) | The engine recognizes `noopjs`, `set-constant` and the `async-sugarcoat-*` resources | ⬜ |
| 5.2 | Serve `redirect` as the response in `shouldInterceptRequest` | Test with a `redirect=` rule | ⬜ |
| 5.3 | Inject `injected_script` (scriptlets) at document start | A test site with an ad-block detector does not show its warning | ⬜ |

## Phase 6 — Shields UI

| # | Task | Done when | Status |
|---|---|---|---|
| 6.1 | Shields panel: status, per-page counter | The number of blocked items on the current page is visible | ⬜ |
| 6.2 | Per-site toggle (P3, C5) | Turning Shields off for a domain is remembered and reloads without blocking | ⬜ |
| 6.3 | Settings: optional lists, force update, appearance (mode and accent, design §2) | Enabling a list downloads and applies it; mode and accent changes apply immediately and persist | ⬜ |
| 6.4 | "About": licenses and credits (L1, L2) | All licenses and lists are visible | ⬜ |

## Phase 7 — Search

| # | Task | Done when | Status |
|---|---|---|---|
| 7.1 | Text → URL or search resolution | Unit tests for edge cases (`localhost`, IPs, `foo.bar`, phrases) | ⬜ |
| 7.2 | Default search engine picker in Settings (Brave Search, DuckDuckGo, Startpage, Google) | Brave Search on a fresh install; a changed choice persists and applies to the next search | ⬜ |

## Phase 8 — Verification on the real TV (with REA)

| # | Task | Done when | Status |
|---|---|---|---|
| 8.1 | List of representative test sites (news, video, streaming) | Documented in `docs/testing.md` | ⬜ |
| 8.2 | Observe each site with REA (no blocking) to inventory ad requests | Inventory per site | ⬜ |
| 8.3 | Compare against tvshields on the TV (logs over `adb`) | Blocked / leaked table per site | ⬜ |
| 8.4 | Performance: launch time, RAM, time per page | Measurements recorded. Meets T7 | ⬜ |

## Phase 9 — Release

| # | Task | Done when | Status |
|---|---|---|---|
| 9.1 | CI on GitHub Actions: Rust + APK + tests | Green on every PR | ⬜ |
| 9.2 | Release signing (key outside the repo, in GitHub secrets) | Signed APK reproducible from CI | ⬜ |
| 9.3 | Size check in CI (fails if > 100 MB; warns if > 25 MB) | Verified (T1) | ⬜ |
| 9.4 | v0.1.0 on GitHub Releases (universal + per ABI) | Installable on the reference TV from the published file | ⬜ |
| 9.5 | Spanish documentation of the repository (rules W5) | Spanish version of the README and docs published and linked from the English README | ⬜ |
