package io.github.merytech1.tvshields.engine

import io.github.merytech1.tvshields.lists.FilterListStore
import io.github.merytech1.tvshields.lists.StoredList
import io.github.merytech1.tvshields.lists.UpdateResult

// Test doubles shared by the engine tests.

/** Engine whose serialized form is its rules joined by newlines. Fails if used after [close]. */
internal class FakeEngine(val rules: List<String>) : AdblockEngine {
    @Volatile
    var closed = false
    var cosmeticJson = """{"hide_selectors":[]}"""
    var resourcesJson: String? = null
    var hiddenSelectors = emptyList<String>()

    override fun checkRequest(url: String, sourceUrl: String, requestType: String, method: String): NetworkDecision {
        // Like a released native handle.
        check(!closed) { "engine used after close" }
        return if (rules.any { url.contains(it) }) NetworkDecision.Block else NetworkDecision.Allow
    }

    override fun cosmeticResources(url: String): String {
        check(!closed) { "engine used after close" }
        return cosmeticJson
    }

    override fun hiddenClassIdSelectors(classes: List<String>, ids: List<String>, exceptions: List<String>): List<String> {
        check(!closed) { "engine used after close" }
        return hiddenSelectors
    }

    override fun serialize() = rules.joinToString("\n").toByteArray()

    /** Counts one resource per line. */
    override fun useResources(json: String): Int {
        if (json == "invalid") throw RuntimeException("invalid resources")
        resourcesJson = json
        return json.lines().size
    }

    override fun close() {
        closed = true
    }
}

internal class FakeFactory : AdblockEngineFactory {
    var built = 0
    var deserialized = 0
    var corrupt = false
    var failBuild = false
    var lastLists = emptyList<StoredList>()

    override fun fromLists(lists: List<StoredList>): AdblockEngine {
        if (failBuild) throw RuntimeException("native error")
        built++
        lastLists = lists
        return FakeEngine(lists.map { it.text })
    }

    override fun deserialize(bytes: ByteArray): AdblockEngine {
        if (corrupt) throw RuntimeException("bad cache")
        deserialized++
        return FakeEngine(String(bytes).split("\n"))
    }
}

/**
 * Stored lists. The next [update] replaces them with [onUpdate] (once) and reports a change only
 * if they differ. Lists starting with "trusted" are trusted; [resources] has one resource per line.
 */
internal class FakeLists(
    var texts: List<String>,
    var onUpdate: List<String>? = null,
    var resources: String? = "noop.js",
) : FilterListStore {
    var updates = 0
    var afterProgress: () -> Unit = {}

    override suspend fun update(onProgress: (done: Int, total: Int) -> Unit): UpdateResult {
        updates++
        onProgress(1, 1)
        afterProgress()
        val new = onUpdate
        onUpdate = null
        if (new != null && new != texts) {
            texts = new
            return UpdateResult(listOf("changed"), emptyList(), emptyList())
        }
        return UpdateResult(emptyList(), listOf("same"), emptyList())
    }

    override suspend fun storedLists() = texts.map { StoredList(it, trusted = it.startsWith("trusted")) }

    override suspend fun storedResources() = resources

    override fun hasStoredLists() = texts.isNotEmpty()

    override suspend fun fingerprint() = texts.joinToString("|").toByteArray().copyOf(32)
}
