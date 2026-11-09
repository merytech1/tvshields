package io.github.merytech1.tvshields.lists

import io.github.merytech1.tvshields.util.writeAtomically
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Properties

/** Outcome of one [FilterListRepository.update]: source URLs grouped by what happened to them. */
data class UpdateResult(
    val downloaded: List<String>,
    val unchanged: List<String>,
    /** Kept their previous copy, if any. */
    val failed: List<String>,
) {
    val changed get() = downloaded.isNotEmpty()
}

/** The stored filter lists, as the engine cache needs them. */
interface FilterListStore {
    /** [onProgress] gets (catalog entries done, total) after each entry. */
    suspend fun update(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): UpdateResult

    /** Quick check (no reading) for at least one stored list. */
    fun hasStoredLists(): Boolean

    suspend fun storedLists(): List<String>

    suspend fun fingerprint(): ByteArray
}

/**
 * Downloads filter lists into [dir] (filesDir/lists, architecture §8) and keeps them up to date.
 *
 * Each source is stored as `<hash>.txt` plus a `<hash>.meta` with its `ETag` / `Last-Modified`.
 * Updates send those back, so an unchanged list costs one 304 response and no download. A failed
 * source keeps its previous copy. Files are replaced atomically (write to a temp file, then rename).
 */
class FilterListRepository(
    private val dir: File,
    private val lists: List<FilterList> = DefaultLists.all,
) : FilterListStore {
    private val mutex = Mutex()

    private val sources get() = lists.flatMap { it.sources }.distinctBy { it.url }

    /** Checks every source once. Never throws for network errors: they end up in [UpdateResult.failed]. */
    override suspend fun update(onProgress: (done: Int, total: Int) -> Unit): UpdateResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val downloaded = mutableListOf<String>()
            val unchanged = mutableListOf<String>()
            val failed = mutableListOf<String>()
            val seen = mutableSetOf<String>()
            lists.forEachIndexed { index, list ->
                for (source in list.sources) {
                    if (!seen.add(source.url)) continue
                    when (fetch(source.url)) {
                        Fetch.DOWNLOADED -> downloaded += source.url
                        Fetch.UNCHANGED -> unchanged += source.url
                        Fetch.FAILED -> failed += source.url
                    }
                }
                onProgress(index + 1, lists.size)
            }
            UpdateResult(downloaded, unchanged, failed)
        }
    }

    override fun hasStoredLists(): Boolean = sources.any { listFile(it.url).isFile }

    /** Texts of every stored list, in catalog order; sources never downloaded are skipped. */
    override suspend fun storedLists(): List<String> = withContext(Dispatchers.IO) {
        sources.map { listFile(it.url) }.filter { it.isFile }.map { it.readText() }
    }

    /**
     * Identifies the stored lists without reading them: changes whenever a list is downloaded
     * again (new `ETag` / `Last-Modified` or size). Lets a cached engine tell if it is stale.
     */
    override suspend fun fingerprint(): ByteArray = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        for (source in sources) {
            val list = listFile(source.url)
            if (!list.isFile) continue
            digest.update(source.url.toByteArray())
            digest.update(list.length().toString().toByteArray())
            metaFile(source.url).takeIf { it.isFile }?.let { digest.update(it.readBytes()) }
        }
        digest.digest()
    }

    private enum class Fetch { DOWNLOADED, UNCHANGED, FAILED }

    private fun fetch(url: String): Fetch {
        val list = listFile(url)
        val metaFile = metaFile(url)
        val meta = if (list.isFile) readMeta(metaFile) else Properties()
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return Fetch.FAILED
        }
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            meta.getProperty(ETAG)?.let { connection.setRequestProperty("If-None-Match", it) }
            meta.getProperty(LAST_MODIFIED)?.let { connection.setRequestProperty("If-Modified-Since", it) }

            when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> Fetch.UNCHANGED
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.use { input ->
                        val bytes = input.readNBytesCompat(MAX_LIST_BYTES + 1)
                        if (bytes.size > MAX_LIST_BYTES) return Fetch.FAILED
                        bytes
                    }
                    list.writeAtomically(body)
                    writeMeta(metaFile, connection.getHeaderField("ETag"), connection.getHeaderField("Last-Modified"))
                    Fetch.DOWNLOADED
                }
                else -> Fetch.FAILED
            }
        } catch (e: IOException) {
            Fetch.FAILED
        } finally {
            connection.disconnect()
        }
    }

    private fun name(url: String): String =
        MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }

    private fun listFile(url: String) = File(dir, "${name(url)}.txt")

    private fun metaFile(url: String) = File(dir, "${name(url)}.meta")

    private fun readMeta(file: File) = Properties().apply {
        if (file.isFile) file.inputStream().use { load(it) }
    }

    private fun writeMeta(file: File, etag: String?, lastModified: String?) {
        val props = Properties()
        etag?.let { props.setProperty(ETAG, it) }
        lastModified?.let { props.setProperty(LAST_MODIFIED, it) }
        val bytes = java.io.ByteArrayOutputStream().also { props.store(it, null) }.toByteArray()
        file.writeAtomically(bytes)
    }

    private companion object {
        const val ETAG = "etag"
        const val LAST_MODIFIED = "last-modified"
        const val TIMEOUT_MS = 30_000
        /** Largest list accepted, well above any default list; guards storage against a bad response. */
        const val MAX_LIST_BYTES = 16 * 1024 * 1024
        const val USER_AGENT = "tvshields"
    }
}

/** `InputStream.readNBytes` needs API 33; minSdk is 24. */
private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}
