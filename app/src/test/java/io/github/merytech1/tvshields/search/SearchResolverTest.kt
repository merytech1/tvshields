package io.github.merytech1.tvshields.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchResolverTest {

    private val resolver = SearchResolver { SearchEngine.BRAVE }

    @Test
    fun `blank input resolves to nothing`() {
        assertNull(resolver.resolve("   "))
    }

    @Test
    fun `full web URLs open as typed`() {
        assertEquals("http://example.com/a?b=1", resolver.resolve(" http://example.com/a?b=1 "))
        assertEquals("HTTPS://Example.com", resolver.resolve("HTTPS://Example.com"))
    }

    @Test
    fun `domains get https`() {
        assertEquals("https://elnoticiero.com", resolver.resolve("elnoticiero.com"))
        assertEquals("https://github.com/merytech1/tvshields", resolver.resolve("github.com/merytech1/tvshields"))
        assertEquals("https://sub.example.co.uk:8443/x", resolver.resolve("sub.example.co.uk:8443/x"))
    }

    @Test
    fun `local addresses get http`() {
        assertEquals("http://192.168.1.13:8080", resolver.resolve("192.168.1.13:8080"))
        assertEquals("http://localhost/test", resolver.resolve("localhost/test"))
    }

    @Test
    fun `everything else is searched`() {
        assertEquals("https://search.brave.com/search?q=tvshields+github", resolver.resolve("tvshields github"))
        assertEquals("https://search.brave.com/search?q=1.5", resolver.resolve("1.5"))
        assertEquals("https://search.brave.com/search?q=hello", resolver.resolve("hello"))
        assertEquals("https://search.brave.com/search?q=what+is+example.com%3F", resolver.resolve("what is example.com?"))
    }

    @Test
    fun `non-web schemes are searched, not opened`() {
        assertEquals("https://search.brave.com/search?q=javascript%3Aalert%281%29", resolver.resolve("javascript:alert(1)"))
        assertEquals("https://search.brave.com/search?q=file%3A%2F%2F%2Fsdcard", resolver.resolve("file:///sdcard"))
    }

    @Test
    fun `uses the chosen engine`() {
        val ddg = SearchResolver { SearchEngine.DUCKDUCKGO }
        assertEquals("https://duckduckgo.com/?q=tv", ddg.resolve("tv"))
    }
}
