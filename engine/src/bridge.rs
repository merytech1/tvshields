//! JNI-independent bridge logic: engine handles and the wire formats shared with Kotlin.
//! Kept apart from the JNI glue so it can be tested without a JVM.

use std::collections::{HashMap, HashSet};
use std::fmt;
use std::sync::atomic::{AtomicI64, Ordering};
use std::sync::{Arc, OnceLock, RwLock, RwLockReadGuard, RwLockWriteGuard};

use crate::engine::{EngineError, NetworkDecision, ShieldsEngine};

/// Handles are opaque ids, never raw pointers: a stale or wrong handle from Kotlin
/// fails with an exception instead of touching freed memory.
pub type Handle = i64;

type SharedEngine = Arc<RwLock<ShieldsEngine>>;

#[derive(Debug)]
pub enum BridgeError {
    InvalidHandle(Handle),
    Engine(EngineError),
    Json(String),
    Jni(String),
}

impl fmt::Display for BridgeError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::InvalidHandle(h) => write!(f, "invalid or released engine handle: {h}"),
            Self::Engine(e) => write!(f, "{e}"),
            Self::Json(msg) => write!(f, "JSON encoding failed: {msg}"),
            Self::Jni(msg) => write!(f, "JNI error: {msg}"),
        }
    }
}

impl std::error::Error for BridgeError {}

impl From<EngineError> for BridgeError {
    fn from(e: EngineError) -> Self {
        Self::Engine(e)
    }
}

pub type Result<T> = std::result::Result<T, BridgeError>;

struct Registry {
    next: AtomicI64,
    engines: RwLock<HashMap<Handle, SharedEngine>>,
}

fn registry() -> &'static Registry {
    static REGISTRY: OnceLock<Registry> = OnceLock::new();
    REGISTRY.get_or_init(|| Registry {
        next: AtomicI64::new(1),
        engines: RwLock::new(HashMap::new()),
    })
}

// A panic while holding a lock must not disable Shields for the rest of the session.
fn read<T>(lock: &RwLock<T>) -> RwLockReadGuard<'_, T> {
    lock.read().unwrap_or_else(|e| e.into_inner())
}

fn write<T>(lock: &RwLock<T>) -> RwLockWriteGuard<'_, T> {
    lock.write().unwrap_or_else(|e| e.into_inner())
}

fn register(engine: ShieldsEngine) -> Handle {
    let reg = registry();
    let handle = reg.next.fetch_add(1, Ordering::Relaxed);
    write(&reg.engines).insert(handle, Arc::new(RwLock::new(engine)));
    handle
}

/// In-flight calls keep their own `Arc`, so releasing a handle never frees an engine in use.
fn get(handle: Handle) -> Result<SharedEngine> {
    read(&registry().engines)
        .get(&handle)
        .cloned()
        .ok_or(BridgeError::InvalidHandle(handle))
}

pub fn create(lists: Vec<String>) -> Handle {
    register(ShieldsEngine::from_lists(lists))
}

pub fn deserialize(bytes: &[u8]) -> Result<Handle> {
    Ok(register(ShieldsEngine::from_serialized(bytes)?))
}

pub fn serialize(handle: Handle) -> Result<Vec<u8>> {
    Ok(read(&*get(handle)?).serialize())
}

pub fn use_resources(handle: Handle, json: &str) -> Result<usize> {
    Ok(write(&*get(handle)?).use_resources_json(json)?)
}

pub fn check_request(
    handle: Handle,
    url: &str,
    source_url: &str,
    request_type: &str,
    method: &str,
) -> Result<Option<String>> {
    let decision = read(&*get(handle)?).check_request(url, source_url, request_type, method);
    Ok(encode_decision(decision))
}

pub fn cosmetic_resources(handle: Handle, url: &str) -> Result<String> {
    let resources = read(&*get(handle)?).url_cosmetic_resources(url);
    serde_json::to_string(&resources).map_err(|e| BridgeError::Json(e.to_string()))
}

pub fn hidden_selectors(
    handle: Handle,
    classes: &[String],
    ids: &[String],
    exceptions: Vec<String>,
) -> Result<String> {
    let exceptions: HashSet<String> = exceptions.into_iter().collect();
    let selectors = read(&*get(handle)?).hidden_class_id_selectors(classes, ids, &exceptions);
    serde_json::to_string(&selectors).map_err(|e| BridgeError::Json(e.to_string()))
}

/// Returns `false` if the handle was unknown (already released or never created).
pub fn destroy(handle: Handle) -> bool {
    write(&registry().engines).remove(&handle).is_some()
}

