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

/** One stored list's text, and whether it may use trusted scriptlets ([FilterList.trusted]). */
data class StoredList(val text: String, val trusted: Boolean = false)

/** The stored filter lists and resources, as the engine cache needs them. */
interface FilterListStore {
    /**
     * [onProgress] gets (catalog entries done, total) after each entry. The resources come last,
     * after the final report: the first-launch screen counts lists only.
     */
    suspend fun update(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): UpdateResult

    /** Quick check (no reading) for at least one stored list. */
    fun hasStoredLists(): Boolean

    suspend fun storedLists(): List<StoredList>

    /** The stored `resources.json` (scriptlets and redirects), or null if never downloaded. */
    suspend fun storedResources(): String? = null

    /** Identifies the stored lists (not the resources: they are not part of the cached engine). */
    suspend fun fingerprint(): ByteArray
}

/**
 * Downloads filter lists and [resourcesUrl] into [dir] (filesDir/lists, architecture §8) and keeps
 * them up to date.
 *
 * Each source is stored as `<hash>.txt` plus a `<hash>.meta` with its `ETag` / `Last-Modified`
 * (the resources as `resources.json` and `resources.meta`). Updates send those back, so an
 * unchanged file costs one 304 response; a 200 with the same content also counts as unchanged.
 * A failed source keeps its previous copy. Files are replaced atomically (temp file, then rename).
 */
class FilterListRepository(
    private val dir: File,
    private val lists: List<FilterList> = DefaultLists.all,
    private val resourcesUrl: String? = DefaultLists.RESOURCES_URL,
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
            fun record(url: String, fetch: Fetch) = when (fetch) {
                Fetch.DOWNLOADED -> downloaded += url
                Fetch.UNCHANGED -> unchanged += url
                Fetch.FAILED -> failed += url
            }
            lists.forEachIndexed { index, list ->
                for (source in list.sources) {
                    if (!seen.add(source.url)) continue
                    record(source.url, fetch(source.url, listFile(source.url), metaFile(source.url)))
                }
                onProgress(index + 1, lists.size)
            }
            if (resourcesUrl != null) record(resourcesUrl, fetch(resourcesUrl, resourcesFile, resourcesMeta))
            UpdateResult(downloaded, unchanged, failed)
        }
    }

    override fun hasStoredLists(): Boolean = sources.any { listFile(it.url).isFile }

    /**
     * Every stored list, in catalog order; sources never downloaded are skipped. A source shared
     * by several entries is trusted if any of them is.
     */
    override suspend fun storedLists(): List<StoredList> = withContext(Dispatchers.IO) {
        val trusted = lists.filter { it.trusted }.flatMap { it.sources }.map { it.url }.toSet()
        sources.map { it.url to listFile(it.url) }
            .filter { (_, file) -> file.isFile }
            .map { (url, file) -> StoredList(file.readText(), trusted = url in trusted) }
    }

    override suspend fun storedResources(): String? = withContext(Dispatchers.IO) {
        if (resourcesUrl != null && resourcesFile.isFile) resourcesFile.readText() else null
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

    private fun fetch(url: String, target: File, metaFile: File): Fetch {
        val meta = if (target.isFile) readMeta(metaFile) else Properties()
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
                    // Some servers never answer 304: the same bytes must not trigger a rebuild.
                    val same = target.isFile && target.readBytes().contentEquals(body)
                    if (!same) target.writeAtomically(body)
                    writeMeta(metaFile, connection.getHeaderField("ETag"), connection.getHeaderField("Last-Modified"))
                    if (same) Fetch.UNCHANGED else Fetch.DOWNLOADED
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

    private val resourcesFile get() = File(dir, "resources.json")

    private val resourcesMeta get() = File(dir, "resources.meta")

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
