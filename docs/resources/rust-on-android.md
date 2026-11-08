# Rust on Android (NDK, cargo-ndk, jni)

## Pieces

| Piece | What it does | Link |
|---|---|---|
| Android NDK | Compilers and libraries for native code on Android | https://developer.android.com/ndk |
| Rust targets | `armv7-linux-androideabi`, `aarch64-linux-android`, `x86_64-linux-android` | `rustup target add …` |
| `cargo-ndk` | Builds the crate with the NDK and places the `.so` files in `jniLibs/<abi>/` | https://github.com/bbqsrc/cargo-ndk |
| `jni` crate | Write `extern "system"` functions that Kotlin calls via `external fun` | https://crates.io/crates/jni |

## ABI ↔ target mapping

| Android ABI | Rust target | Use |
|---|---|---|
| `armeabi-v7a` | `armv7-linux-androideabi` | 32-bit TVs (very common, including the reference TV, which only supports this ABI) |
| `arm64-v8a` | `aarch64-linux-android` | 64-bit TVs and boxes |
| `x86_64` | `x86_64-linux-android` | Emulator only, never in release (rules T3) |

## Build

```bash
cargo ndk -t armeabi-v7a -t arm64-v8a -t x86_64 -o ../app/src/main/jniLibs build --release
```

Size-oriented profile, in `engine/Cargo.toml`:

```toml
[lib]
crate-type = ["cdylib"]

[profile.release]
opt-level = "s"
lto = true
codegen-units = 1
strip = true
panic = "abort"
```

## Bridge shape

```rust
// engine/src/lib.rs
#[no_mangle]
pub extern "system" fn Java_…_NativeEngine_checkRequest(
    mut env: JNIEnv, _class: JClass, handle: jlong,
    url: JString, source: JString, request_type: JString,
) -> jint { … }  // 0 = allow, 1 = block, 2 = redirect (content fetched in a separate call)
```

```kotlin
// app: …/shields/NativeEngine.kt
object NativeEngine {
    init { System.loadLibrary("tvshields_engine") }
    external fun checkRequest(handle: Long, url: String, source: String, type: String): Int
}
```

Bridge rules:
- The `Engine` lives in Rust behind a pointer (`Box::into_raw`); Kotlin only holds the `handle`.
- **No error crosses into the JVM**: every exported function handles its errors and returns "allow"
  on failure. Since the release profile uses `panic = "abort"`, code on the bridge must not panic
  (no `unwrap()` on external input).
- Per-request calls pass only strings and integers. Large responses (cosmetic) travel as a single JSON.
