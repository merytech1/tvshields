package io.github.merytech1.tvshields.shields

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CosmeticScriptTest {

    @Test
    fun `installs a style element with the css`() {
        val script = cosmeticScript(".ad-slot, #banner { display: none !important; }")

        assertTrue(script, script.contains("var id = 'tvshields-cosmetic'"))
        assertTrue(script, script.contains("""style.textContent = ".ad-slot, #banner { display: none !important; }";"""))
    }

    @Test
    fun `applies generic css through the page function`() {
        assertEquals(
            """(function(){var f=window.__tvshieldsApplyCosmeticCss;if(f)f(".ad { display: none !important; }");})();""",
            applyGenericCosmeticCss(".ad { display: none !important; }"),
        )
    }

    @Test
    fun `starts procedural filters with an escaped json string`() {
        assertEquals(
            """(function(){var f=window.__tvshieldsStartProcedural;if(f)f("[{\"selector\":[]}]");})();""",
            startProceduralScript("""[{"selector":[]}]"""),
        )
    }

    @Test
    fun `escapes css that could end the javascript string`() {
        assertEquals(""""a\"b\\c\nd"""", jsString("a\"b\\c\nd"))
    }

    @Test
    fun `escapes line and paragraph separators`() {
        assertEquals("\"a\\u2028b\\u2029c\"", jsString("a\u2028b\u2029c"))
    }
}
