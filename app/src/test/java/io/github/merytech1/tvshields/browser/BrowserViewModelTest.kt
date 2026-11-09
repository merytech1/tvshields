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
    fun `history changes update back availability`() {
        assertFalse(vm.state.value.canGoBack)
        vm.onHistoryChanged(true)
        assertTrue(vm.state.value.canGoBack)
    }
}
