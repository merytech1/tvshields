package io.github.merytech1.tvshields.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.merytech1.tvshields.search.SearchResolver
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

data class BrowserUiState(
    val currentUrl: String = "",
    /** 0–100 while a page loads, null otherwise. */
    val progress: Int? = null,
    val canGoBack: Boolean = false,
    val shields: PageShields = PageShields(),
)

/** What Shields stopped on the current page (roadmap 6.1). Ads and trackers come from the same rules, so they share a count. */
data class PageShields(
    val blocked: Int = 0,
    val scriptsReplaced: Int = 0,
) {
    val total: Int get() = blocked + scriptsReplaced
}

/** What the WebView shows for the start page; the screen draws the start page over it. */
const val HOME_URL = "about:blank"

/** Browser screen state. Holds no WebView: the screen applies [loads] and reports page events back. */
class BrowserViewModel(private val searchResolver: SearchResolver) : ViewModel() {

    private val _state = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    private val _loads = Channel<String>(Channel.BUFFERED)
    /** URLs the WebView must load, one event each. */
    val loads: Flow<String> = _loads.receiveAsFlow()

    /** URL of the last main-frame request; a page that starts without one came from the back-forward cache. */
    @Volatile
    private var requestedUrl: String? = null

    /** Returns false when there is nothing to open, so the address bar keeps focus. */
    fun onAddressSubmit(text: String): Boolean {
        val url = searchResolver.resolve(text) ?: return false
        _loads.trySend(url)
        return true
    }

    /** Goes to the start page. It stays in the history, so Back returns to the page left. */
    fun onHome() {
        _state.update { it.copy(currentUrl = "", progress = null, shields = PageShields()) }
        _loads.trySend(HOME_URL)
    }

    fun onPageStarted(url: String) = _state.update {
        // No request means nothing was checked for this document: the old counts do not apply.
        val shields = if (url == requestedUrl) it.shields else PageShields()
        if (url == HOME_URL) it.copy(currentUrl = "", progress = null, shields = shields)
        else it.copy(currentUrl = url, progress = 0, shields = shields)
    }

    /** A new document is requested (WebView background thread): its counts start at zero. */
    fun onDocumentRequested(url: String) {
        requestedUrl = url
        _state.update { it.copy(shields = PageShields()) }
    }

    /** A request was blocked, or replaced by a stand-in that is not a script (WebView background thread). */
    fun onBlocked() = _state.update { it.copy(shields = it.shields.copy(blocked = it.shields.blocked + 1)) }

    /** A script was replaced by a harmless stand-in (WebView background thread). */
    fun onScriptReplaced() =
        _state.update { it.copy(shields = it.shields.copy(scriptsReplaced = it.shields.scriptsReplaced + 1)) }

    fun onProgress(progress: Int) = _state.update { if (it.progress == null) it else it.copy(progress = progress) }

    fun onPageFinished(url: String) = _state.update {
        it.copy(currentUrl = if (url == HOME_URL) "" else url, progress = null)
    }

    fun onHistoryChanged(canGoBack: Boolean) = _state.update { it.copy(canGoBack = canGoBack) }

    companion object {
        fun factory(searchResolver: SearchResolver) = viewModelFactory {
            initializer { BrowserViewModel(searchResolver) }
        }
    }
}
