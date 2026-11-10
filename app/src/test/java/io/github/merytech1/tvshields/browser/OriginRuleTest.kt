package io.github.merytech1.tvshields.browser

import org.junit.Assert.assertEquals
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
}
