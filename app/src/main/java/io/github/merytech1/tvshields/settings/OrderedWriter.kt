package io.github.merytech1.tvshields.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException

/** Reads a DataStore once and applies edits strictly in the order they are sent. */
internal class OrderedWriter(private val store: DataStore<Preferences>, scope: CoroutineScope) {
    private val edits = Channel<(MutablePreferences) -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (change in edits) {
                try {
                    store.edit { change(it) }
                } catch (e: IOException) {
                    // Not saved: the change still applies until the app closes.
                }
            }
        }
    }

    /** The stored values; an unreadable file gives none, so the defaults apply. */
    suspend fun readOnce(): Preferences = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .first()

    fun write(edit: (MutablePreferences) -> Unit) {
        edits.trySend(edit)
    }
}
