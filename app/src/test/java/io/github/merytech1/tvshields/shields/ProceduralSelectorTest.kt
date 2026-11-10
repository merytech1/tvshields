package io.github.merytech1.tvshields.shields

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProceduralSelectorTest {

    private fun parse(selector: String): JSONObject =
        ProceduralSelector.parse(selector) ?: error("selector was not parsed: $selector")

    private fun selector(filter: JSONObject): JSONArray = filter.getJSONArray("selector")

    private fun operator(filter: JSONObject, index: Int): JSONObject =
        selector(filter).getJSONObject(index)

    @Test
    fun `parses operators and a remove action`() {
        val filter = parse("div.sponsor:has-text(Sponsored):upward(2):remove()")

        assertEquals(3, selector(filter).length())
        assertEquals("css-selector", operator(filter, 0).getString("type"))
        assertEquals("div.sponsor", operator(filter, 0).getString("arg"))
        assertEquals("has-text", operator(filter, 1).getString("type"))
        assertEquals("Sponsored", operator(filter, 1).getString("arg"))
        assertEquals("upward", operator(filter, 2).getString("type"))
        assertEquals("2", operator(filter, 2).getString("arg"))
        assertEquals("remove", filter.getJSONObject("action").getString("type"))
    }

    @Test
    fun `parses nested has with has-text`() {
        val filter = parse("a[role=\"button\"]:has(p:has-text(Sponsored))")

        assertEquals(2, selector(filter).length())
        assertEquals("a[role=\"button\"]", operator(filter, 0).getString("arg"))
        assertEquals("has", operator(filter, 1).getString("type"))

        val inner = operator(filter, 1).getJSONArray("arg")
        assertEquals(2, inner.length())
        assertEquals("css-selector", inner.getJSONObject(0).getString("type"))
        assertEquals("p", inner.getJSONObject(0).getString("arg"))
        assertEquals("has-text", inner.getJSONObject(1).getString("type"))
        assertEquals("Sponsored", inner.getJSONObject(1).getString("arg"))
    }

    @Test
    fun `keeps regex and parentheses inside has-text`() {
        val filter = parse(".item:has(.badge:has-text(/^(?:AD|An[uú]ncio|광고|広告)$/))")

        val inner = operator(filter, 1).getJSONArray("arg")
        assertEquals(
            "/^(?:AD|An[uú]ncio|광고|広告)$/",
            inner.getJSONObject(1).getString("arg"),
        )
    }

    @Test
    fun `accepts a leading has-text`() {
        val filter = parse(":has-text(Advertisement)")

        assertEquals(1, selector(filter).length())
        assertEquals("has-text", operator(filter, 0).getString("type"))
        assertEquals("Advertisement", operator(filter, 0).getString("arg"))
    }

    @Test
    fun `parses the remaining operators`() {
        for ((selector, type) in listOf(
            ":xpath(//div[@class=\"ad\"])" to "xpath",
            ":matches-attr(data-ad=\"x\")" to "matches-attr",
            ":matches-css(display: block)" to "matches-css",
            ":matches-css-before(content: /Ad/i)" to "matches-css-before",
            ":matches-css-after(content: Ad)" to "matches-css-after",
            ":matches-path(/article/)" to "matches-path",
            ":min-text-length(10)" to "min-text-length",
        )) {
            val filter = parse(selector)
            assertEquals(selector, type, operator(filter, 0).getString("type"))
        }
    }

    @Test
    fun `parses style, remove-attr and remove-class actions`() {
        val style = parse(".ad:style(margin: 0 !important)")
        assertEquals("style", style.getJSONObject("action").getString("type"))
        assertEquals("margin: 0 !important", style.getJSONObject("action").getString("arg"))

        val attribute = parse(".ad:remove-attr(onclick)")
        assertEquals("remove-attr", attribute.getJSONObject("action").getString("type"))

        val cssClass = parse(".ad:remove-class(sponsored)")
        assertEquals("remove-class", cssClass.getJSONObject("action").getString("type"))
    }

    @Test
    fun `rejects unsafe style actions`() {
        assertNull(ProceduralSelector.parse(".ad:style(background: url(https://tracker/x))"))
        assertNull(ProceduralSelector.parse(".ad:style(width: expression(alert(1)))"))
    }

    @Test
    fun `ignores plain css and attribute values that look procedural`() {
        assertNull(ProceduralSelector.parse(".ad > .banner, #promo"))
        assertNull(ProceduralSelector.parse("div:has(> span)"))
        assertNull(ProceduralSelector.parse("[title=\":has-text(foo)\"]"))
        assertNull(ProceduralSelector.parse(":nth-child(2n + 1)"))
    }

    @Test
    fun `rejects malformed or oversized filters`() {
        assertNull(ProceduralSelector.parse(""))
        assertNull(ProceduralSelector.parse(":has-text(unterminated"))
        assertNull(ProceduralSelector.parse(".x" + ":has(".repeat(10) + ".y" + ")".repeat(10)))
        assertNull(ProceduralSelector.parse("." + "a".repeat(3000) + ":has-text(Ad)"))
        assertNull(ProceduralSelector.parse(":has-text(Ad):style"))
    }

    @Test
    fun `validates structured engine filters`() {
        val filter = ProceduralSelector.validate(
            """{"selector":[{"type":"css-selector","arg":".ad"}],"action":{"type":"style","arg":"margin: 0"}}""",
        )

        assertNotNull(filter)
        assertEquals(".ad", filter!!.getJSONArray("selector").getJSONObject(0).getString("arg"))
        assertEquals("margin: 0", filter.getJSONObject("action").getString("arg"))
    }

    @Test
    fun `rejects invalid structured engine filters`() {
        assertNull(ProceduralSelector.validate("not json"))
        assertNull(ProceduralSelector.validate("""{"selector":[]}"""))
        assertNull(
            ProceduralSelector.validate(
                """{"selector":[{"type":"css-selector","arg":".ad"},{"type":"unknown","arg":"x"}]}""",
            ),
        )
        assertNull(
            ProceduralSelector.validate(
                """{"selector":[{"type":"css-selector","arg":".ad"}],"action":{"type":"style","arg":"background:url(x)"}}""",
            ),
        )
        assertNull(
            ProceduralSelector.validate(
                """{"selector":[{"type":"css-selector","arg":".ad"}],"action":{"type":"remove-class","arg":"a b"}}""",
            ),
        )
    }

    @Test
    fun `structured filters may nest procedural operators`() {
        val filter = ProceduralSelector.validate(
            """{"selector":[{"type":"css-selector","arg":".a"},{"type":"has","arg":[{"type":"css-selector","arg":"p"},{"type":"has-text","arg":"Ad"}]}]}""",
        )

        assertTrue(selector(filter!!).getJSONObject(1).getJSONArray("arg").length() == 2)
    }

    @Test
    fun `parses relative has selectors`() {
        val child = parse(".ts-has-child:has(> div > span:has-text(HAS-CHILD-AD))")
        val childInner = operator(child, 1).getJSONArray("arg")
        assertEquals("> div > span", childInner.getJSONObject(0).getString("arg"))
        assertEquals("has-text", childInner.getJSONObject(1).getString("type"))

        val sibling = parse(".ts-next:has(+ .anchor:has-text(NEXT-AD))")
        val siblingInner = operator(sibling, 1).getJSONArray("arg")
        assertEquals("+ .anchor", siblingInner.getJSONObject(0).getString("arg"))
        assertEquals("has-text", siblingInner.getJSONObject(1).getString("type"))
    }

    @Test
    fun `splits procedural text inside a structured css selector`() {
        val filter = ProceduralSelector.validate(
            """{"selector":[{"type":"css-selector","arg":"a[role=\"button\"]:has(p:has-text(Sponsored))"}],"action":{"type":"remove"}}""",
        )

        val operators = selector(filter!!)
        assertEquals(2, operators.length())
        assertEquals("a[role=\"button\"]", operators.getJSONObject(0).getString("arg"))
        assertEquals("has", operators.getJSONObject(1).getString("type"))
        assertEquals(
            "has-text",
            operators.getJSONObject(1).getJSONArray("arg").getJSONObject(1).getString("type"),
        )
        assertEquals("remove", filter.getJSONObject("action").getString("type"))
    }

    @Test
    fun `splits procedural text inside a nested has`() {
        val filter = ProceduralSelector.validate(
            """{"selector":[{"type":"css-selector","arg":".a"},{"type":"has","arg":[{"type":"css-selector","arg":"p:has-text(Ad)"}]}]}""",
        )

        val inner = selector(filter!!).getJSONObject(1).getJSONArray("arg")
        assertEquals(2, inner.length())
        assertEquals("css-selector", inner.getJSONObject(0).getString("type"))
        assertEquals("has-text", inner.getJSONObject(1).getString("type"))
    }
}
