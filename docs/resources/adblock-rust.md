# adblock-rust

- **What it is:** the engine behind Brave's Shields, published as a standalone library.
- **Repository:** https://github.com/brave/adblock-rust
- **Crate:** [`adblock`](https://crates.io/crates/adblock), version **0.13.5** (2026-10-28). Docs: https://docs.rs/adblock
- **License:** MPL-2.0. It is per-file, so it can be used in an app under a different license. If we
  modify its files, those changes are published (rules T5, L1).

## What it does

Network blocking, cosmetic filtering, resource replacement, *hosts* syntax and uBlock Origin syntax
extensions. It compiles to native code, so it runs on Android through the NDK.

## API we use

| Function | Purpose | Shields |
|---|---|---|
| `FilterSet::new(debug)` + `add_filter_list(text, ParseOptions::default())` | Combine the downloaded lists | — |
| `Engine::new_with_filter_set(set)` | Build the engine | — |
| `engine.check_network_request(&Request::new(url, source_url, request_type, method))` → `BlockerResult` | Decide on each request | 1.1 |
| `BlockerResult::should_block()` / `.redirect` / `.rewritten_url` | Block, serve a fake resource, or rewrite the URL | 1.1, 1.3 |
| `engine.url_cosmetic_resources(url)` → `UrlSpecificResources` | Selectors to hide, procedural filters, exceptions, scriptlets and `generichide` | 1.2, 1.3 |
| `engine.hidden_class_id_selectors(classes, ids, exceptions)` | Generic cosmetic filtering on new class/id values | 1.2 |
| `engine.use_resources(resources)` | Load scriptlets and redirects | 1.3 |
| `engine.serialize()` / `engine.deserialize(bytes)` | Cache the compiled engine | T8 |
| `engine.enable_tags` / `disable_tags` | Enable optional rules by tag | future |

Minimal example (from the official repo):

```rust
use adblock::{Engine, lists::{FilterSet, ParseOptions}, request::Request};

let mut filter_set = FilterSet::new(false);
filter_set.add_filter_list(&rules, ParseOptions::default());
let engine = Engine::new_with_filter_set(filter_set);

let request = Request::new(
    "http://example.com/-advertisement-icon.",
    "http://example.com/helloworld",
    "image",
    "",
).unwrap();
let result = engine.check_network_request(&request);
```

## Cargo features

| Feature | Default | Our choice |
|---|---|---|
| `embedded-domain-resolver` | yes | **yes**: built-in domain resolution |
| `full-regex-handling` | yes | **yes** |
| `single-thread` | yes | **no**: the engine must be `Send + Sync` because the WebView calls in from several threads (rules C2) |
| `css-validation` | no | no (larger binary) |
| `content-blocking` | no | no (iOS only) |
| `resource-assembler` | no | no: the resource generator is written in JS (see [replacement-resources.md](replacement-resources.md)) |
| `debug-info` | no | debug builds only |

```toml
[dependencies]
adblock = { version = "0.13", default-features = false, features = ["embedded-domain-resolver", "full-regex-handling"] }
```

## Limitations and notes

- `procedural_actions` arrives as JSON: the engine does **not** run procedural filters. They must be
  implemented in JS inside the page (roadmap 4.3).
- `get_csp_directives` exists, but we cannot inject CSP headers into the WebView's main document.
  Out of scope.
- The `serialize` format changes between crate versions: `engine.dat` stores the version and is
  regenerated on mismatch.
