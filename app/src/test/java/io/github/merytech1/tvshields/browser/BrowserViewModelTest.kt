package io.github.merytech1.tvshields.browser

import io.github.merytech1.tvshields.search.SearchEngine
import io.github.merytech1.tvshields.search.SearchResolver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserViewModelTest {

    private val vm = BrowserViewModel(SearchResolver { SearchEngine.BRAVE })

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
    fun `history changes update back availability`() {
        assertFalse(vm.state.value.canGoBack)
        vm.onHistoryChanged(true)
        assertTrue(vm.state.value.canGoBack)
    }
}
