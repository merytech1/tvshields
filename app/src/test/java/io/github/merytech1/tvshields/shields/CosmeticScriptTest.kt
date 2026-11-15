package io.github.merytech1.tvshields.shields

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CosmeticScriptTest {

    @Test
    fun `installs a style element with the css`() {
        val script = cosmeticScript(".ad-slot, #banner { display: none !important; }")

        assertTrue(script, script.contains("var id = 'tvshields-cosmetic'"))
        assertTrue(script, script.contains("""var css = ".ad-slot, #banner { display: none !important; }";"""))
        // The fallback run repeats the script: the same CSS must not be written (and restyled) again.
        assertTrue(script, script.contains("if (style.textContent !== css) style.textContent = css;"))
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
    fun `wraps scriptlets with the globals uBO expects`() {
        val script = scriptletScript("try { abortOnPropertyRead(\"adblock\") } catch ( e ) { }", "tvshields_x")

        assertTrue(script, script.startsWith("(function () {"))
        assertTrue(script, script.contains("const scriptletGlobals = {};"))
        assertTrue(script, script.contains("try { abortOnPropertyRead(\"adblock\") } catch ( e ) { }"))
        assertTrue(script, script.endsWith("})();"))
        // Scriptlet code comes after the globals it reads.
        assertTrue(script.indexOf("scriptletGlobals") < script.indexOf("abortOnPropertyRead"))
    }

    @Test
    fun `scriptlets run once per document`() {
        val script = scriptletScript("noop()", "tvshields_x")

        assertTrue(script, script.contains("""var guard = "tvshields_x";"""))
        assertTrue(script, script.contains("if (Object.prototype.hasOwnProperty.call(window, guard)) return;"))
        assertTrue(script, script.contains("Object.defineProperty(window, guard, { value: true });"))
        assertTrue(script.indexOf("hasOwnProperty") < script.indexOf("noop()"))
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
