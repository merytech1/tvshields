//! Thin wrapper around `adblock::Engine` exposing only what tvshields needs.

use std::collections::HashSet;
use std::fmt;

use adblock::lists::{FilterSet, ParseOptions};
use adblock::request::Request;
use adblock::resources::Resource;
use adblock::Engine;

pub use adblock::cosmetic_filter_cache::UrlSpecificResources;

/// What the WebView should do with a network request.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum NetworkDecision {
    /// Load the request normally.
    Allow,
    /// Answer with an empty response.
    Block,
    /// Answer with this `data:` URL instead of the real resource.
    Redirect(String),
    /// Not blocked, but the URL had parameters removed (`removeparam`).
    Rewrite(String),
}

#[derive(Debug)]
pub enum EngineError {
    /// The cached engine could not be loaded (corrupt file or another crate version).
    Deserialize(String),
    /// The resources JSON is not a valid list of resources.
    Resources(String),
}

impl fmt::Display for EngineError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Deserialize(msg) => write!(f, "cannot deserialize engine: {msg}"),
            Self::Resources(msg) => write!(f, "invalid resources: {msg}"),
        }
    }
}

impl std::error::Error for EngineError {}

/// Blocking engine shared across WebView threads (`Send + Sync`, rules C2).
pub struct ShieldsEngine {
    inner: Engine,
}

impl ShieldsEngine {
    /// Builds an engine from the text of one or more filter lists.
    pub fn from_lists<I, S>(lists: I) -> Self
    where
        I: IntoIterator<Item = S>,
        S: Into<String>,
    {
        let mut set = FilterSet::new(false);
        for list in lists {
            set.add_filter_list(list.into(), ParseOptions::default());
        }
        Self {
            inner: Engine::new_with_filter_set(set),
        }
    }

    /// Loads an engine cached with [`ShieldsEngine::serialize`] (rules T8).
    ///
    /// Resources are not part of the cache: call [`ShieldsEngine::use_resources_json`] again.
    pub fn from_serialized(bytes: &[u8]) -> Result<Self, EngineError> {
        let mut inner = Engine::default();
        inner
            .deserialize(bytes)
            .map_err(|e| EngineError::Deserialize(format!("{e:?}")))?;
        Ok(Self { inner })
    }

    pub fn serialize(&self) -> Vec<u8> {
        self.inner.serialize()
    }

    /// Loads scriptlets and redirect resources from a `resources.json` file.
    /// Returns how many resources were loaded.
    pub fn use_resources_json(&mut self, json: &str) -> Result<usize, EngineError> {
        let resources: Vec<Resource> =
            serde_json::from_str(json).map_err(|e| EngineError::Resources(e.to_string()))?;
        let count = resources.len();
        self.inner.use_resources(resources);
        Ok(count)
    }

    /// Decides what to do with a request. Unparseable URLs are allowed, never blocked.
    pub fn check_request(
        &self,
        url: &str,
        source_url: &str,
        request_type: &str,
        method: &str,
    ) -> NetworkDecision {
        let Ok(request) = Request::new(url, source_url, request_type, method) else {
            return NetworkDecision::Allow;
        };
        let result = self.inner.check_network_request(&request);
        if result.should_block() {
            match result.redirect {
                Some(data_url) => NetworkDecision::Redirect(data_url),
                None => NetworkDecision::Block,
            }
        } else if let Some(url) = result.rewritten_url {
            NetworkDecision::Rewrite(url)
        } else {
            NetworkDecision::Allow
        }
    }

    /// Cosmetic resources for a page: selectors to hide, procedural filters,
    /// generic-rule exceptions, scriptlets and the `generichide` flag.
    pub fn url_cosmetic_resources(&self, url: &str) -> UrlSpecificResources {
        self.inner.url_cosmetic_resources(url)
    }