/// Wire format of a network decision, chosen so the common case allocates nothing:
/// `None` = allow, `"B"` = block, `"R<data URL>"` = redirect, `"W<url>"` = rewritten URL.
pub fn encode_decision(decision: NetworkDecision) -> Option<String> {
    match decision {
        NetworkDecision::Allow => None,
        NetworkDecision::Block => Some("B".to_owned()),
        NetworkDecision::Redirect(url) => Some(format!("R{url}")),
        NetworkDecision::Rewrite(url) => Some(format!("W{url}")),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const PAGE: &str = "https://news.example.com/article";

    fn rules() -> Vec<String> {
        vec!["||ads.example.net^\nnews.example.com##.sponsored\n##.ad-banner".to_owned()]
    }

    #[test]
    fn decisions_are_encoded() {
        assert_eq!(encode_decision(NetworkDecision::Allow), None);
        assert_eq!(encode_decision(NetworkDecision::Block).as_deref(), Some("B"));
        assert_eq!(
            encode_decision(NetworkDecision::Redirect("data:text/plain;base64,".into())).as_deref(),
            Some("Rdata:text/plain;base64,")
        );
        assert_eq!(
            encode_decision(NetworkDecision::Rewrite("https://a.example/?x=1".into())).as_deref(),
            Some("Whttps://a.example/?x=1")
        );
    }

    #[test]
    fn handles_are_unique_and_never_zero() {
        let a = create(rules());
        let b = create(rules());
        assert_ne!(a, 0);
        assert_ne!(a, b);
        destroy(a);
        destroy(b);
    }

    #[test]
    fn check_request_through_handle() {
        let h = create(rules());
        let blocked = check_request(h, "https://ads.example.net/x.png", PAGE, "image", "GET");
        let allowed = check_request(h, "https://cdn.example.com/x.png", PAGE, "image", "GET");
        assert_eq!(blocked.unwrap().as_deref(), Some("B"));
        assert_eq!(allowed.unwrap(), None);
        destroy(h);
    }

    #[test]
    fn released_handle_is_rejected() {
        let h = create(rules());
        assert!(destroy(h));
        assert!(!destroy(h));
        let err = check_request(h, "https://ads.example.net/x.png", PAGE, "image", "GET");
        assert!(matches!(err, Err(BridgeError::InvalidHandle(id)) if id == h));
        assert!(matches!(serialize(h), Err(BridgeError::InvalidHandle(_))));
    }

    #[test]
    fn unknown_handle_is_rejected() {
        assert!(matches!(cosmetic_resources(0, PAGE), Err(BridgeError::InvalidHandle(0))));
    }

    #[test]
    fn cosmetic_json_has_expected_fields() {
        let h = create(rules());
        let json: serde_json::Value =
            serde_json::from_str(&cosmetic_resources(h, PAGE).unwrap()).unwrap();
        assert_eq!(json["hide_selectors"], serde_json::json!([".sponsored"]));
        assert_eq!(json["generichide"], serde_json::json!(false));
        assert_eq!(json["injected_script"], serde_json::json!(""));
        assert!(json["procedural_actions"].is_array());
        assert!(json["exceptions"].is_array());
        destroy(h);
    }

    #[test]
    fn hidden_selectors_json_is_an_array() {
        let h = create(rules());
        let json = hidden_selectors(h, &["ad-banner".into(), "content".into()], &[], vec![]);
        assert_eq!(json.unwrap(), r#"[".ad-banner"]"#);
        let excepted = hidden_selectors(h, &["ad-banner".into()], &[], vec![".ad-banner".into()]);
        assert_eq!(excepted.unwrap(), "[]");
        destroy(h);
    }

    #[test]
    fn serialize_and_deserialize_through_handles() {
        let h = create(rules());
        let bytes = serialize(h).unwrap();
        destroy(h);
        let restored = deserialize(&bytes).unwrap();
        let blocked = check_request(restored, "https://ads.example.net/x.png", PAGE, "image", "GET");
        assert_eq!(blocked.unwrap().as_deref(), Some("B"));
        destroy(restored);
    }

    #[test]
    fn corrupt_cache_creates_no_handle() {
        assert!(matches!(deserialize(&[1, 2, 3]), Err(BridgeError::Engine(_))));
    }

    #[test]
    fn invalid_resources_are_reported() {
        let h = create(rules());
        assert!(matches!(use_resources(h, "nope"), Err(BridgeError::Engine(_))));
        assert_eq!(use_resources(h, "[]").unwrap(), 0);
        destroy(h);
    }

    #[test]
    fn engine_survives_release_during_use() {
        let h = create(rules());
        let in_flight = get(h).unwrap();
        destroy(h);
        let decision =
            read(&*in_flight).check_request("https://ads.example.net/x.png", PAGE, "image", "GET");
        assert_eq!(decision, NetworkDecision::Block);
    }

    #[test]
    fn concurrent_checks_share_one_engine() {
        let h = create(rules());
        let threads: Vec<_> = (0..8)
            .map(|_| {
                std::thread::spawn(move || {
                    (0..200).all(|_| {
                        check_request(h, "https://ads.example.net/x.png", PAGE, "image", "GET")
                            .unwrap()
                            .as_deref()
                            == Some("B")
                    })
                })
            })
            .collect();
        assert!(threads.into_iter().all(|t| t.join().unwrap()));
        destroy(h);
    }
}
