package io.github.merytech1.tvshields.shields

import io.github.merytech1.tvshields.engine.AdblockEngine
import io.github.merytech1.tvshields.engine.NetworkDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CosmeticFilterTest {

    private class FakeEngine(private val json: String? = null, private val fails: Boolean = false) : AdblockEngine {
        override fun checkRequest(url: String, sourceUrl: String, requestType: String, method: String) =
            NetworkDecision.Allow

        override fun cosmeticResources(url: String): String {
            if (fails) throw RuntimeException("engine error")
            return json ?: error("cosmetic resources not expected")
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

        assertEquals(".ad-slot, #banner-ad { display: none !important; }", filter.cssFor(page))
    }

    @Test
    fun `no selectors means no css`() {
        val filter = CosmeticFilter { FakeEngine("""{"hide_selectors":[]}""") }

        assertNull(filter.cssFor(page))
    }

    @Test
    fun `skips blank selectors`() {
        val filter = CosmeticFilter { FakeEngine("""{"hide_selectors":["","  ",".ad"]}""") }

        assertEquals(".ad { display: none !important; }", filter.cssFor(page))
    }

    @Test
    fun `no css while the engine is loading`() {
        assertNull(CosmeticFilter { null }.cssFor(page))
    }

    @Test
    fun `no css when the engine fails`() {
        assertNull(CosmeticFilter { FakeEngine(fails = true) }.cssFor(page))
    }

    @Test
    fun `no css when the engine returns invalid json`() {
        assertNull(CosmeticFilter { FakeEngine("not json") }.cssFor(page))
    }
}
