package io.github.merytech1.tvshields.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OriginRuleTest {

    @Test
    fun `keeps scheme host and port`() {
        assertEquals("https://news.example", originRule("https://news.example/a?b=c"))
        assertEquals("http://192.168.1.8:8000", originRule("http://192.168.1.8:8000/test.html"))
    }

    @Test
    fun `unparseable urls match every origin`() {
        assertEquals("*", originRule("not a url"))
        assertEquals("*", originRule("about:blank"))
    }

    @Test
    fun `origin keys omit default ports and normalize case`() {
        assertEquals("https://news.example", originKey("HTTPS://News.Example:443/a"))
        assertEquals("http://192.168.1.8:8000", originKey("http://192.168.1.8:8000/"))
        assertNull(originKey("about:blank"))
        assertNull(originKey("not a url"))
    }
}
