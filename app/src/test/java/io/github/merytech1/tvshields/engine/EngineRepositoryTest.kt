package io.github.merytech1.tvshields.engine

import io.github.merytech1.tvshields.lists.FilterListStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EngineRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

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
