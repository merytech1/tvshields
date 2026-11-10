package io.github.merytech1.tvshields.shields

import io.github.merytech1.tvshields.engine.AdblockEngine
import io.github.merytech1.tvshields.engine.NetworkDecision
import org.junit.Assert.assertEquals
import org.junit.Test

class ShieldsManagerTest {

    /** Blocks URLs containing "ads" and records what it was asked. */
    private class FakeEngine(val fails: Boolean = false) : AdblockEngine {
        val calls = mutableListOf<List<String>>()

        override fun checkRequest(url: String, sourceUrl: String, requestType: String, method: String): NetworkDecision {
            if (fails) throw RuntimeException("engine error")
            calls += listOf(url, sourceUrl, requestType, method)
            return if (url.contains("ads")) NetworkDecision.Block else NetworkDecision.Allow
        }

        override fun cosmeticResources(url: String) = """{"hide_selectors":[]}"""

        override fun serialize() = ByteArray(0)

        override fun close() = Unit
    }

    private val page = "https://news.example/"

    @Test
    fun `blocks what the engine blocks`() {
        val shields = ShieldsManager { FakeEngine() }
        assertEquals(NetworkDecision.Block, shields.check("https://ads.example/a.js", page, false, "*/*", "GET"))
        assertEquals(NetworkDecision.Allow, shields.check("https://cdn.example/a.js", page, false, "*/*", "GET"))
    }

    @Test
    fun `passes the guessed type, source and method to the engine`() {
        val engine = FakeEngine()
        val shields = ShieldsManager { engine }

        shields.check("https://cdn.example/a.js", page, false, "*/*", "GET")
        shields.check("https://cdn.example/pixel", page, false, "image/webp,*/*", "POST")
        shields.check(page, page, true, "text/html", "GET")

        assertEquals(
            listOf(
                listOf("https://cdn.example/a.js", page, "script", "GET"),
                listOf("https://cdn.example/pixel", page, "image", "POST"),
                listOf(page, page, "document", "GET"),
            ),
            engine.calls,
        )
    }

    @Test
    fun `allows everything while the engine is loading`() {
        val shields = ShieldsManager { null }
        assertEquals(NetworkDecision.Allow, shields.check("https://ads.example/a.js", page, false, "*/*", "GET"))
    }

    @Test
    fun `non http requests never reach the engine`() {
        val engine = FakeEngine()
        val shields = ShieldsManager { engine }
        listOf("data:image/png;base64,AAAA", "blob:https://ads.example/1", "about:blank", "file:///ads.html").forEach {
            assertEquals(NetworkDecision.Allow, shields.check(it, page, false, null, "GET"))
        }
        assertEquals(0, engine.calls.size)
    }

    @Test
    fun `an engine error allows the request`() {
        val shields = ShieldsManager { FakeEngine(fails = true) }
        assertEquals(NetworkDecision.Allow, shields.check("https://ads.example/a.js", page, false, "*/*", "GET"))
    }
}
