package io.github.merytech1.tvshields.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EngineLoaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val factory = FakeFactory()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default)

    private fun loader(lists: FakeLists) =
        EngineLoader(EngineRepository(tmp.root.resolve("engine.dat"), lists, factory), lists::hasStoredLists, scope)

    /** Waits for everything the loader launched. */
    private fun awaitIdle() = runBlocking { job.children.toList().forEach { it.join() } }

    @Test
    fun `stored lists start as loading, so browsing is not held`() {
        val status = loader(FakeLists(listOf("ads"))).status.value
        assertEquals(EngineStatus.Loading, status)
        assertTrue(status.allowsBrowsing)
    }

    @Test
    fun `no stored lists start as downloading, so browsing waits`() {
        val status = loader(FakeLists(emptyList())).status.value
        assertTrue(status is EngineStatus.Downloading)
        assertFalse(status.allowsBrowsing)
    }

    @Test
    fun `loading ends ready with an engine`() {
        val loader = loader(FakeLists(listOf("ads")))
        loader.start()
        awaitIdle()
        assertEquals(EngineStatus.Ready, loader.status.value)
        assertNotNull(loader.engine.value)
    }

    @Test
    fun `first launch download reports progress and ends ready`() {
        val lists = FakeLists(emptyList(), onUpdate = listOf("ads"))
        val loader = loader(lists)
        var duringDownload: EngineStatus? = null
        lists.afterProgress = { duringDownload = loader.status.value }

        loader.start()
        awaitIdle()

        assertEquals(EngineStatus.Downloading(1, 1), duringDownload)
        assertEquals(EngineStatus.Ready, loader.status.value)
    }

    @Test
    fun `offline first launch ends with no lists and no engine`() {
        val loader = loader(FakeLists(emptyList()))
        loader.start()
        awaitIdle()
        assertEquals(EngineStatus.NoLists, loader.status.value)
        assertFalse(loader.status.value.allowsBrowsing)
        assertNull(loader.engine.value)
    }

    @Test
    fun `retry after reconnecting loads the engine`() {
        val lists = FakeLists(emptyList())
        val loader = loader(lists)
        loader.start()
        awaitIdle()

        lists.onUpdate = listOf("ads")
        loader.retry()
        awaitIdle()

        assertEquals(EngineStatus.Ready, loader.status.value)
        assertNotNull(loader.engine.value)
    }

    @Test
    fun `continue without protection allows browsing with no engine`() {
        val loader = loader(FakeLists(emptyList()))
        loader.start()
        awaitIdle()

        loader.continueUnprotected()

        assertEquals(EngineStatus.Unprotected, loader.status.value)
        assertTrue(loader.status.value.allowsBrowsing)
        assertNull(loader.engine.value)
    }

    @Test
    fun `a native failure goes on unprotected`() {
        factory.failBuild = true
        val loader = loader(FakeLists(listOf("ads")))
        loader.start()
        awaitIdle()
        assertEquals(EngineStatus.Unprotected, loader.status.value)
    }

    @Test
    fun `retry and continue do nothing unless lists are missing`() {
        val loader = loader(FakeLists(listOf("ads")))
        loader.start()
        awaitIdle()

        loader.retry()
        loader.continueUnprotected()
        awaitIdle()

        assertEquals(EngineStatus.Ready, loader.status.value)
        assertEquals(1, factory.built)
    }
}
