package io.github.merytech1.tvshields.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.merytech1.tvshields.search.SearchEngine
import io.github.merytech1.tvshields.ui.theme.Accent
import io.github.merytech1.tvshields.ui.theme.UiMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class SettingsRepositoryTest {

    /**
     * DataStore in memory: on the Windows build machine the real one cannot replace its file on
     * the second write (Android can). What is tested here is the order and the single read.
     */
    private class MemoryStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        private val mutex = Mutex()
        var failWrites = false

        override val data = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            mutex.withLock {
                if (failWrites) throw IOException("disk full")
                transform(state.value).also { state.value = it }
            }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun stop() = scope.cancel()

    private fun current(repository: SettingsRepository) = runBlocking { withTimeout(5_000) { repository.current() } }

    @Test
    fun `a fresh install has dark, purple, no optional lists and Brave Search`() {
        val settings = current(SettingsRepository(MemoryStore(), scope))
        assertEquals(Settings(UiMode.DARK, Accent.PURPLE, emptySet(), SearchEngine.BRAVE), settings)
    }

    @Test
    fun `the chosen search engine persists`() {
        val store = MemoryStore()
        val repository = SettingsRepository(store, scope)
        current(repository)

        repository.setSearchEngine(SearchEngine.STARTPAGE)
        assertEquals(SearchEngine.STARTPAGE, repository.settings.value!!.searchEngine)

        runBlocking { withTimeout(5_000) { store.data.first { it[stringPreferencesKey("search_engine")] == "STARTPAGE" } } }
        assertEquals(SearchEngine.STARTPAGE, current(SettingsRepository(store, scope)).searchEngine)
    }

    @Test
    fun `changes apply at once and are written in order`() {
        val store = MemoryStore()
        val repository = SettingsRepository(store, scope)
        current(repository)

        repository.setUiMode(UiMode.LIGHT)
        repository.setAccent(Accent.EMERALD)
        repository.setListEnabled("spanish", true)
        // On and off at once: the last one must win on disk too.
        repository.setListEnabled("newsletter", true)
        repository.setListEnabled("newsletter", false)
        val expected = Settings(UiMode.LIGHT, Accent.EMERALD, setOf("spanish"))
        assertEquals(expected, repository.settings.value)

        runBlocking {
            withTimeout(5_000) {
                store.data.first { it[stringSetPreferencesKey("enabled_lists")] == setOf("spanish") && it.asMap().size == 3 }
            }
        }
        assertEquals(expected, current(SettingsRepository(store, scope)))
    }

    @Test
    fun `unknown stored values fall back to the defaults`() {
        val store = MemoryStore(
            mutablePreferencesOf(
                stringPreferencesKey("ui_mode") to "SEPIA",
                stringPreferencesKey("accent") to "ORANGE",
                stringPreferencesKey("search_engine") to "ALTAVISTA",
            ),
        )
        assertEquals(Settings(), current(SettingsRepository(store, scope)))
    }

    @Test
    fun `a failed write keeps the change for this session`() {
        val store = MemoryStore().apply { failWrites = true }
        val repository = SettingsRepository(store, scope)
        current(repository)

        repository.setUiMode(UiMode.LIGHT)
        repository.setAccent(Accent.EMERALD)

        assertEquals(Settings(UiMode.LIGHT, Accent.EMERALD), repository.settings.value)
    }
}
