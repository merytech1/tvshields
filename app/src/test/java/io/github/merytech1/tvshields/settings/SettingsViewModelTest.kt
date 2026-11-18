package io.github.merytech1.tvshields.settings

import io.github.merytech1.tvshields.engine.ListRefresh
import io.github.merytech1.tvshields.search.SearchEngine
import io.github.merytech1.tvshields.ui.theme.Accent
import io.github.merytech1.tvshields.ui.theme.UiMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SettingsViewModelTest {

    /** Keeps the settings in memory, as the repository does before writing. */
    private class FakeSettings : SettingsStore {
        override val settings = MutableStateFlow<Settings?>(Settings())
        private fun change(block: (Settings) -> Settings) {
            settings.value = block(settings.value!!)
        }
        override fun setUiMode(mode: UiMode) = change { it.copy(uiMode = mode) }
        override fun setAccent(accent: Accent) = change { it.copy(accent = accent) }
        override fun setListEnabled(id: String, enabled: Boolean) =
            change { it.copy(enabledLists = if (enabled) it.enabledLists + id else it.enabledLists - id) }
        override fun setSearchEngine(engine: SearchEngine) = change { it.copy(searchEngine = engine) }
    }

    private val store = FakeSettings()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshes = AtomicInteger()
    private var result = ListRefresh.UPDATED

    /** Results for the next refreshes, in order; [result] after that. */
    private val results = ArrayDeque<ListRefresh>()
    private var gate: CompletableDeferred<Unit>? = null

    private val vm = SettingsViewModel(
        store,
        refreshLists = {
            refreshes.incrementAndGet()
            val answer = synchronized(results) { results.removeFirstOrNull() } ?: result
            gate?.await()
            answer
        },
        toggleDelayMs = 100,
        scope = scope,
    )

    @After
    fun stop() = scope.cancel()

    private fun awaitLists(status: ListsStatus) = runBlocking {
        withTimeout(5_000) { vm.state.first { it.lists == status } }
    }

    @Test
    fun `mode and accent apply at once`() {
        vm.onUiMode(UiMode.LIGHT)
        vm.onAccent(Accent.EMERALD)
        assertEquals(Settings(UiMode.LIGHT, Accent.EMERALD), store.settings.value)
        runBlocking { withTimeout(5_000) { vm.state.first { it.settings.uiMode == UiMode.LIGHT } } }
    }

    @Test
    fun `the search engine applies at once and needs no list update`() {
        vm.onSearchEngine(SearchEngine.DUCKDUCKGO)
        assertEquals(SearchEngine.DUCKDUCKGO, store.settings.value!!.searchEngine)
        runBlocking { withTimeout(5_000) { vm.state.first { it.settings.searchEngine == SearchEngine.DUCKDUCKGO } } }
        assertEquals(0, refreshes.get())
    }

    @Test
    fun `toggles in a row are saved at once and share one update`() {
        vm.onListToggled("spanish")
        vm.onListToggled("newsletter")
        vm.onListToggled("newsletter")
        assertEquals(setOf("spanish"), store.settings.value!!.enabledLists)

        awaitLists(ListsStatus.UPDATED)
        assertEquals(1, refreshes.get())
    }

    @Test
    fun `update lists starts at once and says how it went`() {
        result = ListRefresh.OFFLINE
        vm.onUpdateLists()
        awaitLists(ListsStatus.OFFLINE)

        result = ListRefresh.UP_TO_DATE
        vm.onUpdateLists()
        awaitLists(ListsStatus.UP_TO_DATE)
        assertEquals(2, refreshes.get())
    }

    @Test
    fun `update lists is ignored while one runs`() {
        gate = CompletableDeferred()
        vm.onUpdateLists()
        awaitLists(ListsStatus.UPDATING)
        vm.onUpdateLists()

        gate!!.complete(Unit)
        awaitLists(ListsStatus.UPDATED)
        assertEquals(1, refreshes.get())
    }

    @Test
    fun `a switch made during an update gets its own update and the status sums both up`() {
        gate = CompletableDeferred()
        results += listOf(ListRefresh.UPDATED, ListRefresh.UP_TO_DATE)
        vm.onListToggled("spanish")
        awaitLists(ListsStatus.UPDATING)
        vm.onListToggled("newsletter")
        runBlocking { withTimeout(5_000) { while (refreshes.get() < 2) kotlinx.coroutines.delay(10) } }

        gate!!.complete(Unit)
        // The second update found nothing new because the first already had it.
        awaitLists(ListsStatus.UPDATED)
        assertEquals(2, refreshes.get())
    }
}
