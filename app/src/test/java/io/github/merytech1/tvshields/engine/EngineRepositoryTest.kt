package io.github.merytech1.tvshields.engine

import io.github.merytech1.tvshields.lists.FilterListStore
import io.github.merytech1.tvshields.lists.UpdateResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EngineRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** Engine whose serialized form is its rules joined by newlines. */
    private class FakeEngine(val rules: List<String>) : AdblockEngine {
        override fun checkRequest(url: String, sourceUrl: String, requestType: String, method: String) =
            if (rules.any { url.contains(it) }) NetworkDecision.Block else NetworkDecision.Allow

        override fun serialize() = rules.joinToString("\n").toByteArray()

        override fun close() = Unit
    }

    private class FakeFactory : AdblockEngineFactory {
        var built = 0
        var deserialized = 0
        var corrupt = false

        override fun fromLists(lists: List<String>): AdblockEngine {
            built++
            return FakeEngine(lists)
        }

        override fun deserialize(bytes: ByteArray): AdblockEngine {
            if (corrupt) throw RuntimeException("bad cache")
            deserialized++
            return FakeEngine(String(bytes).split("\n"))
        }
    }

    private class FakeLists(var texts: List<String>, var onUpdate: List<String>? = null) : FilterListStore {
        var updates = 0

        override suspend fun update(): UpdateResult {
            updates++
            onUpdate?.let { texts = it }
            return UpdateResult(emptyList(), emptyList(), emptyList())
        }

        override suspend fun storedLists() = texts

        override suspend fun fingerprint() = texts.joinToString("|").toByteArray().copyOf(32)
    }

    private val file get() = tmp.root.resolve("engine.dat")
    private val factory = FakeFactory()

    private fun repository(lists: FilterListStore) = EngineRepository(file, lists, factory)

    @Test
    fun `first load builds the engine and caches it`() = runBlocking {
        val loaded = repository(FakeLists(listOf("ads"))).load()!!

        assertEquals(LoadedEngine.Source.BUILT, loaded.source)
        assertEquals(1, factory.built)
        assertEquals(NetworkDecision.Block, loaded.engine.checkRequest("https://ads.example/", "", "script"))
        assertEquals(true, file.isFile)
    }

    @Test
    fun `second load comes from the cache`() = runBlocking {
        val lists = FakeLists(listOf("ads"))
        repository(lists).load()

        val loaded = repository(lists).load()!!

        assertEquals(LoadedEngine.Source.CACHE, loaded.source)
        assertEquals(1, factory.built)
        assertEquals(1, factory.deserialized)
        assertEquals(NetworkDecision.Block, loaded.engine.checkRequest("https://ads.example/", "", "script"))
    }

    @Test
    fun `changed lists rebuild the engine`() = runBlocking {
        val lists = FakeLists(listOf("ads"))
        repository(lists).load()

        lists.texts = listOf("ads", "tracker")
        val loaded = repository(lists).load()!!

        assertEquals(LoadedEngine.Source.BUILT, loaded.source)
        assertEquals(2, factory.built)
        assertEquals(NetworkDecision.Block, loaded.engine.checkRequest("https://tracker.example/", "", "script"))
    }

    @Test
    fun `a cache the engine rejects is rebuilt`() = runBlocking {
        val lists = FakeLists(listOf("ads"))
        repository(lists).load()

        factory.corrupt = true
        val loaded = repository(lists).load()!!

        assertEquals(LoadedEngine.Source.BUILT, loaded.source)
        assertEquals(2, factory.built)
    }

    @Test
    fun `a file without our header is ignored`() = runBlocking {
        file.writeText("garbage")

        val loaded = repository(FakeLists(listOf("ads"))).load()!!

        assertEquals(LoadedEngine.Source.BUILT, loaded.source)
        assertEquals(0, factory.deserialized)
    }

    @Test
    fun `no stored lists downloads them first`() = runBlocking {
        val lists = FakeLists(emptyList(), onUpdate = listOf("ads"))

        val loaded = repository(lists).load()!!

        assertEquals(1, lists.updates)
        assertEquals(LoadedEngine.Source.BUILT, loaded.source)
    }

    @Test
    fun `no lists even after downloading gives no engine`() = runBlocking {
        val lists = FakeLists(emptyList())

        assertNull(repository(lists).load())
        assertEquals(0, factory.built)
    }
}
