package io.github.merytech1.tvshields.shields

import io.github.merytech1.tvshields.engine.AdblockEngine
import io.github.merytech1.tvshields.engine.NetworkDecision
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
}
