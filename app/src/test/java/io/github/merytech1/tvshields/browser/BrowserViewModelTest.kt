package io.github.merytech1.tvshields.browser

import io.github.merytech1.tvshields.search.SearchEngine
import io.github.merytech1.tvshields.search.SearchResolver
import io.github.merytech1.tvshields.shields.SiteShields
import io.github.merytech1.tvshields.shields.SiteShieldsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserViewModelTest {

    /** Remembers the switch in memory, like the DataStore repository does before writing. */
    private class FakeSiteShields : SiteShieldsStore {
        val off = mutableSetOf<String>()
        override fun isOff(url: String) = SiteShields.isOff(off, url)
        override fun setOff(site: String, off: Boolean) {
            if (off) this.off += site else this.off -= site
        }
    }

    private val siteShields = FakeSiteShields()
    private val vm = BrowserViewModel(SearchResolver { SearchEngine.BRAVE }, siteShields)

    @Test
    fun `home shows the start page and loads the blank page`() = runBlocking {
        vm.onPageStarted("https://example.com/")
        vm.onHome()
        assertEquals("", vm.state.value.currentUrl)
        assertNull(vm.state.value.progress)
        assertEquals(HOME_URL, vm.loads.first())
    }

    @Test
    fun `the blank page is reported as the start page`() {
        vm.onPageStarted(HOME_URL)
        assertEquals("", vm.state.value.currentUrl)
        assertNull(vm.state.value.progress)
        vm.onPageFinished(HOME_URL)
        assertEquals("", vm.state.value.currentUrl)
    }

    @Test
    fun `counts what shields stopped on the page`() {
        vm.onDocumentRequested("https://news.example/")
        vm.onPageStarted("https://news.example/")
        repeat(3) { vm.onBlocked() }
        vm.onScriptReplaced()
        assertEquals(PageShields(blocked = 3, scriptsReplaced = 1), vm.state.value.shields)
        assertEquals(4, vm.state.value.shields.total)
    }

    @Test
    fun `a new document starts from zero`() {
        vm.onDocumentRequested("https://news.example/")
        vm.onBlocked()
        vm.onDocumentRequested("https://news.example/next")
        assertEquals(0, vm.state.value.shields.total)
    }

    @Test
    fun `a blocked main frame is counted on its own page`() {
        vm.onDocumentRequested("https://ads.example/")
        vm.onBlocked()
        vm.onPageStarted("https://ads.example/")
        assertEquals(1, vm.state.value.shields.total)
    }

    @Test
    fun `a page restored without a request does not keep the old counts`() {
        vm.onDocumentRequested("https://news.example/next")
        vm.onPageStarted("https://news.example/next")
        vm.onBlocked()
        // Back from the cache: no request reaches Shields for this document.
        vm.onPageStarted("https://news.example/")
        assertEquals(0, vm.state.value.shields.total)
    }

    @Test
    fun `home clears the counts`() {
        vm.onDocumentRequested("https://news.example/")
        vm.onBlocked()
        vm.onHome()
        assertEquals(0, vm.state.value.shields.total)
    }

    @Test
    fun `turning shields off remembers the site and reloads the page`() = runBlocking {
        vm.onPageStarted("https://www.news.example/article")
        assertFalse(vm.state.value.shieldsOffForSite)

        vm.onSiteShieldsToggled()
        assertTrue(vm.state.value.shieldsOffForSite)
        assertEquals(setOf("news.example"), siteShields.off)
        vm.reloads.first()

        vm.onSiteShieldsToggled()
        assertFalse(vm.state.value.shieldsOffForSite)
        assertEquals(emptySet<String>(), siteShields.off)
    }

    @Test
    fun `a page on a site with shields off starts with them off`() {
        siteShields.off += "news.example"
        vm.onPageStarted("https://m.news.example/")
        assertTrue(vm.state.value.shieldsOffForSite)
        vm.onPageStarted("https://other.example/")
        assertFalse(vm.state.value.shieldsOffForSite)
    }

    @Test
    fun `the start page has no site to switch`() {
        vm.onPageStarted(HOME_URL)
        vm.onSiteShieldsToggled()
        assertFalse(vm.state.value.shieldsOffForSite)
        assertEquals(emptySet<String>(), siteShields.off)
    }

    @Test
    fun `history changes update back availability`() {
        assertFalse(vm.state.value.canGoBack)
        vm.onHistoryChanged(true)
        assertTrue(vm.state.value.canGoBack)
    }
}
