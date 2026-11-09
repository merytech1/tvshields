package io.github.merytech1.tvshields.engine

/**
 * JNI entry points of `libtvshields_engine.so` (engine/src/jni_bridge.rs). The library registers
 * these methods by name in `JNI_OnLoad`, so names and signatures must match `native_methods()`
 * there; loading fails with `UnsatisfiedLinkError` if they drift.
 *
 * Engines are numeric handles. Errors (unknown handle, bad input, Rust panic) arrive as
 * `RuntimeException`. Wire formats: docs/resources/rust-on-android.md.
 */
object NativeEngine {
    init {
        System.loadLibrary("tvshields_engine")
    }

    /** Builds an engine from filter list texts; returns its handle. */
    @JvmStatic external fun nativeCreate(lists: Array<String>): Long

    @JvmStatic external fun nativeDeserialize(bytes: ByteArray): Long

    @JvmStatic external fun nativeSerialize(handle: Long): ByteArray

    /** Loads redirect/scriptlet resources (JSON); returns how many were loaded. */
    @JvmStatic external fun nativeUseResources(handle: Long, json: String): Int

    /** null = allow, "B" = block, "R<data URL>" = redirect, "W<url>" = rewritten URL. */
    @JvmStatic external fun nativeCheckRequest(
        handle: Long,
        url: String,
        sourceUrl: String,
        requestType: String,
        method: String,
    ): String?

    @JvmStatic external fun nativeCosmeticResources(handle: Long, url: String): String

    @JvmStatic external fun nativeHiddenSelectors(
        handle: Long,
        classes: Array<String>,
        ids: Array<String>,
        exceptions: Array<String>,
    ): String

    @JvmStatic external fun nativeDestroy(handle: Long)
}
