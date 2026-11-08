//! JNI glue: converts Kotlin arguments, calls [`crate::bridge`] and registers the natives.

use std::ffi::c_void;

use jni::errors::ThrowRuntimeExAndDefault;
use jni::objects::{JByteArray, JClass, JObjectArray, JString};
use jni::strings::JNIStr;
use jni::sys::{jint, jlong, JNI_ERR, JNI_VERSION_1_6};
use jni::{jni_str, Env, EnvUnowned, JavaVM, NativeMethod};

use crate::bridge::{self, BridgeError};

impl From<jni::errors::Error> for BridgeError {
    fn from(e: jni::errors::Error) -> Self {
        Self::Jni(e.to_string())
    }
}

/// Kotlin class that declares the `external` functions. Change it here if the package changes.
pub const NATIVE_CLASS: &JNIStr = jni_str!("io/github/merytech1/tvshields/engine/NativeEngine");

fn to_strings(env: &mut Env, array: &JObjectArray<JString>) -> Result<Vec<String>, BridgeError> {
    let len = array.len(env)?;
    let mut out = Vec::with_capacity(len);
    for i in 0..len {
        let item = array.get_element(env, i)?;
        out.push(item.try_to_string(env)?);
        // Free the local reference now: Android caps them at 512 per native call.
        env.delete_local_ref(item);
    }
    Ok(out)
}

extern "system" fn native_create<'l>(
    mut env: EnvUnowned<'l>,
    _class: JClass<'l>,
    lists: JObjectArray<'l, JString<'l>>,
) -> jlong {
    env.with_env(|env| -> Result<jlong, BridgeError> {
        Ok(bridge::create(to_strings(env, &lists)?))
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

extern "system" fn native_deserialize<'l>(
    mut env: EnvUnowned<'l>,
    _class: JClass<'l>,
    bytes: JByteArray<'l>,
) -> jlong {
    env.with_env(|env| -> Result<jlong, BridgeError> {
        bridge::deserialize(&env.convert_byte_array(&bytes)?)
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

extern "system" fn native_serialize<'l>(
    mut env: EnvUnowned<'l>,
    _class: JClass<'l>,
    handle: jlong,
) -> JByteArray<'l> {
    env.with_env(|env| -> Result<JByteArray<'l>, BridgeError> {
        Ok(env.byte_array_from_slice(&bridge::serialize(handle)?)?)
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

extern "system" fn native_use_resources<'l>(
    mut env: EnvUnowned<'l>,
    _class: JClass<'l>,
    handle: jlong,
    json: JString<'l>,
) -> jint {
    env.with_env(|env| -> Result<jint, BridgeError> {
        let count = bridge::use_resources(handle, &json.try_to_string(env)?)?;
        Ok(jint::try_from(count).unwrap_or(jint::MAX))
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

extern "system" fn native_check_request<'l>(
    mut env: EnvUnowned<'l>,
    _class: JClass<'l>,
    handle: jlong,
    url: JString<'l>,
    source_url: JString<'l>,
    request_type: JString<'l>,
    method: JString<'l>,
) -> JString<'l> {
    env.with_env(|env| -> Result<JString<'l>, BridgeError> {
        let decision = bridge::check_request(
            handle,
            &url.try_to_string(env)?,
            &source_url.try_to_string(env)?,
            &request_type.try_to_string(env)?,
            &method.try_to_string(env)?,
        )?;
        Ok(match decision {
            Some(encoded) => JString::from_str(env, encoded)?,
            None => JString::default(),
        })
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

extern "system" fn native_cosmetic_resources<'l>(
    mut env: EnvUnowned<'l>,
    _class: JClass<'l>,
    handle: jlong,
    url: JString<'l>,
) -> JString<'l> {
    env.with_env(|env| -> Result<JString<'l>, BridgeError> {
        let json = bridge::cosmetic_resources(handle, &url.try_to_string(env)?)?;
        Ok(JString::from_str(env, json)?)
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

extern "system" fn native_hidden_selectors<'l>(
    mut env: EnvUnowned<'l>,
    _class: JClass<'l>,
    handle: jlong,
    classes: JObjectArray<'l, JString<'l>>,
    ids: JObjectArray<'l, JString<'l>>,
    exceptions: JObjectArray<'l, JString<'l>>,
) -> JString<'l> {
    env.with_env(|env| -> Result<JString<'l>, BridgeError> {
        let classes = to_strings(env, &classes)?;
        let ids = to_strings(env, &ids)?;
        let exceptions = to_strings(env, &exceptions)?;
        let json = bridge::hidden_selectors(handle, &classes, &ids, exceptions)?;
        Ok(JString::from_str(env, json)?)
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

extern "system" fn native_destroy<'l>(mut env: EnvUnowned<'l>, _class: JClass<'l>, handle: jlong) {
    env.with_env(|_| -> Result<(), BridgeError> {
        bridge::destroy(handle);
        Ok(())
    })
    .resolve::<ThrowRuntimeExAndDefault>()
}

macro_rules! native {
    ($name:literal, $sig:literal, $f:expr) => {
        // SAFETY: each signature below matches the Rust function it is paired with,
        // and all of them are static methods (second parameter is `JClass`).
        unsafe { NativeMethod::from_raw_parts(jni_str!($name), jni_str!($sig), $f as *mut c_void) }
    };
}

fn native_methods() -> [NativeMethod<'static>; 8] {
    [
        native!("nativeCreate", "([Ljava/lang/String;)J", native_create),
        native!("nativeDeserialize", "([B)J", native_deserialize),
        native!("nativeSerialize", "(J)[B", native_serialize),
        native!("nativeUseResources", "(JLjava/lang/String;)I", native_use_resources),
        native!(
            "nativeCheckRequest",
            "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            native_check_request
        ),
        native!(
            "nativeCosmeticResources",
            "(JLjava/lang/String;)Ljava/lang/String;",
            native_cosmetic_resources
        ),
        native!(
            "nativeHiddenSelectors",
            "(J[Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;)Ljava/lang/String;",
            native_hidden_selectors
        ),
        native!("nativeDestroy", "(J)V", native_destroy),
    ]
}

/// Called by `System.loadLibrary("tvshields_engine")` on the loading thread, which can see
/// the app's classes. Fails the load if the Kotlin class or a method is missing.
#[unsafe(no_mangle)]
pub extern "system" fn JNI_OnLoad(vm: *mut jni::sys::JavaVM, _reserved: *mut c_void) -> jint {
    // SAFETY: the JVM passes a valid `JavaVM` pointer to `JNI_OnLoad`.
    let vm = unsafe { JavaVM::from_raw(vm) };
    let registered = vm.attach_current_thread(|env| -> jni::errors::Result<()> {
        // SAFETY: see `native!`.
        unsafe { env.register_native_methods(NATIVE_CLASS, &native_methods()) }
    });
    match registered {
        Ok(()) => JNI_VERSION_1_6,
        Err(_) => JNI_ERR,
    }
}
