package io.github.merytech1.tvshields.engine

import io.github.merytech1.tvshields.lists.FilterListStore
import io.github.merytech1.tvshields.lists.UpdateResult

// Test doubles shared by the engine tests.

/** Engine whose serialized form is its rules joined by newlines. */
internal class FakeEngine(val rules: List<String>) : AdblockEngine {
    override fun checkRequest(url: String, sourceUrl: String, requestType: String, method: String) =
        if (rules.any { url.contains(it) }) NetworkDecision.Block else NetworkDecision.Allow

    override fun serialize() = rules.joinToString("\n").toByteArray()

    override fun close() = Unit
}

internal class FakeFactory : AdblockEngineFactory {
    var built = 0
    var deserialized = 0
    var corrupt = false
    var failBuild = false

    override fun fromLists(lists: List<String>): AdblockEngine {
        if (failBuild) throw RuntimeException("native error")
        built++
        return FakeEngine(lists)
    }

    override fun deserialize(bytes: ByteArray): AdblockEngine {
        if (corrupt) throw RuntimeException("bad cache")
        deserialized++
        return FakeEngine(String(bytes).split("\n"))
    }
}

internal class FakeLists(var texts: List<String>, var onUpdate: List<String>? = null) : FilterListStore {
    var updates = 0
    var afterProgress: () -> Unit = {}

    override suspend fun update(onProgress: (done: Int, total: Int) -> Unit): UpdateResult {
        updates++
        onProgress(1, 1)
        afterProgress()
        onUpdate?.let { texts = it }
        return UpdateResult(emptyList(), emptyList(), emptyList())
    }

    override suspend fun storedLists() = texts

    override fun hasStoredLists() = texts.isNotEmpty()

    override suspend fun fingerprint() = texts.joinToString("|").toByteArray().copyOf(32)
}
