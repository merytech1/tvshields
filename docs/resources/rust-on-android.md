# Rust on Android (NDK, cargo-ndk, jni)

## Pieces

| Piece | What it does | Link |
|---|---|---|
| Android NDK | Compilers and libraries for native code on Android | https://developer.android.com/ndk |
| Rust targets | `armv7-linux-androideabi`, `aarch64-linux-android` | `rustup target add …` |
| `cargo-ndk` | Builds the crate with the NDK and places the `.so` files in `jniLibs/<abi>/` | https://github.com/bbqsrc/cargo-ndk |
| `jni` crate | Write `extern "system"` functions that Kotlin calls via `external fun` | https://crates.io/crates/jni |

## ABI ↔ target mapping

| Android ABI | Rust target | Use |
|---|---|---|
| `armeabi-v7a` | `armv7-linux-androideabi` | 32-bit TVs (very common, including the reference TV, which only supports this ABI) |
| `arm64-v8a` | `aarch64-linux-android` | 64-bit TVs and boxes |

## Build

```bash
engine/build-android.sh
```

It runs `cargo ndk -t armeabi-v7a -t arm64-v8a --platform 24 -o ../app/src/main/jniLibs build --release`
(`--platform 24` = `minSdk`, rules T2) and prints the size of each `.so`. `--debug` builds without
optimizations. `jniLibs/` is generated, not versioned. `x86_64` is not built (rules T3).

Size-oriented profile, in `engine/Cargo.toml`:

```toml
[lib]
crate-type = ["cdylib", "rlib"]   # rlib so `cargo test` can run on the host

[profile.release]
opt-level = "z"
lto = true
codegen-units = 1
strip = true
# No `panic = "abort"`: panics must unwind so the bridge can turn them into exceptions.
```

Measured on 2026-11-08 (adblock 0.13.5, jni 0.22.4, NDK r30, Rust 1.99.1), roadmap 1.3 / rules C3:

| ABI | `libtvshields_engine.so` |
|---|---|
| `armeabi-v7a` | 1,967,532 bytes (1.88 MB) |
| `arm64-v8a` | 2,908,640 bytes (2.77 MB) |

Both only depend on `libc.so` and `libdl.so` (no `libc++_shared.so` to bundle).

## Bridge shape

The bridge lives in `engine/src/jni_bridge.rs` (compiled for Android only) on top of
`engine/src/bridge.rs` (plain Rust, unit-tested on the host).

- **Registration with `RegisterNatives` in `JNI_OnLoad`**, not with mangled `Java_…` symbol
  names. The Kotlin class path is a single constant, `NATIVE_CLASS`
  (`io/github/merytech1/tvshields/engine/NativeEngine`), so renaming the package means changing
  one line. `JNI_OnLoad` is the only exported symbol; if the class or a method is missing, the
  library load fails with `UnsatisfiedLinkError`.
- **Handles are ids, not pointers.** Rust keeps the engines in a registry
  (`id → Arc<RwLock<ShieldsEngine>>`). A released or unknown handle raises an exception instead of
  touching freed memory, and a call already in flight keeps the engine alive until it returns, so
  the engine can be swapped after a list update (roadmap 3.6) without races.
- **Errors become Kotlin exceptions.** Every function runs inside `EnvUnowned::with_env`, which
  catches errors and panics and throws `java.lang.RuntimeException`. Kotlin decides what to do
  (for `nativeCheckRequest`, allow the request).
- **Local references are freed per array element**, because Android allows at most 512 per call.

Kotlin side (`app/src/main/java/io/github/merytech1/tvshields/engine/NativeEngine.kt`, roadmap
2.5). An R8 keep rule in `app/proguard-rules.pro` preserves the class and its native methods in
release builds. The instrumented test `NativeEngineTest` loads the library on the device and
covers block, allow, cosmetic resources, serialization and an unknown handle. It passed on the
reference TV (`armeabi-v7a`) on 2026-11-09. The `arm64-v8a` library is packaged, but no 64-bit
device has run it yet.

```kotlin
package io.github.merytech1.tvshields.engine

object NativeEngine {
    init { System.loadLibrary("tvshields_engine") }

    @JvmStatic external fun nativeCreate(lists: Array<String>): Long
    @JvmStatic external fun nativeDeserialize(bytes: ByteArray): Long
    @JvmStatic external fun nativeSerialize(handle: Long): ByteArray
    @JvmStatic external fun nativeUseResources(handle: Long, json: String): Int
    @JvmStatic external fun nativeCheckRequest(
        handle: Long, url: String, sourceUrl: String, requestType: String, method: String,
    ): String?
    @JvmStatic external fun nativeCosmeticResources(handle: Long, url: String): String
    @JvmStatic external fun nativeHiddenSelectors(
        handle: Long, classes: Array<String>, ids: Array<String>, exceptions: Array<String>,
    ): String
    @JvmStatic external fun nativeDestroy(handle: Long)
}
```

Wire formats:

| Function | Returns |
|---|---|
| `nativeCheckRequest` | `null` = allow (no allocation in the common case), `"B"` = block, `"R<data URL>"` = redirect, `"W<url>"` = URL rewritten by `removeparam` |
| `nativeCosmeticResources` | JSON object: `hide_selectors`, `procedural_actions`, `exceptions` (arrays of strings), `injected_script` (string), `generichide` (boolean) |
| `nativeHiddenSelectors` | JSON array of selectors |
| `nativeUseResources` | Number of resources loaded |

## Windows host note

`jni` has an unconditional build dependency (`walkdir` → `winapi-util`). With
`winapi-util` ≥ 0.1.10 it pulls `windows-sys` 0.61, whose `raw-dylib` linking needs a full MinGW
`dlltool` + `as` that the `windows-gnu` toolchain does not ship. `Cargo.lock` pins
`winapi-util` to **0.1.9** (`windows-sys` 0.59, prebuilt import libraries). Do not run a plain
`cargo update` without keeping that pin:

```bash
cargo update -p winapi-util --precise 0.1.9
```