    /// Generic hide selectors matching newly seen classes and ids on a page.
    pub fn hidden_class_id_selectors<C, I>(
        &self,
        classes: impl IntoIterator<Item = C>,
        ids: impl IntoIterator<Item = I>,
        exceptions: &HashSet<String>,
    ) -> Vec<String>
    where
        C: AsRef<str>,
        I: AsRef<str>,
    {
        self.inner
            .hidden_class_id_selectors(classes, ids, exceptions)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const PAGE: &str = "https://news.example.com/article";

    fn engine(rules: &str) -> ShieldsEngine {
        ShieldsEngine::from_lists([rules])
    }

    fn assert_send_sync<T: Send + Sync>() {}

    #[test]
    fn engine_is_shareable_across_threads() {
        assert_send_sync::<ShieldsEngine>();
    }

    #[test]
    fn blocks_matching_request() {
        let e = engine("||ads.example.net^");
        let decision = e.check_request("https://ads.example.net/banner.png", PAGE, "image", "GET");
        assert_eq!(decision, NetworkDecision::Block);
    }

    #[test]
    fn allows_unrelated_request() {
        let e = engine("||ads.example.net^");
        let decision = e.check_request("https://cdn.example.com/app.js", PAGE, "script", "GET");
        assert_eq!(decision, NetworkDecision::Allow);
    }

    #[test]
    fn rules_from_several_lists_are_combined() {
        let e = ShieldsEngine::from_lists(["||ads.example.net^", "||tracker.example.org^"]);
        let ad = e.check_request("https://ads.example.net/a.js", PAGE, "script", "GET");
        let tracker = e.check_request("https://tracker.example.org/t.gif", PAGE, "image", "GET");
        assert_eq!(ad, NetworkDecision::Block);
        assert_eq!(tracker, NetworkDecision::Block);
    }

    #[test]
    fn request_type_option_is_respected() {
        let e = engine("||ads.example.net^$script");
        let script = e.check_request("https://ads.example.net/a.js", PAGE, "script", "GET");
        let image = e.check_request("https://ads.example.net/a.png", PAGE, "image", "GET");
        assert_eq!(script, NetworkDecision::Block);
        assert_eq!(image, NetworkDecision::Allow);
    }

    #[test]
    fn exception_overrides_block() {
        let e = engine("||ads.example.net^\n@@||ads.example.net/allowed/*");
        let blocked = e.check_request("https://ads.example.net/banner.png", PAGE, "image", "GET");
        let excepted =
            e.check_request("https://ads.example.net/allowed/logo.png", PAGE, "image", "GET");
        assert_eq!(blocked, NetworkDecision::Block);
        assert_eq!(excepted, NetworkDecision::Allow);
    }

    #[test]
    fn important_beats_exception() {
        let e = engine("||ads.example.net^$important\n@@||ads.example.net^");
        let decision = e.check_request("https://ads.example.net/banner.png", PAGE, "image", "GET");
        assert_eq!(decision, NetworkDecision::Block);
    }

    #[test]
    fn invalid_url_is_allowed() {
        let e = engine("||ads.example.net^");
        let decision = e.check_request("not a url", PAGE, "image", "GET");
        assert_eq!(decision, NetworkDecision::Allow);
    }

    // "noop.js" as base64: a script that does nothing.
    const RESOURCES: &str = r#"[
        {
            "name": "noop.js",
            "aliases": ["noopjs"],
            "kind": { "mime": "application/javascript" },
            "content": "KGZ1bmN0aW9uKCkgeyd1c2Ugc3RyaWN0Jzt9KSgpOw=="
        }
    ]"#;

    #[test]
    fn redirect_serves_resource_as_data_url() {
        let mut e = engine("||ads.example.net/loader.js$script,redirect=noopjs");
        assert_eq!(e.use_resources_json(RESOURCES).unwrap(), 1);
        let decision = e.check_request("https://ads.example.net/loader.js", PAGE, "script", "GET");
        match decision {
            NetworkDecision::Redirect(url) => {
                assert!(url.starts_with("data:application/javascript;base64,"), "{url}")
            }
            other => panic!("expected a redirect, got {other:?}"),
        }
    }

    #[test]
    fn redirect_without_resource_still_blocks() {
        let e = engine("||ads.example.net/loader.js$script,redirect=noopjs");
        let decision = e.check_request("https://ads.example.net/loader.js", PAGE, "script", "GET");
        assert_eq!(decision, NetworkDecision::Block);
    }

    #[test]
    fn invalid_resources_json_is_rejected() {
        let mut e = engine("");
        assert!(matches!(
            e.use_resources_json("{not json"),
            Err(EngineError::Resources(_))
        ));
    }

