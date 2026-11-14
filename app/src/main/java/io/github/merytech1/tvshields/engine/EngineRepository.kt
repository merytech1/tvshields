package io.github.merytech1.tvshields.engine

import io.github.merytech1.tvshields.lists.FilterListStore
import io.github.merytech1.tvshields.lists.UpdateResult
import io.github.merytech1.tvshields.util.writeAtomically
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * An engine ready to use, where it came from, how long it took (resources included) and how many
 * resources it loaded.
 */
data class LoadedEngine(val engine: AdblockEngine, val source: Source, val millis: Long, val resources: Int = 0) {
    enum class Source { CACHE, BUILT }
}

/** Outcome of [EngineRepository.refresh]. */
data class RefreshResult(val update: UpdateResult, val engine: LoadedEngine?)

/**
 * Provides the blocking engine, cached in [file] (`filesDir/engine.dat`, rules T8, architecture §5.4).
 *
 * The cache starts with a header: [MAGIC], [FORMAT_VERSION] and the fingerprint of the lists it was
 * built from. It is used only if the header matches the stored lists and the native engine accepts
 * it (adblock-rust rejects data from another crate version). Otherwise the engine is built from the
 * lists and the cache is written again.
 */
class EngineRepository(
    private val file: File,
    private val lists: FilterListStore,
    private val factory: AdblockEngineFactory,
) {
    private val mutex = Mutex()

    /**
     * Loads the engine from the cache, or builds it from the stored lists. With no lists stored,
     * downloads them first, reporting [onProgress]. Returns null if there are still no lists
     * (offline first launch, rules C1).
     */
    suspend fun load(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): LoadedEngine? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val start = System.nanoTime()
            readCache(lists.fingerprint())?.let {
                val resources = useResources(it)
                return@withContext LoadedEngine(it, LoadedEngine.Source.CACHE, since(start), resources)
            }

            var texts = lists.storedLists()
            if (texts.isEmpty()) {
                lists.update(onProgress)
                texts = lists.storedLists()
            }
            if (texts.isEmpty()) return@withContext null

            val engine = factory.fromLists(texts)
            writeCache(engine, lists.fingerprint())
            val resources = useResources(engine)
            LoadedEngine(engine, LoadedEngine.Source.BUILT, since(start), resources)
        }
    }

    /**
     * Checks the lists for updates (roadmap 3.6). If any changed, or [rebuild] is set (no engine
     * yet), builds a new engine and rewrites the cache. [RefreshResult.engine] is null when there
     * is nothing new or still no lists.
     */
    suspend fun refresh(rebuild: Boolean = false): RefreshResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val update = lists.update()
            if (!update.changed && !rebuild) return@withContext RefreshResult(update, null)
            val start = System.nanoTime()
            val texts = lists.storedLists()
            if (texts.isEmpty()) return@withContext RefreshResult(update, null)
            val engine = factory.fromLists(texts)
            writeCache(engine, lists.fingerprint())
            val resources = useResources(engine)
            RefreshResult(update, LoadedEngine(engine, LoadedEngine.Source.BUILT, since(start), resources))
        }
    }

    /**
     * Resources are not in the cache, so every engine gets them here, before anyone uses it.
     * Without them (not downloaded yet, or invalid) blocking still works: `redirect=` rules
     * block and scriptlets are skipped.
     */
    private suspend fun useResources(engine: AdblockEngine): Int {
        val json = lists.storedResources() ?: return 0
        return try {
            engine.useResources(json)
        } catch (e: RuntimeException) {
            0
        }
    }

    private fun readCache(fingerprint: ByteArray): AdblockEngine? {
        if (!file.isFile) return null
        val bytes = try {
            file.readBytes()
        } catch (e: IOException) {
            return null
        }
        val header = header(fingerprint)
        if (bytes.size <= header.size || !bytes.copyOfRange(0, header.size).contentEquals(header)) return null
        return try {
            factory.deserialize(bytes.copyOfRange(header.size, bytes.size))
        } catch (e: RuntimeException) {
            file.delete()
            null
        }
    }

    /** A failed write only costs a rebuild on the next launch. */
    private fun writeCache(engine: AdblockEngine, fingerprint: ByteArray) {
        try {
            file.parentFile?.mkdirs()
            file.writeAtomically(header(fingerprint) + engine.serialize())
        } catch (e: IOException) {
            file.delete()
        }
    }

    private fun header(fingerprint: ByteArray) = MAGIC + byteArrayOf(FORMAT_VERSION) + fingerprint

    private fun since(start: Long) = (System.nanoTime() - start) / 1_000_000

    private companion object {
        val MAGIC = "TVSE".toByteArray()

        /**
         * Bump when the way the engine is built changes, to discard old caches.
         * 2: `css-validation` (structured procedural filters, roadmap 4.3).
         * 3: per-list permissions (trusted scriptlets, roadmap 5.1).
         */
        const val FORMAT_VERSION: Byte = 3
    }
}
