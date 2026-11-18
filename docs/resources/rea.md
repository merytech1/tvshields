# REA (Reverse Engineer Anything)

- **Repository:** https://github.com/morluto/rea
- **Install:** `npx rea-agents setup` (requires Node.js 22.x/24.x/26.x)
- **License:** MIT
- **Role in tvshields:** an **observation and testing** tool. It is not used to decompile Brave,
  which is open source (rules L3).

## What we use it for

| Need | REA capability | REA guide |
|---|---|---|
| Inventory which ads and trackers a site loads (roadmap 8.2) | Site observation through the Chrome DevTools Protocol: `inspect_web_page`, `observe_web_session` | `docs/browser-observation.md` |
| Compare the same site with and without Shields | `compare_web_captures` | `docs/browser-observation.md` |
| Analyze saved HAR captures | `inspect_web_network_capture` | `docs/web-network-captures.md` |
| Study a site's anti-adblock script to find out which scriptlet is missing | `analyze_web_bundle`, `export_web_scripts` | `docs/website-script-export.md` |
| Inspect the TV: packages, processes, logs, screenshots | `list-adb-devices`, `read-adb-logcat`, `capture-adb-screen`, `inspect-adb-package` | `docs/adb-device-analysis.md` |
| Review our own APK (manifest, size, signature) | `inspect-android-package`, `inspect_apk_signature` | `docs/android-analysis.md` |

## Inspecting the TV's WebView directly

In **debug** builds, tvshields enables `WebView.setWebContentsDebuggingEnabled(true)`. The TV's
WebView then exposes DevTools, and REA can observe it as if it were a local Chrome:

```bash
adb shell cat /proc/net/unix | grep webview_devtools_remote
```

```bash
adb forward tcp:9222 localabstract:webview_devtools_remote_<PID>
```

```bash
rea list-browser-targets http://127.0.0.1:9222
```

This shows, **on the real TV**, which ad requests leak through and which elements remain visible
on each page.

**Limitation (REA 6.8.0, 2026-11-18):** REA's browser commands (`list-browser-targets`,
`observe-web-session`) reject the WebView endpoint. They expect a browser WebSocket of the form
`ws://127.0.0.1:PORT/devtools/browser/<id>`, and the WebView publishes `ws://127.0.0.1:PORT/devtools/browser`
without an id. Until that changes, phase 8 records requests with `tools/capture/requests.mjs`,
which talks to the same forwarded port through the Chrome DevTools Protocol (see
[testing.md](../testing.md#3-ad-inventory-without-blocking-roadmap-82)). REA's adb commands are
not affected.

## adb setup for REA

```bash
export REA_ADB_PATH=/absolute/path/to/adb
```

```bash
rea inspect-adb-client
```

REA does not install adb or the SDK: it uses the platform tools installed in roadmap 0.4.
