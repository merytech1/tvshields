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

A Node.js script that runs **in CI only** (roadmap 5.0):

1. Clones uBlock Origin and `adblock-resources` at **pinned commits**.
2. Loads the uBO scriptlet modules and, for each one, produces a `Resource` with `kind: "template"`,
   its aliases and its code, plus the `web_accessible_resources` files with their MIME type.
3. Merges the result with Brave's `dist/resources.json`. On a name collision, Brave wins.
4. Validates the JSON by loading it into `adblock-rust` (Rust test).
5. Publishes `resources.json` as a GitHub Releases asset, together with `LICENSE-resources.txt`
   (GPLv3 + MPL-2.0) and links to the source commits.

Why this way: uBO resources are GPLv3. Published as a **separate file** that the app downloads,
they do not force the app to be licensed under the GPL (rules C11).

## `Resource` format

```json
{
  "name": "set-constant.js",
  "aliases": ["set-constant"],
  "kind": "template",
  "content": "<base64 of the code with {{1}}, {{2}}…>"
}
```

`kind` is `"template"` for parameterized scriptlets, or `{"mime": "application/javascript"}` for
redirects.
