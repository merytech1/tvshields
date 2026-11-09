package io.github.merytech1.tvshields.lists

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
) {
    private val mutex = Mutex()

    private val sources get() = lists.flatMap { it.sources }.distinctBy { it.url }

    /** Checks every source once. Never throws for network errors: they end up in [UpdateResult.failed]. */
    suspend fun update(): UpdateResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val downloaded = mutableListOf<String>()
            val unchanged = mutableListOf<String>()
            val failed = mutableListOf<String>()
            for (source in sources) {
                when (fetch(source.url)) {
                    Fetch.DOWNLOADED -> downloaded += source.url
                    Fetch.UNCHANGED -> unchanged += source.url
                    Fetch.FAILED -> failed += source.url
                }
            }
            UpdateResult(downloaded, unchanged, failed)
        }
    }

    /** Texts of every stored list, in catalog order; sources never downloaded are skipped. */
    suspend fun storedLists(): List<String> = withContext(Dispatchers.IO) {
        sources.map { listFile(it.url) }.filter { it.isFile }.map { it.readText() }
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
                    writeAtomically(list, body)
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
        writeAtomically(file, bytes)
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) throw IOException("Cannot replace $target")
        }
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
