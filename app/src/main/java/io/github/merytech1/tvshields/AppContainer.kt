package io.github.merytech1.tvshields

import android.content.Context
import android.util.Log
import io.github.merytech1.tvshields.engine.AdblockEngine
import io.github.merytech1.tvshields.engine.EngineRepository
import io.github.merytech1.tvshields.engine.NativeAdblockEngine
import io.github.merytech1.tvshields.lists.FilterListRepository
import io.github.merytech1.tvshields.search.SearchEngine
import io.github.merytech1.tvshields.search.SearchResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Creates and holds the app-wide dependencies (repositories, engine, Shields manager).
 * Each feature adds its pieces here as it lands.
 */
class AppContainer(val context: Context) {

    /** Work that outlives any screen, such as loading the engine. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Brave Search until the search engine setting exists (roadmap 6.3).
    val searchResolver = SearchResolver { SearchEngine.BRAVE }

    val filterLists = FilterListRepository(File(context.filesDir, "lists"))

    val engines = EngineRepository(File(context.filesDir, "engine.dat"), filterLists, NativeAdblockEngine)

    private val _engine = MutableStateFlow<AdblockEngine?>(null)

    /** The blocking engine, null until loaded (or while there are no lists, rules C1). */
    val engine: StateFlow<AdblockEngine?> = _engine.asStateFlow()

    fun loadEngine() {
        appScope.launch {
            val loaded = try {
                engines.load()
            } catch (e: RuntimeException) {
                Log.e(TAG, "Engine load failed", e)
                null
            }
            if (loaded == null) {
                Log.w(TAG, "No filter lists available; Shields have no rules")
                return@launch
            }
            Log.i(TAG, "Engine ${loaded.source.name.lowercase()} in ${loaded.millis} ms")
            _engine.value = loaded.engine
        }
    }

    private companion object {
        const val TAG = "tvshields.engine"
    }
}
