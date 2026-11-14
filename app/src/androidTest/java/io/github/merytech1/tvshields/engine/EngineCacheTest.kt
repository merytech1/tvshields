package io.github.merytech1.tvshields.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.merytech1.tvshields.lists.FilterListStore
import io.github.merytech1.tvshields.lists.StoredList
import io.github.merytech1.tvshields.lists.UpdateResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** [EngineRepository] with the real native engine: build, cache, load from cache. */
@RunWith(AndroidJUnit4::class)
class EngineCacheTest {

    private val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "engine-test.dat")

    private val lists = object : FilterListStore {
        override suspend fun update(onProgress: (done: Int, total: Int) -> Unit) =
            UpdateResult(emptyList(), emptyList(), emptyList())

        override fun hasStoredLists() = true
        override suspend fun storedLists() =
            listOf(StoredList("||ads.example.com^"), StoredList("@@||ads.example.com/ok.js"))
        override suspend fun fingerprint() = ByteArray(32) { 7 }
    }

    @After
    fun cleanUp() {
        file.delete()
    }

    @Test
    fun secondLoadUsesTheCacheAndKeepsTheRules() = runBlocking {
        file.delete()
        val built = EngineRepository(file, lists, NativeAdblockEngine).load()!!
        built.engine.close()

        val cached = EngineRepository(file, lists, NativeAdblockEngine).load()!!

        assertEquals(LoadedEngine.Source.BUILT, built.source)
        assertEquals(LoadedEngine.Source.CACHE, cached.source)
        val engine = cached.engine
        assertEquals(
            NetworkDecision.Block,
            engine.checkRequest("https://ads.example.com/ad.js", "https://news.example.org/", "script"),
        )
        assertEquals(
            NetworkDecision.Allow,
            engine.checkRequest("https://ads.example.com/ok.js", "https://news.example.org/", "script"),
        )
        engine.close()
    }
}
