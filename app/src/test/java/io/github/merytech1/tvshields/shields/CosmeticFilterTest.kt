package io.github.merytech1.tvshields.shields

import io.github.merytech1.tvshields.engine.AdblockEngine
import io.github.merytech1.tvshields.engine.NetworkDecision
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CosmeticFilterTest {

    private class FakeEngine(
        private val json: String? = null,
        private val fails: Boolean = false,
        private val selectors: List<String> = emptyList(),
    ) : AdblockEngine {
        var lastRequest: List<List<String>>? = null

        override fun checkRequest(url: String, sourceUrl: String, requestType: String, method: String) =
            NetworkDecision.Allow

        override fun cosmeticResources(url: String): String {
            if (fails) throw RuntimeException("engine error")
            return json ?: error("cosmetic resources not expected")
        }

        override fun hiddenClassIdSelectors(classes: List<String>, ids: List<String>, exceptions: List<String>): List<String> {
            if (fails) throw RuntimeException("engine error")
            lastRequest = listOf(classes, ids, exceptions)
            return selectors
        }

        override fun serialize() = ByteArray(0)

        override fun close() = Unit
    }

    private val page = "https://news.example/article"

    @Test
    fun `joins the hide selectors into one css rule`() {
        val filter = CosmeticFilter {
            FakeEngine("""{"hide_selectors":[".ad-slot","#banner-ad"],"generichide":false}""")
        }

        assertEquals(".ad-slot, #banner-ad { display: none !important; }", filter.resourcesFor(page)?.css)
    }

    @Test
    fun `no selectors means no css`() {
        val filter = CosmeticFilter { FakeEngine("""{"hide_selectors":[]}""") }

        assertNull(filter.resourcesFor(page)?.css)
    }

    @Test
    fun `skips blank selectors`() {
        val filter = CosmeticFilter { FakeEngine("""{"hide_selectors":["","  ",".ad"]}""") }

        assertEquals(".ad { display: none !important; }", filter.resourcesFor(page)?.css)
    }

    @Test
    fun `parses generichide and exceptions`() {
        val filter = CosmeticFilter {
            FakeEngine("""{"hide_selectors":[],"generichide":true,"exceptions":[".allowed","#ok"]}""")
        }

        val resources = filter.resourcesFor(page)!!
        assertTrue(resources.generichide)
        assertEquals(listOf(".allowed", "#ok"), resources.exceptions)
    }

    @Test
    fun `generichide defaults to false`() {
        val filter = CosmeticFilter { FakeEngine("""{"hide_selectors":[]}""") }

        assertFalse(filter.resourcesFor(page)!!.generichide)
    }

    @Test
    fun `no resources while the engine is loading`() {
        assertNull(CosmeticFilter { null }.resourcesFor(page))
    }

    @Test
    fun `no resources when the engine fails`() {
        assertNull(CosmeticFilter { FakeEngine(fails = true) }.resourcesFor(page))
    }

    @Test
    fun `no resources when the engine returns invalid json`() {
        assertNull(CosmeticFilter { FakeEngine("not json") }.resourcesFor(page))
    }

    @Test
    fun `asks the engine for generic selectors with the page exceptions`() {
        val engine = FakeEngine(selectors = listOf(".ad-banner", "#sidebar-ad"))
        val filter = CosmeticFilter { engine }

        val selectors = filter.hiddenSelectors(listOf("ad-banner"), listOf("sidebar-ad"), listOf(".allowed"))

        assertEquals(listOf(".ad-banner", "#sidebar-ad"), selectors)
        assertEquals(
            listOf(listOf("ad-banner"), listOf("sidebar-ad"), listOf(".allowed")),
            engine.lastRequest,
        )
    }

    @Test
    fun `drops blank and duplicate generic selectors`() {
        val filter = CosmeticFilter { FakeEngine(selectors = listOf("", ".ad", ".ad", "#id")) }

        assertEquals(listOf(".ad", "#id"), filter.hiddenSelectors(listOf("ad"), emptyList(), emptyList()))
    }

    @Test
    fun `no generic selectors without tokens`() {
        val filter = CosmeticFilter { FakeEngine(fails = true) }

        assertEquals(emptyList<String>(), filter.hiddenSelectors(emptyList(), emptyList(), emptyList()))
    }

    @Test
    fun `no generic selectors when the engine fails`() {
        val filter = CosmeticFilter { FakeEngine(fails = true) }

        assertEquals(emptyList<String>(), filter.hiddenSelectors(listOf("ad"), emptyList(), emptyList()))
    }

    @Test
    fun `moves raw procedural selectors out of the css`() {
        val filter = CosmeticFilter {
            FakeEngine(
                """{"hide_selectors":[".ad","a[role=\"button\"]:has(p:has-text(Sponsored))"]}""",
            )
        }

        val resources = filter.resourcesFor(page)!!
        assertEquals(".ad { display: none !important; }", resources.css)

        val procedural = JSONArray(resources.procedural!!)
        assertEquals(1, procedural.length())
        val operators = procedural.getJSONObject(0).getJSONArray("selector")
        assertEquals(2, operators.length())
        assertEquals("css-selector", operators.getJSONObject(0).getString("type"))
        assertEquals("has", operators.getJSONObject(1).getString("type"))
    }

    @Test
    fun `validates procedural actions from the engine`() {
        val valid = """{"selector":[{"type":"css-selector","arg":".x"}],"action":{"type":"remove"}}"""
        val invalid = """{"selector":[{"type":"unknown","arg":".y"}]}"""
        val json = JSONObject()
            .put("hide_selectors", JSONArray())
            .put("procedural_actions", JSONArray().put(valid).put("not json").put(invalid))
            .toString()
        val filter = CosmeticFilter { FakeEngine(json) }

        val procedural = JSONArray(filter.resourcesFor(page)!!.procedural!!)
        assertEquals(1, procedural.length())
        assertEquals(
            ".x",
            procedural.getJSONObject(0).getJSONArray("selector").getJSONObject(0).getString("arg"),
        )
    }

    @Test
    fun `drops a malformed raw selector without losing the plain css`() {
        val filter = CosmeticFilter {
            FakeEngine("""{"hide_selectors":[".keep",":has-text(unterminated"]}""")
        }

        val resources = filter.resourcesFor(page)!!
        assertEquals(".keep { display: none !important; }", resources.css)
        assertNull(resources.procedural)
    }

    @Test
    fun `caps the number of procedural filters`() {
        val actions = JSONArray()
        repeat(40) { index ->
            actions.put("""{"selector":[{"type":"css-selector","arg":".ad-$index"}]}""")
        }
        val filter = CosmeticFilter {
            FakeEngine(JSONObject().put("hide_selectors", JSONArray()).put("procedural_actions", actions).toString())
        }

        assertEquals(32, JSONArray(filter.resourcesFor(page)!!.procedural!!).length())
    }

    /** Counts the engine calls; every other member is unused here. */
    private class CountingEngine(private val json: String) : AdblockEngine {
        var calls = 0
        var fails = false

        override fun checkRequest(url: String, sourceUrl: String, requestType: String, method: String) =
            NetworkDecision.Allow

        override fun cosmeticResources(url: String): String {
            calls++
            if (fails) throw RuntimeException("engine error")
            return json
        }

        override fun hiddenClassIdSelectors(classes: List<String>, ids: List<String>, exceptions: List<String>) =
            emptyList<String>()

        override fun serialize() = ByteArray(0)

        override fun close() = Unit
    }

    private val adsJson = """{"hide_selectors":[".ad"]}"""

    @Test
    fun `a page asking again does not reach the engine again`() {
        val engine = CountingEngine(adsJson)
        val filter = CosmeticFilter { engine }

        val first = filter.resourcesFor(page)
        val second = filter.resourcesFor(page)

        assertEquals(1, engine.calls)
        assertTrue(first === second)
    }

    @Test
    fun `another url asks the engine`() {
        val engine = CountingEngine(adsJson)
        val filter = CosmeticFilter { engine }

        filter.resourcesFor(page)
        filter.resourcesFor("https://other.example/")

        assertEquals(2, engine.calls)
    }

    @Test
    fun `a new engine drops the cached answers`() {
        var engine = CountingEngine(adsJson)
        val filter = CosmeticFilter { engine }
        filter.resourcesFor(page)

        engine = CountingEngine("""{"hide_selectors":[".new"]}""")
        val resources = filter.resourcesFor(page)

        assertEquals(1, engine.calls)
        assertEquals(".new { display: none !important; }", resources?.css)
    }

    @Test
    fun `an engine failure is not cached`() {
        val engine = CountingEngine(adsJson)
        val filter = CosmeticFilter { engine }
        engine.fails = true
        assertNull(filter.resourcesFor(page))

        engine.fails = false
        assertEquals(".ad { display: none !important; }", filter.resourcesFor(page)?.css)
    }

    @Test
    fun `only the last few pages are kept`() {
        val engine = CountingEngine(adsJson)
        val filter = CosmeticFilter { engine }

        (1..6).forEach { filter.resourcesFor("https://site$it.example/") }
        filter.resourcesFor("https://site6.example/") // still cached
        filter.resourcesFor("https://site1.example/") // evicted

        assertEquals(7, engine.calls)
    }
}
