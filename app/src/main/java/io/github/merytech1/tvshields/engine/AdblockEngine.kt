package io.github.merytech1.tvshields.engine

/** What to do with a network request. */
sealed interface NetworkDecision {
    data object Allow : NetworkDecision
    data object Block : NetworkDecision
    /** Serve this `data:` URL instead of the request (`redirect=` rules). */
    data class Redirect(val dataUrl: String) : NetworkDecision
    /** Load this URL instead (`removeparam` and similar rules). */
    data class Rewrite(val url: String) : NetworkDecision
}

/**
 * The blocking engine as the rest of the app sees it (architecture §3.1). [NativeAdblockEngine]
 * is the JNI implementation; tests use fakes, so code around the engine runs without the `.so`.
 * Safe to use from several threads at once (rules C2).
 */
interface AdblockEngine : AutoCloseable {
    fun checkRequest(url: String, sourceUrl: String, requestType: String, method: String = "GET"): NetworkDecision

    /**
     * URL-specific cosmetic resources as JSON (`UrlSpecificResources`: `hide_selectors`,
     * `procedural_actions`, `exceptions`, `injected_script`, `generichide`).
     */
    fun cosmeticResources(url: String): String

    /** Engine state for `engine.dat` (rules T8). Resources are not included. */
    fun serialize(): ByteArray
}

/** Creates engines, either from list texts (slow) or from a serialized engine (fast). */
interface AdblockEngineFactory {
    fun fromLists(lists: List<String>): AdblockEngine

    /** Throws [RuntimeException] if [bytes] are corrupt or come from another engine version. */
    fun deserialize(bytes: ByteArray): AdblockEngine
}
