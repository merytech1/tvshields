package io.github.merytech1.tvshields.engine

import io.github.merytech1.tvshields.lists.FilterListStore
import io.github.merytech1.tvshields.util.writeAtomically
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** An engine ready to use, where it came from and how long it took. */
data class LoadedEngine(val engine: AdblockEngine, val source: Source, val millis: Long) {
    enum class Source { CACHE, BUILT }
}

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
     * downloads them first. Returns null if there are still no lists (offline first launch, rules C1).
     */
    suspend fun load(): LoadedEngine? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val start = System.nanoTime()
            readCache(lists.fingerprint())?.let { return@withContext LoadedEngine(it, LoadedEngine.Source.CACHE, since(start)) }

            var texts = lists.storedLists()
            if (texts.isEmpty()) {
                lists.update()
                texts = lists.storedLists()
            }
            if (texts.isEmpty()) return@withContext null

            val engine = factory.fromLists(texts)
            writeCache(engine, lists.fingerprint())
            LoadedEngine(engine, LoadedEngine.Source.BUILT, since(start))
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

        /** Bump when the way the engine is built changes, to discard old caches. */
        const val FORMAT_VERSION: Byte = 1
    }
}
