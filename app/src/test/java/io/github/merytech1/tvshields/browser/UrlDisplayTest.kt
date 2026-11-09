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
    fun `empty stays empty`() {
        assertEquals("", displayUrl(""))
    }
}
