//! Checks the `resources.json` built by `tools/resources` (roadmap 5.0). Needs the generated file,
//! so it is ignored by default; CI runs it right after the generator:
//!
//! `cargo test --test generated_resources -- --ignored`
//!
//! The path comes from `TVSHIELDS_RESOURCES`, or defaults to `tools/resources/dist/resources.json`.

use std::path::PathBuf;

use tvshields_engine::{NetworkDecision, ShieldsEngine};

const PAGE: &str = "https://news.example.com/article";

fn resources_json() -> String {
    let path = std::env::var_os("TVSHIELDS_RESOURCES").map(PathBuf::from).unwrap_or_else(|| {
        PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../tools/resources/dist/resources.json")
    });
    std::fs::read_to_string(&path).unwrap_or_else(|e| {
        panic!("cannot read {}: {e} (run tools/resources first)", path.display())
    })
}

fn names(json: &str) -> Vec<String> {
    let resources: Vec<serde_json::Value> = serde_json::from_str(json).unwrap();
    resources.iter().map(|r| r["name"].as_str().unwrap().to_string()).collect()
}

fn engine_with_resources(rules: &str, json: &str) -> ShieldsEngine {
    let mut engine = ShieldsEngine::from_lists([rules]);
    engine.use_resources_json(json).unwrap();
    engine
}

#[test]
#[ignore = "needs the generated resources.json"]
fn every_resource_is_accepted() {
    let json = resources_json();
    let total = names(&json).len();
    let mut engine = ShieldsEngine::from_lists([""]);
    assert_eq!(engine.use_resources_json(&json).unwrap(), total);
    assert!(total > 150, "only {total} resources");
}

#[test]
#[ignore = "needs the generated resources.json"]
fn ublock_redirects_are_served() {
    let rule = "||ads.example.net/ad.js$script,redirect=noopjs";
    let engine = engine_with_resources(rule, &resources_json());
    let decision = engine.check_request("https://ads.example.net/ad.js", PAGE, "script", "GET");
    match decision {
        NetworkDecision::Redirect(url) => {
            assert!(url.starts_with("data:application/javascript;base64,"), "{url}")
        }
        other => panic!("expected a redirect, got {other:?}"),
    }
}

#[test]
#[ignore = "needs the generated resources.json"]
fn ublock_scriptlets_are_injected_with_their_helpers() {
    let engine = engine_with_resources(
        "news.example.com##+js(set-constant, adBlockDetected, false)",
        &resources_json(),
    );
    let script = engine.url_cosmetic_resources(PAGE).injected_script;
    assert!(script.contains("function setConstant("), "scriptlet missing");
    assert!(script.contains("function safeSelf("), "helper missing");
    assert!(script.contains(r#"setConstant("adBlockDetected", "false")"#), "call missing");
}

#[test]
#[ignore = "needs the generated resources.json"]
fn trusted_scriptlets_need_a_trusted_list() {
    let rule = "news.example.com##+js(trusted-set-cookie, a, b)";
    let engine = engine_with_resources(rule, &resources_json());
    let script = engine.url_cosmetic_resources(PAGE).injected_script;
    assert!(!script.contains("trustedSetCookie"), "{script}");
}

#[test]
#[ignore = "needs the generated resources.json"]
fn brave_sugarcoat_resources_are_served() {
    let json = resources_json();
    let sugarcoat = names(&json)
        .into_iter()
        .find(|name| name.starts_with("async-sugarcoat-"))
        .expect("no SugarCoat resource");
    let rule = format!("||tracker.example.net/analytics.js$script,important,redirect={sugarcoat}");
    let engine = engine_with_resources(&rule, &json);
    let url = "https://tracker.example.net/analytics.js";
    let decision = engine.check_request(url, PAGE, "script", "GET");
    assert!(matches!(decision, NetworkDecision::Redirect(_)), "{decision:?}");
}
