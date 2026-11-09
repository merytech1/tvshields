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
)

/** What the WebView shows for the start page; the screen draws the start page over it. */
const val HOME_URL = "about:blank"

/** Browser screen state. Holds no WebView: the screen applies [loads] and reports page events back. */
class BrowserViewModel(private val searchResolver: SearchResolver) : ViewModel() {

    private val _state = MutableStateFlow(BrowserUiState())
    val state: StateFlow<BrowserUiState> = _state.asStateFlow()

    private val _loads = Channel<String>(Channel.BUFFERED)
    /** URLs the WebView must load, one event each. */
    val loads: Flow<String> = _loads.receiveAsFlow()

    /** Returns false when there is nothing to open, so the address bar keeps focus. */
    fun onAddressSubmit(text: String): Boolean {
        val url = searchResolver.resolve(text) ?: return false
        _loads.trySend(url)
        return true
    }

    /** Goes to the start page. It stays in the history, so Back returns to the page left. */
    fun onHome() {
        _state.update { it.copy(currentUrl = "", progress = null) }
        _loads.trySend(HOME_URL)
    }

    fun onPageStarted(url: String) = _state.update {
        if (url == HOME_URL) it.copy(currentUrl = "", progress = null) else it.copy(currentUrl = url, progress = 0)
    }

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
