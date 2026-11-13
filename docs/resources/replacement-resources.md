# Replacement resources (scriptlets, redirects and SugarCoat)

These are the foundation of feature 1.3. Lists only **name** a resource (`redirect=noopjs`,
`+js(set-constant, …)`). For the engine to serve or inject it, its content must be loaded with
`engine.use_resources(...)`.

## SugarCoat

SugarCoat is a research technique from Brave, published in the paper *"SugarCoat: Programmatically
Generating Privacy-Preserving, Web-Compatible Resource Replacements for Content Blocking"*
(ACM CCS 2021). It targets tracking scripts that **cannot be blocked** because the page breaks
without them. It analyzes which sensitive APIs the script touches (cookies, storage, etc.) and
automatically generates a **rewritten version** that keeps working but receives fake data from
those APIs.

In practice it ships as `async-sugarcoat-<hash>.js` resources inside `brave/adblock-resources`,
enabled by the `brave-sugarcoat.txt` list. To the engine it is just another `redirect=`:

```
||google-analytics.com/analytics.js$script,important,domain=house.gov,redirect=async-sugarcoat-8a459c…
```

**Actual scope (2026-11-07):** `brave-sugarcoat.txt` has only **2 active rules** (dell.com and
house.gov). The heavy lifting against **ad-block detectors** is not done by SugarCoat but by uBlock
Origin **scriptlets** (`set-constant`, `abort-on-property-read`, `json-prune`…), hand-written by the
list maintainers. Both are loaded into the engine the same way.

## Sources

### 1. `brave/adblock-resources` (MPL-2.0)

- https://github.com/brave/adblock-resources
- `dist/resources.json`: **17 resources** (2026-11-07), already in the `Resource` format the engine
  expects (`name`, `aliases`, `kind`, base64 `content`). Includes:
  - `async-sugarcoat-*.js` (SugarCoat),
  - YouTube fixes (`brave-youtube-*`, `brave-yt-sabr-fix.js`),
  - `de-amp.js`, `trusted_types.js`, `vaft-ublock-origin.js`, `video-swap-new-ublock-origin.js`, etc.
- Does **not** include the standard scriptlets.

### 2. uBlock Origin (GPLv3)

- https://github.com/gorhill/uBlock
- `src/js/resources/`: scriptlets (`set-constant`, `abort-on-property-read`, `json-prune`,
  `no-setTimeout-if`…). They have been **ES modules** for years.
- `src/web_accessible_resources/` + `redirect-resources.js`: redirects (`noopjs`, `noop.txt`,
  `1x1.gif`, `noopmp4-1s`, `google-ima`…).
- `adblock-rust` can only parse the **old** scriptlet format. Its documentation recommends
  transforming the new format into `Resource` **using JavaScript**.

## `tools/resources/` generator

Node.js (no dependencies), run by the `resources` workflow (`.github/workflows/resources.yml`) on
pull requests that touch it and on demand (roadmap 5.0):

1. `generate.mjs` checks out uBlock Origin and `adblock-resources` at the commits pinned in
   `tools/resources/sources.json` (shallow fetch, cached in `tools/resources/.cache/`).
2. It imports uBO's `src/js/resources/scriptlets.js` (`builtinScriptlets`) and
   `src/js/redirect-resources.js`, and `convert.mjs` turns them into `Resource` entries (format
   below). The redirect files come from `src/web_accessible_resources/`; entries with `params`
   (`click2load.html`) are skipped because they need uBO's extension pages.
3. The groups are merged in priority order: Brave's `dist/resources.json`, then uBO scriptlets,
   then uBO redirects. A name already taken drops the later resource and a taken alias is removed
   (the engine rejects duplicates); the generator prints what it dropped. Every `dependencies`
   entry must name an existing resource.
4. Output in `tools/resources/dist/` (not versioned): `resources.json` and
   `LICENSE-resources.txt` (source commits, GPLv3 and MPL-2.0 texts).
5. `engine/tests/generated_resources.rs` loads the file into adblock-rust: every resource must be
   accepted, and `noopjs`, `set-constant` (with its helpers), a SugarCoat redirect and the trusted
   scriptlet restriction are checked. These tests are `#[ignore]`d in a plain `cargo test`.
6. When started by hand with *publish*, the workflow uploads both files to the `resources`
   release, so the app has a stable URL (roadmap 5.1):
   `https://github.com/merytech1/tvshields/releases/download/resources/resources.json`.

`node --test` in `tools/resources` tests `convert.mjs` with its own fixtures. Running
`generate.mjs` executes uBO code: only at the pinned commits, and only in CI or a disposable
checkout. Updating a source means changing its commit in `sources.json` in a pull request, so the
workflow checks the new version before it can be published.

Why this way: uBO resources are GPLv3. Published as a **separate file** that the app downloads,
they do not force the app to be licensed under the GPL (rules C11).

## `Resource` format

uBO scriptlets are converted to the **function style** adblock-rust 0.13 understands. The content
is the function's own source; the engine appends the helpers listed in `dependencies` and calls
the function with the filter's arguments as JSON strings:

```json
{
  "name": "set-constant.js",
  "aliases": ["set.js"],
  "kind": "template",
  "content": "<base64 of `function setConstant(...) {...}`>",
  "dependencies": ["set-constant.fn"]
}
```

Helpers have their own `dependencies` (`set-constant.fn` needs `safe-self.fn`, …); the engine
resolves them recursively.

| uBO entry | `kind` | Notes |
|---|---|---|
| Scriptlet (`*.js`) | `"template"` | Injection only, never a redirect |
| Helper (`*.fn`) | `{"mime": "fn/javascript"}` | Only reachable through `dependencies` |
| Redirect file | `{"mime": ...}` from the extension (`empty` → `text/plain`) | Usable as `redirect=` and, for JS, in `+js()` |

Brave's resources keep their own format (`{"mime": "application/javascript"}`, no arguments).

**Trusted scriptlets.** uBO marks 34 scriptlets (`trusted-set-cookie`, `trusted-set-attr`, …) and
the `google-ima-dai.js` redirect with `requiresTrust`. They get `"permission": 1`: adblock-rust only
injects them for filters from a list parsed with that permission bit (roadmap 5.1 decides which
lists are trusted), and a resource with a permission is never served as a redirect.

**What the injection must provide (roadmap 5.3).** uBO helpers read a `scriptletGlobals` object
that uBO's own wrapper defines (`safeSelf` reads `scriptletGlobals.bcSecret`). Without it every
scriptlet throws, and the engine's `try` blocks hide the error. The injected script must
therefore be wrapped as `(function () { const scriptletGlobals = {}; … })();`. uBO also runs
22 scriptlets (cookie, `localStorage`, `href-sanitizer`…) in an isolated world; a WebView
document-start script only has the page's world, so they run there.
