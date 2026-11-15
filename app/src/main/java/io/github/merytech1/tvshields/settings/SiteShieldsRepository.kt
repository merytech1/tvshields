package io.github.merytech1.tvshields.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.merytech1.tvshields.shields.SiteShields
import io.github.merytech1.tvshields.shields.SiteShieldsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Sites with Shields off (architecture §8), kept in DataStore. Requests read the in-memory copy,
 * never the disk; a change applies at once and is written in the background.
 */
class SiteShieldsRepository(
    private val store: DataStore<Preferences>,
    private val scope: CoroutineScope,
) : SiteShieldsStore {

    private val _offSites = MutableStateFlow<Set<String>>(emptySet())
    val offSites: StateFlow<Set<String>> = _offSites.asStateFlow()

    init {
        scope.launch {
            store.data
                // An unreadable file must not stop browsing: Shields stay on everywhere.
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .collect { _offSites.value = it[OFF_SITES].orEmpty() }
        }
    }

    override fun isOff(url: String): Boolean = SiteShields.isOff(_offSites.value, url)

    override fun setOff(site: String, off: Boolean) {
        _offSites.update { if (off) it + site else it - site }
        scope.launch {
            store.edit { prefs ->
                val current = prefs[OFF_SITES].orEmpty()
                prefs[OFF_SITES] = if (off) current + site else current - site
            }
        }
    }

    private companion object {
        val OFF_SITES = stringSetPreferencesKey("shields_off_sites")
    }
}
