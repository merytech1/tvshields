package io.github.merytech1.tvshields.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResolverTest {

    private val resolver = SearchResolver { SearchEngine.BRAVE }

    private fun searched(query: String) = "https://search.brave.com/search?q=$query"

    @Test
    fun `blank input resolves to nothing`() {
        assertNull(resolver.resolve("   "))
        assertNull(resolver.resolve("?"))
        assertNull(resolver.resolve(" ?  "))
    }

    @Test
    fun `full web URLs open as typed`() {
        assertEquals("http://example.com/a?b=1", resolver.resolve(" http://example.com/a?b=1 "))
        assertEquals("HTTPS://Example.com", resolver.resolve("HTTPS://Example.com"))
        assertEquals("https://file.txt", resolver.resolve("https://file.txt"))
    }

    @Test
    fun `public domains get https`() {
        assertEquals("https://elnoticiero.com", resolver.resolve("elnoticiero.com"))
        assertEquals("https://github.com/merytech1/tvshields", resolver.resolve("github.com/merytech1/tvshields"))
        assertEquals("https://sub.example.co.uk:8443/x", resolver.resolve("sub.example.co.uk:8443/x"))
        assertEquals("https://foo.bar", resolver.resolve("foo.bar"))
        assertEquals("https://EXAMPLE.COM", resolver.resolve("EXAMPLE.COM"))
        assertEquals("https://example.com.", resolver.resolve("example.com."))
        assertEquals("https://my-site.info?x=1#top", resolver.resolve("my-site.info?x=1#top"))
        assertEquals("https://example.org#top", resolver.resolve("example.org#top"))
    }

    @Test
    fun `internationalized domains get https`() {
        assertEquals("https://españa.com", resolver.resolve("españa.com"))
        assertEquals("https://пример.рф", resolver.resolve("пример.рф"))
    }

    @Test
    fun `names whose last part is not a top-level domain are searched`() {
        assertEquals(searched("file.txt"), resolver.resolve("file.txt"))
        assertEquals(searched("node.js"), resolver.resolve("node.js"))
        assertEquals(searched("1.5"), resolver.resolve("1.5"))
        assertEquals(searched("3.14159"), resolver.resolve("3.14159"))
    }

    @Test
    fun `malformed domains are searched`() {
        assertEquals(searched("-bad.com"), resolver.resolve("-bad.com"))
        assertEquals(searched("bad-.com"), resolver.resolve("bad-.com"))
        assertEquals(searched("a..com"), resolver.resolve("a..com"))
        assertEquals(searched(".com"), resolver.resolve(".com"))
        assertEquals(searched("example.com%3Aabc"), resolver.resolve("example.com:abc"))
        assertEquals(searched("example.com%3A99999"), resolver.resolve("example.com:99999"))
        assertEquals(searched("example.com%3A0"), resolver.resolve("example.com:0"))
        assertEquals(searched("user%40example.com"), resolver.resolve("user@example.com"))
    }

    @Test
    fun `localhost gets http`() {
        assertEquals("http://localhost", resolver.resolve("localhost"))
        assertEquals("http://LOCALHOST:3000", resolver.resolve("LOCALHOST:3000"))
        assertEquals("http://localhost/test", resolver.resolve("localhost/test"))
        assertEquals("http://app.localhost:8080/", resolver.resolve("app.localhost:8080/"))
    }

    @Test
    fun `IP addresses get http`() {
        assertEquals("http://192.168.1.13:8080", resolver.resolve("192.168.1.13:8080"))
        assertEquals("http://10.0.0.1/admin", resolver.resolve("10.0.0.1/admin"))
        assertEquals("http://[::1]:8080/", resolver.resolve("[::1]:8080/"))
        assertEquals("http://[fe80::1]", resolver.resolve("[fe80::1]"))
    }

    @Test
    fun `invalid IP addresses are searched`() {
        assertEquals(searched("999.1.1.1"), resolver.resolve("999.1.1.1"))
        assertEquals(searched("1.2.3"), resolver.resolve("1.2.3"))
        assertEquals(searched("1.2.3.4.5"), resolver.resolve("1.2.3.4.5"))
        assertEquals(searched("%3A%3A1"), resolver.resolve("::1"))
    }

    @Test
    fun `local network names get http`() {
        assertEquals("http://printer.local", resolver.resolve("printer.local"))
        assertEquals("http://nas.home.arpa:5000", resolver.resolve("nas.home.arpa:5000"))
    }

    @Test
    fun `single words are searched, even with a slash`() {
        assertEquals(searched("hello"), resolver.resolve("hello"))
        assertEquals(searched("router"), resolver.resolve("router"))
        assertEquals(searched("AC%2FDC"), resolver.resolve("AC/DC"))
    }

    @Test
    fun `phrases are searched`() {
        assertEquals(searched("tvshields+github"), resolver.resolve("tvshields github"))
        assertEquals(searched("what+is+example.com%3F"), resolver.resolve("what is example.com?"))
        assertEquals(searched("example.com+login"), resolver.resolve("example.com login"))
        assertEquals(searched("caf%C3%A9+cerca"), resolver.resolve("café cerca"))
    }

    @Test
    fun `a leading question mark forces a search`() {
        assertEquals(searched("example.com"), resolver.resolve("? example.com"))
        assertEquals(searched("localhost"), resolver.resolve("?localhost"))
    }

    @Test
    fun `non-web schemes are searched, not opened`() {
        assertEquals(searched("javascript%3Aalert%281%29"), resolver.resolve("javascript:alert(1)"))
        assertEquals(searched("file%3A%2F%2F%2Fsdcard"), resolver.resolve("file:///sdcard"))
        assertEquals(searched("intent%3A%2F%2Fx"), resolver.resolve("intent://x"))
        assertEquals(searched("http%3A%2F%2F"), resolver.resolve("http://"))
    }

    @Test
    fun `uses the chosen engine`() {
        val ddg = SearchResolver { SearchEngine.DUCKDUCKGO }
        assertEquals("https://duckduckgo.com/?q=tv", ddg.resolve("tv"))
    }

    @Test
    fun `the bundled top-level domains are IANA's`() {
        val tlds = TopLevelDomains.all
        assertTrue(tlds.size > 1000)
        listOf("com", "bar", "dev", "md", "xn--p1ai").forEach { assertTrue(it, it in tlds) }
        listOf("txt", "js", "local", "localhost").forEach { assertFalse(it, it in tlds) }
    }
}
