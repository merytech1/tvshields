package io.github.merytech1.tvshields

import android.content.Context
import android.os.Looper
import android.util.Log
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.merytech1.tvshields.engine.EngineLoader
import io.github.merytech1.tvshields.engine.EngineRepository
import io.github.merytech1.tvshields.engine.NativeAdblockEngine
import io.github.merytech1.tvshields.lists.FilterListRepository
import io.github.merytech1.tvshields.lists.OptionalLists
import io.github.merytech1.tvshields.search.SearchEngine
import io.github.merytech1.tvshields.search.SearchResolver
import io.github.merytech1.tvshields.settings.SettingsRepository
import io.github.merytech1.tvshields.settings.SiteShieldsRepository
import io.github.merytech1.tvshields.shields.CosmeticFilter
import io.github.merytech1.tvshields.shields.ShieldsManager
import io.github.merytech1.tvshields.shields.ShieldsPerf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * Creates and holds the app-wide dependencies (repositories, engine, Shields manager).
 * Each feature adds its pieces here as it lands.
 */
class AppContainer(val context: Context) {

    /** Work that outlives any screen, such as loading the engine. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Brave Search until the search engine setting exists (roadmap 7.2).
    val searchResolver = SearchResolver { SearchEngine.BRAVE }

    /** General settings and the sites with Shields off (architecture §8). One instance per file. */
    private val settingsStore = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    val settings = SettingsRepository(settingsStore, appScope)

    // The default lists plus those turned on in Settings (roadmap 6.3).
    val filterLists = FilterListRepository(
        File(context.filesDir, "lists"),
        lists = { OptionalLists.withEnabled(settings.current().enabledLists) },
    )

    val engines = EngineRepository(File(context.filesDir, "engine.dat"), filterLists, NativeAdblockEngine)

    val engineLoader = EngineLoader(
        repository = engines,
        hasStoredLists = filterLists::hasStoredLists,
        scope = appScope,
        log = { Log.i(TAG, it) },
    )

    /** `adb shell setprop log.tag.tvshields.perf DEBUG` logs what Shields cost per page (read at start). */
    val perf = ShieldsPerf(
        enabled = Log.isLoggable(PERF_TAG, Log.DEBUG),
        isUiThread = { Looper.myLooper() == Looper.getMainLooper() },
        log = { Log.d(PERF_TAG, it) },
    )

    val siteShields = SiteShieldsRepository(settingsStore, appScope)

    val shields = ShieldsManager(perf, siteShields::isOff) { engineLoader.engine.value }

    val cosmetics = CosmeticFilter(perf, siteShields::isOff) { engineLoader.engine.value }

    init {
        // The first engine calls of a process are the slow ones (cold code and data, ~200 ms on
        // the reference TV). Pay for them here, in the background, not on the first page (T7).
        appScope.launch {
            engineLoader.engine.filterNotNull().first()
            shields.check(WARM_UP_URL, WARM_UP_URL, isForMainFrame = false, accept = null, method = "GET")
            cosmetics.resourcesFor(WARM_UP_URL)
            perf.pageFinished("warm-up")
        }
    }

    private companion object {
        const val TAG = "tvshields.engine"
        const val PERF_TAG = "tvshields.perf"

        /** Never requested: only run through the engine. */
        const val WARM_UP_URL = "https://warm-up.invalid/"
    }
}
