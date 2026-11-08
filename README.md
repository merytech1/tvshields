# tvshields

A lightweight, open-source browser for **Android TV** that blocks ads and trackers
in the style of Brave's *Shields*, built to be used with the TV remote.

> **Status:** design phase. There is no APK yet. See the [roadmap](docs/roadmap.md).

## What it does

| Feature | What it means for the user |
|---|---|
| **Network blocking** | Requests to ad and tracking servers are cut before they download, using filter lists (EasyList, EasyPrivacy, uBlock Origin, Brave). |
| **Cosmetic filtering** | The gaps, empty banners and black screens left by blocked ads disappear, and the content reflows. |
| **Script replacement** | Scripts that detect ad blockers or track the user are neutralized with *scriptlets* and harmless fake resources (including those generated with *SugarCoat*, Brave's technique). |
| **Per-site Shields** | On by default. They can be turned off for a single site if something breaks. Shows a counter of blocked items. |
| **Search** | Configurable search engine from the address bar. |
| **TV remote** | Everything works with the arrows, OK and Back. |

## Where it runs

Any TV or set-top box with **Android 7.0 or later**: Android TV, Google TV, Amazon Fire TV and
Android boxes. It does **not** run on Samsung (Tizen), LG (webOS) or Roku.

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
| [docs/resources/](docs/resources/README.md) | Specific documentation for each external resource we use |

## Disclaimer

tvshields is an independent project. It is not affiliated with Brave Software and does not use
its trademarks. It reuses Brave's open-source components under their licenses.

## License

To be decided (see [rules](docs/rules.md#pending-decisions)).
