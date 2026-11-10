package io.github.merytech1.tvshields.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.merytech1.tvshields.lists.FilterListStore
import io.github.merytech1.tvshields.lists.UpdateResult
import io.github.merytech1.tvshields.shields.ShieldsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Roadmap 3.6 with the real native engine: list updates swap engines under live requests. */
@RunWith(AndroidJUnit4::class)
class EngineSwapTest {

    private val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "engine-swap.dat")

    /** Each update adds one more blocked domain. */
    private val lists = object : FilterListStore {
        var rules = listOf("||ads.example.com^")

        override suspend fun update(onProgress: (done: Int, total: Int) -> Unit): UpdateResult {
            rules = rules + "||tracker${rules.size}.example.com^"
            return UpdateResult(listOf("changed"), emptyList(), emptyList())
        }

        override fun hasStoredLists() = true
        override suspend fun storedLists() = rules
        override suspend fun fingerprint() = rules.joinToString().toByteArray()
    }

    @After
    fun cleanUp() {
        file.delete()
    }

    @Test
    fun repeatedUpdatesNeverLetAnAdThrough() {
        file.delete()
        val job = SupervisorJob()
        val loader = EngineLoader(
            EngineRepository(file, lists, NativeAdblockEngine),
            lists::hasStoredLists,
            CoroutineScope(job + Dispatchers.Default),
            closeDelayMs = 200,
        )
        loader.start()
        runBlocking { job.children.toList().forEach { it.join() } }
        val shields = ShieldsManager { loader.engine.value }

        val running = AtomicBoolean(true)
        val checks = AtomicInteger()
        val leaks = AtomicInteger()
        val threads = List(4) {
            thread {
                while (running.get()) {
                    val decision = shields.check(
                        "https://ads.example.com/ad.js", "https://news.example.org/", false, "*/*", "GET",
                    )
                    if (decision != NetworkDecision.Block) leaks.incrementAndGet()
                    checks.incrementAndGet()
                }
            }
        }
        repeat(5) { runBlocking { loader.refresh() } }
        // Let every replaced engine be closed while requests keep coming.
        runBlocking { job.children.toList().forEach { it.join() } }
        running.set(false)
        threads.forEach { it.join() }

        assertTrue(checks.get() > 0)
        assertEquals("ad requests allowed during updates", 0, leaks.get())
        assertEquals(
            NetworkDecision.Block,
            shields.check("https://tracker5.example.com/t.js", "https://news.example.org/", false, "*/*", "GET"),
        )
    }
}
