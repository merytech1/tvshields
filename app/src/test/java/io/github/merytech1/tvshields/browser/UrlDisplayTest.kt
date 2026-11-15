package io.github.merytech1.tvshields.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlDisplayTest {

    @Test
    fun `drops scheme, www and trailing slash`() {
        assertEquals("elnoticiero.com", displayUrl("https://www.elnoticiero.com/"))
        assertEquals("github.com/merytech1/tvshields", displayUrl("https://github.com/merytech1/tvshields"))
        assertEquals("192.168.1.13:8080", displayUrl("http://192.168.1.13:8080"))
    }

    @Test
    fun `the site is the host without www`() {
        assertEquals("elnoticiero.com", displaySite("https://www.elnoticiero.com/local/bikes?id=3"))
        assertEquals("news.example.com", displaySite("https://News.Example.com:8443/a"))
        assertEquals("192.168.1.13", displaySite("http://192.168.1.13:8080/"))
    }

    @Test
    fun `a url without a host falls back to the short url`() {
        assertEquals("about:blank", displaySite("about:blank"))
        assertEquals("not a url", displaySite("not a url"))
    }

    @Test
    fun `empty stays empty`() {
        assertEquals("", displayUrl(""))
    }
}
