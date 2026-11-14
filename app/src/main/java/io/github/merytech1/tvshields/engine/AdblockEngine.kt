package io.github.merytech1.tvshields.engine

import io.github.merytech1.tvshields.lists.StoredList

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

    /**
     * CSS selectors that hide the generic rules matching [classes] and [ids]. [exceptions] must
     * come from [cosmeticResources] for the same page, so `#@#` rules are respected.
     *
     * Throws [RuntimeException] on a native engine error.
     */
    fun hiddenClassIdSelectors(classes: List<String>, ids: List<String>, exceptions: List<String>): List<String>

    /** Engine state for `engine.dat` (rules T8). Resources are not included. */
    fun serialize(): ByteArray

    /**
     * Loads scriptlets and redirect resources (`resources.json`, roadmap 5.1). Call it before the
     * engine is shared. Returns how many were accepted. Throws [RuntimeException] on invalid JSON.
     */
    fun useResources(json: String): Int
}

/** Creates engines, either from list texts (slow) or from a serialized engine (fast). */
interface AdblockEngineFactory {
    fun fromLists(lists: List<StoredList>): AdblockEngine

    /** Throws [RuntimeException] if [bytes] are corrupt or come from another engine version. */
    fun deserialize(bytes: ByteArray): AdblockEngine
}
