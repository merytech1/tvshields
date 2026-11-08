# Rust en Android (NDK, cargo-ndk, jni)

## Piezas

| Pieza | Qué hace | Enlace |
|---|---|---|
| Android NDK | Compiladores y librerías para código nativo en Android | https://developer.android.com/ndk |
| Targets de Rust | `armv7-linux-androideabi`, `aarch64-linux-android`, `x86_64-linux-android` | `rustup target add …` |
| `cargo-ndk` | Compila el crate con el NDK y coloca los `.so` en `jniLibs/<abi>/` | https://github.com/bbqsrc/cargo-ndk |
| crate `jni` | Escribir funciones `extern "system"` que Kotlin llama con `external fun` | https://crates.io/crates/jni |

## Correspondencia ABI ↔ target

| ABI Android | Target Rust | Uso |
|---|---|---|
| `armeabi-v7a` | `armv7-linux-androideabi` | TVs de 32 bits (muy común, incluida probablemente la de referencia) |
| `arm64-v8a` | `aarch64-linux-android` | TVs y cajas de 64 bits |
| `x86_64` | `x86_64-linux-android` | Solo emulador, nunca en release (reglas T3) |

## Compilar

```bash
cargo ndk -t armeabi-v7a -t arm64-v8a -t x86_64 -o ../app/src/main/jniLibs build --release
```

Perfil recomendado para tamaño, en `engine/Cargo.toml`:

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

## Forma del puente

```rust
// engine/src/lib.rs
#[no_mangle]
pub extern "system" fn Java_…_NativeEngine_checkRequest(
    mut env: JNIEnv, _class: JClass, handle: jlong,
    url: JString, source: JString, request_type: JString,
) -> jint { … }  // 0 = permitir, 1 = bloquear, 2 = redirect (contenido en otra llamada)
```

```kotlin
// app: …/shields/NativeEngine.kt
object NativeEngine {
    init { System.loadLibrary("tvshields_engine") }
    external fun checkRequest(handle: Long, url: String, source: String, type: String): Int
}
```

Reglas del puente:
- El `Engine` vive en Rust detrás de un puntero (`Box::into_raw`) y Kotlin solo guarda el `handle`.
- **Ningún `panic` cruza a la JVM**: cada función exportada envuelve su cuerpo en `catch_unwind`
  y, ante un error, devuelve "permitir".
- Las llamadas por petición pasan solo cadenas y enteros. Las respuestas grandes (cosmético)
  viajan como un único JSON.