    #[test]
    fn cosmetic_returns_site_specific_selectors() {
        let e = engine("news.example.com##.sponsored\nother.example.org##.promo");
        let res = e.url_cosmetic_resources(PAGE);
        assert!(res.hide_selectors.contains(".sponsored"));
        assert!(!res.hide_selectors.contains(".promo"));
        assert!(!res.generichide);
    }

    /// `procedural_actions` entries are JSON strings; this keeps the assertions readable.
    fn procedural_filters(res: &UrlSpecificResources) -> Vec<serde_json::Value> {
        res.procedural_actions
            .iter()
            .map(|action| serde_json::from_str(action).expect("procedural action is JSON"))
            .collect()
    }

    #[test]
    fn procedural_operators_and_actions_are_structured() {
        let e = engine(concat!(
            "news.example.com#?#div.sponsor:has-text(Sponsored):upward(2):remove()\n",
            "news.example.com#?#.ad:style(margin: 0 !important)\n",
            "news.example.com#?#.track:remove-attr(onclick)\n",
            "news.example.com#?#.tag:remove-class(ad)\n",
            "news.example.com#?#.css:matches-css(display: block)\n",
            "news.example.com#?#.attr:matches-attr(data-ad)\n",
            "news.example.com#?#.text:min-text-length(10)\n",
            "news.example.com#?#.path:matches-path(/article/)\n",
            "news.example.com#?#.xp:xpath(//div)\n",
        ));
        let res = e.url_cosmetic_resources(PAGE);
        assert!(res.hide_selectors.is_empty(), "{:?}", res.hide_selectors);

        let filters = procedural_filters(&res);
        assert_eq!(filters.len(), 9, "{filters:?}");

        let by_action = |action: &str| {
            filters
                .iter()
                .find(|filter| filter["action"]["type"] == action)
                .unwrap_or_else(|| panic!("missing action {action}: {filters:?}"))
        };

        assert_eq!(
            by_action("remove")["selector"],
            serde_json::json!([
                {"type": "css-selector", "arg": "div.sponsor"},
                {"type": "has-text", "arg": "Sponsored"},
                {"type": "upward", "arg": "2"},
            ])
        );
        assert_eq!(by_action("style")["action"]["arg"], "margin: 0 !important");
        assert_eq!(by_action("remove-attr")["action"]["arg"], "onclick");
        assert_eq!(by_action("remove-class")["action"]["arg"], "ad");

        for (selector, operator) in [
            (".css", "matches-css"),
            (".attr", "matches-attr"),
            (".text", "min-text-length"),
            (".path", "matches-path"),
            (".xp", "xpath"),
        ] {
            let filter = filters
                .iter()
                .find(|filter| filter["selector"][1]["type"] == operator)
                .unwrap_or_else(|| panic!("missing {operator}: {filters:?}"));
            assert_eq!(filter["selector"][0], serde_json::json!({"type": "css-selector", "arg": selector}));
            assert!(filter.get("action").is_none(), "{filter}");
        }
    }

    #[test]
    fn nested_procedural_has_is_reported_as_raw_selector() {
        // adblock-rust cannot split `:has(...:has-text(...))`; it stays as a raw selector and our
        // JS parser handles it (roadmap 4.3).
        let e = ShieldsEngine::from_lists([concat!(
            "deliveroo.*#?#a[role=\"button\"]:has(p:has-text(Sponsored))\n",
            "deliveroo.*#?#.promo:has-text(Sponsored)\n",
        )]);
        let res = e.url_cosmetic_resources("https://deliveroo.co.uk/menu");

        assert_eq!(procedural_filters(&res).len(), 1, "{res:?}");
        assert!(
            res.hide_selectors
                .contains("a[role=\"button\"]:has(p:has-text(Sponsored))"),
            "{:?}",
            res.hide_selectors
        );
    }

