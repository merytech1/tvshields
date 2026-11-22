# tvshields

A lightweight, open-source browser for **Android TV** that blocks ads and trackers
in the style of Brave's *Shields*, built to be used with the TV remote.

*[Leer en español](README.es.md)*

> Download the latest version from
> [GitHub Releases](https://github.com/merytech1/tvshields/releases/latest). See the
> [next release plan](#next-release) for upcoming work.

## What it does

| Feature | What it means for the user |
|---|---|
| **Network blocking** | Requests to ad and tracking servers are cut before they download, using filter lists (EasyList, EasyPrivacy, uBlock Origin, Brave). |
| **Cosmetic filtering** | The gaps, empty banners and black screens left by blocked ads disappear, and the content reflows. |
| **Script replacement** | Scripts that detect ad blockers or track the user are neutralized with *scriptlets* and harmless fake resources (including those generated with *SugarCoat*, Brave's technique). |
| **Per-site Shields** | On by default. They can be turned off for a single site if something breaks. Shows a counter of blocked items. |
| **Search** | Brave Search by default; DuckDuckGo, Startpage or Google can be chosen as the default in Settings. |
| **TV remote** | Everything works with the arrows, OK and Back. |

## Where it runs

Any TV or set-top box with **Android 7.0 or later**: Android TV, Google TV, Amazon Fire TV and
Android boxes. It does **not** run on Samsung (Tizen), LG (webOS) or Roku.

## Download and install

The latest version is on [GitHub Releases](https://github.com/merytech1/tvshields/releases/latest):
a universal APK, plus smaller ones for 32-bit and 64-bit devices; use the universal one if you are
not sure. Install it with the **Downloader** app using this URL, which always downloads the latest
version:

```
https://github.com/merytech1/tvshields/releases/latest/download/tvshields.apk
```

From a USB drive with a file manager, install the APK you downloaded; or use
`adb install <file>.apk`. The device must allow installing apps from that source, and the first
launch needs internet to download the filter lists. Checksums, the certificate and more detail are
in the [v0.1.1 release notes](docs/releases/v0.1.1.md).

## Next release

These phases continue the numbering from the [project roadmap](docs/roadmap.md).

| Phase | Task | Done when |
|---|---|---|
| 10 · Downloads and updates on the TV | **10.1** Download files from web pages and show download progress. | A file downloaded with the remote appears in the download list and can be opened from the TV. |
| 10 · Downloads and updates on the TV | **10.2** Add a manual update check in Settings using GitHub Releases. | Settings shows the installed version and offers the official signed APK when a newer version is available. |
| 10 · Downloads and updates on the TV | **10.3** Download the update and open the Android installer. | The user confirms the update on the TV and tvshields opens with the new version and its previous settings. |
| 11 · Tabs and windows | **11.1** Open, switch and close tabs with the remote. | Each tab keeps its page and navigation state while the user switches between tabs. |
| 11 · Tabs and windows | **11.2** Handle links that request a new browser window. | A link opens in a separate browsing view and the user can return to the original page. |
| 12 · Favorites | **12.1** Add or remove the current page as a favorite. | The remote can save or remove a page and the favorites list reflects the change. |
| 12 · Favorites | **12.2** Reopen saved pages from the favorites list. | Favorites remain after reopening the app and can be opened with the remote. |
| 13 · History | **13.1** Record visited pages on the device and display them in order of most recent visit. | The user can reopen a recent page from the history list. |
| 13 · History | **13.2** Delete individual entries or clear the entire history. | Removed pages stay out of the list after reopening the app. |
| 14 · Languages | **14.1** Translate the interface into English, Spanish, French, Portuguese and German. | The browser, its new features and Settings display text in all five languages. |
| 14 · Languages | **14.2** Add a language selector in Settings. | The user can switch languages with the remote, and the chosen language persists after reopening the app. |

## Tech stack

| Layer | Technology |
|---|---|
| App | Kotlin, Android SDK (minSdk 24), Jetpack Compose for TV |
| Web engine | Android System WebView (system Chromium) + `androidx.webkit` |
| Blocking engine | Brave's [`adblock-rust`](https://github.com/brave/adblock-rust) (MPL-2.0), compiled to a native library |
| Bridge | Rust ↔ Kotlin via JNI (`jni` crate), built with `cargo-ndk` |
| Replacement resources | [`brave/adblock-resources`](https://github.com/brave/adblock-resources) (MPL-2.0, includes SugarCoat) + [uBlock Origin](https://github.com/gorhill/uBlock) scriptlets (GPLv3, downloaded separately) |
| Filter lists | Brave's catalog: uBlock Origin, EasyList, EasyPrivacy, Brave, Fanboy (downloaded at runtime) |
| On-device testing | [REA](https://github.com/morluto/rea) + `adb` |

Details in [architecture](docs/architecture.md).

## Documentation

| Document | Contents |
|---|---|
| [docs/rules.md](docs/rules.md) | Project rules and constraints, and the check that they don't conflict |
| [docs/architecture.md](docs/architecture.md) | Evaluated approaches, architecture, modules, flows and design |
| [docs/roadmap.md](docs/roadmap.md) | Phases and tasks, each with a definition of done |
| [docs/design.md](docs/design.md) | Design system: philosophy, themes, color, typography, focus and components |
| [docs/testing.md](docs/testing.md) | Test sites and how Shields are checked on the reference TV |
| [docs/resources/](docs/resources/README.md) | Specific documentation for each external resource we use |

## Disclaimer

tvshields is an independent project. It is not affiliated with Brave Software and does not use
its trademarks. It reuses Brave's open-source components under their licenses.

## License

[MPL-2.0](LICENSE).
