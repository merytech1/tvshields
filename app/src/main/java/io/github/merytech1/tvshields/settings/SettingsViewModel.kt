package io.github.merytech1.tvshields.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.merytech1.tvshields.engine.ListRefresh
import io.github.merytech1.tvshields.search.SearchEngine
import io.github.merytech1.tvshields.ui.theme.Accent
import io.github.merytech1.tvshields.ui.theme.UiMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/** What the Filter lists section says about the last update started from Settings. */
enum class ListsStatus { IDLE, UPDATING, UPDATED, UP_TO_DATE, PARTLY_FAILED, OFFLINE, FAILED }

data class SettingsUiState(
    val settings: Settings = Settings(),
    val lists: ListsStatus = ListsStatus.IDLE,
)

/**
 * Settings (roadmap 6.3): appearance applies at once through [SettingsStore]; turning a list on
 * or off is saved at once and applied by [refreshLists], which downloads what is missing and
 * rebuilds the engine.
 *
 * @param toggleDelayMs toggles in a row share one update instead of one rebuild each.
 * @param scope becomes [viewModelScope]; tests pass one that needs no main thread.
 */
class SettingsViewModel(
    private val store: SettingsStore,
    private val refreshLists: suspend () -> ListRefresh,
    private val toggleDelayMs: Long = 1_500,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {

    private val lists = MutableStateFlow(ListsStatus.IDLE)
    private var pending: Job? = null

    val state: StateFlow<SettingsUiState> = combine(store.settings.filterNotNull(), lists, ::SettingsUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState(store.settings.value ?: Settings()))

    fun onUiMode(mode: UiMode) = store.setUiMode(mode)

    fun onAccent(accent: Accent) = store.setAccent(accent)

    /** Applies to the next search: the address bar reads the choice each time. */
    fun onSearchEngine(engine: SearchEngine) = store.setSearchEngine(engine)

    fun onListToggled(id: String) {
        // From the store, not [state]: that copy can lag behind a press made a moment ago.
        val enabled = id in (store.settings.value ?: return).enabledLists
        store.setListEnabled(id, !enabled)
        update(toggleDelayMs)
    }

    /** "Update lists": checks every enabled list now. Ignored while an update runs. */
    fun onUpdateLists() {
        if (lists.value == ListsStatus.UPDATING) return
        update(0)
    }

    /** Updates started and not finished; with [updatedInBatch], what the status line sums up. */
    private val running = AtomicInteger()

    @Volatile
    private var updatedInBatch = false

    /**
     * A newer request replaces one still waiting; once an update starts it always finishes
     * (and so does the engine swap, EngineLoader). While updates overlap the status says
     * "Updating"; the last one to end reports for all of them: an error it hit, or "updated" if
     * any of them changed the engine.
     */
    private fun update(delayMs: Long) {
        pending?.cancel()
        pending = viewModelScope.launch {
            delay(delayMs)
            withContext(NonCancellable) {
                running.incrementAndGet()
                lists.value = ListsStatus.UPDATING
                val result = refreshLists()
                if (result == ListRefresh.UPDATED) updatedInBatch = true
                if (running.decrementAndGet() == 0) {
                    lists.value = if (result == ListRefresh.UP_TO_DATE && updatedInBatch) ListsStatus.UPDATED else result.status
                    updatedInBatch = false
                }
            }
        }
    }

    private val ListRefresh.status
        get() = when (this) {
            ListRefresh.UPDATED -> ListsStatus.UPDATED
            ListRefresh.UP_TO_DATE -> ListsStatus.UP_TO_DATE
            ListRefresh.PARTLY_FAILED -> ListsStatus.PARTLY_FAILED
            ListRefresh.OFFLINE -> ListsStatus.OFFLINE
            ListRefresh.FAILED -> ListsStatus.FAILED
        }

    companion object {
        fun factory(store: SettingsStore, refreshLists: suspend () -> ListRefresh) = viewModelFactory {
            initializer { SettingsViewModel(store, refreshLists) }
        }
    }
}
