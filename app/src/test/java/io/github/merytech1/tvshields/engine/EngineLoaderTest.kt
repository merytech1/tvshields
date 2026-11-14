package io.github.merytech1.tvshields.engine

import io.github.merytech1.tvshields.shields.ShieldsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class EngineLoaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val factory = FakeFactory()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Default)

    private fun loader(lists: FakeLists) = EngineLoader(
        EngineRepository(tmp.root.resolve("engine.dat"), lists, factory),
        lists::hasStoredLists,
        scope,
        closeDelayMs = 50,
    )

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
    fun `an engine without resources updates right away`() {
        val lists = FakeLists(listOf("ads"), resources = null)
        val loader = loader(lists)
        loader.start()
        awaitIdle()
        assertEquals(EngineStatus.Ready, loader.status.value)
        assertEquals(1, lists.updates)
    }

    @Test
    fun `an engine with resources waits for the daily update`() {
        val lists = FakeLists(listOf("ads"))
        loader(lists).start()
        awaitIdle()
        assertEquals(0, lists.updates)
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

    @Test
    fun `refresh with changed lists swaps the engine and closes the old one later`() {
        val lists = FakeLists(listOf("ads"))
        val loader = loader(lists)
        loader.start()
        awaitIdle()
        val old = loader.engine.value as FakeEngine

        lists.onUpdate = listOf("ads", "tracker")
        val swapped = runBlocking { loader.refresh() }

        assertTrue(swapped)
        assertEquals(NetworkDecision.Block, loader.engine.value!!.checkRequest("https://tracker.example/", "", "script"))
        assertFalse("old engine stays usable for a while", old.closed)
        awaitIdle()
        assertTrue(old.closed)
    }

    @Test
    fun `refresh with unchanged lists keeps the engine`() {
        val loader = loader(FakeLists(listOf("ads")))
        loader.start()
        awaitIdle()
        val before = loader.engine.value

        assertFalse(runBlocking { loader.refresh() })
        assertSame(before, loader.engine.value)
    }

    @Test
    fun `lists arriving after continuing unprotected turn Shields on`() {
        val lists = FakeLists(emptyList())
        val loader = loader(lists)
        loader.start()
        awaitIdle()
        loader.continueUnprotected()

        lists.onUpdate = listOf("ads")
        runBlocking { loader.refresh() }

        assertEquals(EngineStatus.Ready, loader.status.value)
        assertNotNull(loader.engine.value)
    }

    @Test
    fun `an update does not interrupt ongoing browsing`() {
        val lists = FakeLists(listOf("ads"))
        val loader = loader(lists)
        loader.start()
        awaitIdle()
        val shields = ShieldsManager { loader.engine.value }
        val running = AtomicBoolean(true)
        val checks = AtomicInteger()
        val leaks = AtomicInteger()
        // Page requests on several WebView threads while the update runs.
        val threads = List(4) {
            thread {
                while (running.get()) {
                    val decision = shields.check("https://ads.example/a.js", "https://news.example/", false, "*/*", "GET")
                    if (decision != NetworkDecision.Block) leaks.incrementAndGet()
                    checks.incrementAndGet()
                }
            }
        }

        lists.onUpdate = listOf("ads", "tracker")
        runBlocking { loader.refresh() }
        awaitIdle() // the old engine is closed while requests keep coming
        Thread.sleep(50)
        running.set(false)
        threads.forEach { it.join() }

        assertTrue(checks.get() > 0)
        assertEquals("ad requests allowed during the update", 0, leaks.get())
        assertEquals(
            NetworkDecision.Block,
            shields.check("https://tracker.example/t.js", "https://news.example/", false, "*/*", "GET"),
        )
    }
}
