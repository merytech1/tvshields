package io.github.merytech1.tvshields.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.merytech1.tvshields.shields.SiteShields
import io.github.merytech1.tvshields.shields.SiteShieldsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Sites with Shields off (architecture §8), kept in DataStore. Requests read the in-memory copy,
 * never the disk; a change applies at once and is written in the background, in order.
 */
class SiteShieldsRepository(
    store: DataStore<Preferences>,
    scope: CoroutineScope,
) : SiteShieldsStore {

    private val _offSites = MutableStateFlow<Set<String>>(emptySet())
    val offSites: StateFlow<Set<String>> = _offSites.asStateFlow()

    private val writer = OrderedWriter(store, scope)

    init {
        scope.launch {
            // An unreadable file must not stop browsing: Shields stay on everywhere.
            val stored = writer.readOnce()[OFF_SITES].orEmpty()
            // Sites switched before the read are kept.
            _offSites.update { stored + it }
        }
    }

    override fun isOff(url: String): Boolean = SiteShields.isOff(_offSites.value, url)

    override fun setOff(site: String, off: Boolean) {
        _offSites.update { if (off) it + site else it - site }
        writer.write { prefs ->
            val current = prefs[OFF_SITES].orEmpty()
            prefs[OFF_SITES] = if (off) current + site else current - site
        }
    }

    private companion object {
        val OFF_SITES = stringSetPreferencesKey("shields_off_sites")
    }
}
