//! tvshields blocking engine and its JNI bridge.
//!
//! Native methods are bound with `RegisterNatives` in `JNI_OnLoad`, so the Kotlin class
//! path lives in a single constant (`NATIVE_CLASS`) instead of every exported symbol name.

pub mod bridge;
pub mod engine;

pub use engine::{EngineError, NetworkDecision, ShieldsEngine, UrlSpecificResources};

#[cfg(target_os = "android")]
mod jni_bridge;
#[cfg(target_os = "android")]
pub use jni_bridge::NATIVE_CLASS;

