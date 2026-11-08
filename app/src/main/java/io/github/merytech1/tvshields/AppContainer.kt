package io.github.merytech1.tvshields

import android.content.Context
import io.github.merytech1.tvshields.search.SearchEngine
import io.github.merytech1.tvshields.search.SearchResolver

/**
 * Creates and holds the app-wide dependencies (repositories, engine, Shields manager).
 * Each feature adds its pieces here as it lands.
 */
class AppContainer(val context: Context) {

    // Brave Search until the search engine setting exists (roadmap 6.3).
    val searchResolver = SearchResolver { SearchEngine.BRAVE }
}
