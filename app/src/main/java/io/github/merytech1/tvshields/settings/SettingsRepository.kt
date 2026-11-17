package io.github.merytech1.tvshields.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.merytech1.tvshields.ui.theme.Accent
import io.github.merytech1.tvshields.ui.theme.UiMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The user's choices in Settings (roadmap 6.3). */
data class Settings(
    val uiMode: UiMode = UiMode.DARK,
    val accent: Accent = Accent.PURPLE,
    /** Ids of the enabled [io.github.merytech1.tvshields.lists.OptionalLists]. */
    val enabledLists: Set<String> = emptySet(),
)

/** Reads and changes [Settings]. A change applies at once; the write happens in the background. */
interface SettingsStore {
    /** Null until the stored settings have been read. */
    val settings: StateFlow<Settings?>

    /** Waits for the first read. */
    suspend fun current(): Settings = settings.filterNotNull().first()

    fun setUiMode(mode: UiMode)

    fun setAccent(accent: Accent)

    fun setListEnabled(id: String, enabled: Boolean)
}

/**
 * [SettingsStore] kept in the app's DataStore (architecture §8), next to the per-site switch.
 * The file is read once; after that this class is its only writer, so memory is the truth and
 * writes go out one by one, in the order the changes were made.
 */
class SettingsRepository(
    store: DataStore<Preferences>,
    scope: CoroutineScope,
) : SettingsStore {

    private val _settings = MutableStateFlow<Settings?>(null)
    override val settings: StateFlow<Settings?> = _settings.asStateFlow()

    private val writer = OrderedWriter(store, scope)

    init {
        scope.launch {
            val stored = read(writer.readOnce())
            // A change made before the read already holds the newest values.
            _settings.compareAndSet(null, stored)
        }
    }

    override fun setUiMode(mode: UiMode) = change({ it.copy(uiMode = mode) }) { it[UI_MODE] = mode.name }

    override fun setAccent(accent: Accent) = change({ it.copy(accent = accent) }) { it[ACCENT] = accent.name }

    override fun setListEnabled(id: String, enabled: Boolean) = change(
        { it.copy(enabledLists = if (enabled) it.enabledLists + id else it.enabledLists - id) },
    ) { prefs ->
        val current = prefs[ENABLED_LISTS].orEmpty()
        prefs[ENABLED_LISTS] = if (enabled) current + id else current - id
    }

    private fun change(inMemory: (Settings) -> Settings, onDisk: (MutablePreferences) -> Unit) {
        _settings.update { inMemory(it ?: Settings()) }
        writer.write(onDisk)
    }

    private companion object {
        val UI_MODE = stringPreferencesKey("ui_mode")
        val ACCENT = stringPreferencesKey("accent")
        val ENABLED_LISTS = stringSetPreferencesKey("enabled_lists")

        /** Unknown values (a newer version's, or a damaged file) fall back to the defaults. */
        fun read(prefs: Preferences) = Settings(
            uiMode = UiMode.entries.firstOrNull { it.name == prefs[UI_MODE] } ?: UiMode.DARK,
            accent = Accent.entries.firstOrNull { it.name == prefs[ACCENT] } ?: Accent.PURPLE,
            enabledLists = prefs[ENABLED_LISTS].orEmpty(),
        )
    }
}
