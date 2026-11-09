package io.github.merytech1.tvshields

import android.content.Context
import android.util.Log
import io.github.merytech1.tvshields.engine.EngineLoader
import io.github.merytech1.tvshields.engine.EngineRepository
import io.github.merytech1.tvshields.engine.NativeAdblockEngine
import io.github.merytech1.tvshields.lists.FilterListRepository
import io.github.merytech1.tvshields.search.SearchEngine
import io.github.merytech1.tvshields.search.SearchResolver
import io.github.merytech1.tvshields.shields.ShieldsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    val engineLoader = EngineLoader(
        repository = engines,
        hasStoredLists = filterLists::hasStoredLists,
        scope = appScope,
        log = { Log.i(TAG, it) },
    )

    val shields = ShieldsManager { engineLoader.engine.value }

    private companion object {
        const val TAG = "tvshields.engine"
    }
}