    #[test]
    fn nested_procedural_has_with_action_is_reported_as_raw_selector() {
        let e = ShieldsEngine::from_lists([concat!(
            "deliveroo.*#?#a[role=\"button\"]:has(p:has-text(Sponsored)):remove()\n",
            "deliveroo.*#?#div.x:not(:has-text(Skip))\n",
        )]);
        let res = e.url_cosmetic_resources("https://deliveroo.co.uk/menu");

        let filters = procedural_filters(&res);
        assert_eq!(filters.len(), 1, "{filters:?}");
        assert_eq!(filters[0]["action"]["type"], "remove");
        assert_eq!(
            filters[0]["selector"],
            serde_json::json!([{
                "type": "css-selector",
                "arg": "a[role=\"button\"]:has(p:has-text(Sponsored))"
            }])
        );
        assert!(
            res.hide_selectors.contains("div.x:not(:has-text(Skip))"),
            "{:?}",
            res.hide_selectors
        );
    }

    #[test]
    fn invalid_css_selectors_are_dropped() {
        let e = engine("news.example.com##.ad[\nnews.example.com##.valid");
        let res = e.url_cosmetic_resources(PAGE);

        assert!(res.hide_selectors.contains(".valid"), "{:?}", res.hide_selectors);
        assert!(
            !res.hide_selectors.iter().any(|selector| selector.contains(".ad[")),
            "{:?}",
            res.hide_selectors
        );
    }

    #[test]
    fn cosmetic_generic_rules_match_classes_and_ids() {
        let e = engine("##.ad-banner\n###sidebar-ad");
        let res = e.url_cosmetic_resources(PAGE);
        let selectors =
            e.hidden_class_id_selectors(["ad-banner", "content"], ["sidebar-ad"], &res.exceptions);
        assert!(selectors.contains(&".ad-banner".to_string()), "{selectors:?}");
        assert!(selectors.contains(&"#sidebar-ad".to_string()), "{selectors:?}");
        assert_eq!(selectors.len(), 2);
    }

    #[test]
    fn cosmetic_exception_disables_generic_rule_on_site() {
        let e = engine("##.ad-banner\nnews.example.com#@#.ad-banner");
        let res = e.url_cosmetic_resources(PAGE);
        let selectors = e.hidden_class_id_selectors(["ad-banner"], [] as [&str; 0], &res.exceptions);
        assert!(selectors.is_empty(), "{selectors:?}");
    }

    #[test]
    fn generichide_is_reported() {
        let e = engine("##.ad-banner\n@@||news.example.com^$generichide");
        assert!(e.url_cosmetic_resources(PAGE).generichide);
    }

    #[test]
    fn scriptlet_is_injected_for_site() {
        let resources = r#"[
            {
                "name": "log.js",
                "aliases": [],
                "kind": "template",
                "content": "Y29uc29sZS5sb2coJ3t7MX19Jyk7"
            }
        ]"#;
        let mut e = engine("news.example.com##+js(log, hello)");
        e.use_resources_json(resources).unwrap();
        let script = e.url_cosmetic_resources(PAGE).injected_script;
        assert!(script.contains("hello"), "{script}");
    }

    #[test]
    fn serialization_round_trip_keeps_rules() {
        let original = engine(
            "||ads.example.net^\n@@||ads.example.net/allowed/*\n##.ad-banner\nnews.example.com#?#.x:has-text(Ad)",
        );
        let procedural = original.url_cosmetic_resources(PAGE).procedural_actions;
        let bytes = original.serialize();
        let restored = ShieldsEngine::from_serialized(&bytes).unwrap();

        let blocked =
            restored.check_request("https://ads.example.net/banner.png", PAGE, "image", "GET");
        let excepted =
            restored.check_request("https://ads.example.net/allowed/logo.png", PAGE, "image", "GET");
        assert_eq!(blocked, NetworkDecision::Block);
        assert_eq!(excepted, NetworkDecision::Allow);

        let res = restored.url_cosmetic_resources(PAGE);
        assert_eq!(res.procedural_actions, procedural);
        let selectors =
            restored.hidden_class_id_selectors(["ad-banner"], [] as [&str; 0], &res.exceptions);
        assert_eq!(selectors, vec![".ad-banner".to_string()]);
    }

    #[test]
    fn corrupt_cache_is_rejected() {
        let mut bytes = engine("||ads.example.net^").serialize();
        bytes.truncate(bytes.len() / 2);
        assert!(matches!(
            ShieldsEngine::from_serialized(&bytes),
            Err(EngineError::Deserialize(_))
        ));
    }
}
