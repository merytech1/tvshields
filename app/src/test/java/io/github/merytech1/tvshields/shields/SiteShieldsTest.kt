package io.github.merytech1.tvshields.shields

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteShieldsTest {

    @Test
    fun `the site is the lowercase host without www`() {
        assertEquals("example.com", SiteShields.siteOf("https://www.Example.com/news?id=1"))
        assertEquals("m.example.com", SiteShields.siteOf("http://m.example.com:8080/"))
    }

    @Test
    fun `pages that are not on the web have no site`() {
        assertNull(SiteShields.siteOf("about:blank"))
        assertNull(SiteShields.siteOf("file:///sdcard/page.html"))
        assertNull(SiteShields.siteOf("not a url"))
    }

    @Test
    fun `a site and its subdomains are off`() {
        val off = setOf("example.com")
        assertTrue(SiteShields.isOff(off, "https://example.com/"))
        assertTrue(SiteShields.isOff(off, "https://www.example.com/a"))
        assertTrue(SiteShields.isOff(off, "https://m.example.com/a"))
    }

    @Test
    fun `other sites keep their shields`() {
        val off = setOf("news.example.com")
        assertFalse(SiteShields.isOff(off, "https://example.com/"))
        assertFalse(SiteShields.isOff(off, "https://othernews.example.com/"))
        assertFalse(SiteShields.isOff(off, "https://notexample.com/"))
        assertFalse(SiteShields.isOff(off, "about:blank"))
        assertFalse(SiteShields.isOff(emptySet(), "https://news.example.com/"))
    }
}
