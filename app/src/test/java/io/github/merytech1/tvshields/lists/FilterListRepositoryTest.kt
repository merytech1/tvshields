package io.github.merytech1.tvshields.lists

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class FilterListRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** Fake list server: path -> current body, ETag and Last-Modified. */
    private class Served(val body: String, val etag: String?, val lastModified: String?)

    private val served = ConcurrentHashMap<String, Served>()
    private val bodiesSent = AtomicInteger()
    private val failing = ConcurrentHashMap.newKeySet<String>()
    private lateinit var server: HttpServer

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val entry = served[path]
            val headers = exchange.requestHeaders
            when {
                path in failing || entry == null -> exchange.sendResponseHeaders(500, -1)
                entry.etag != null && headers.getFirst("If-None-Match") == entry.etag ->
                    exchange.sendResponseHeaders(304, -1)
                entry.etag == null && entry.lastModified != null &&
                    headers.getFirst("If-Modified-Since") == entry.lastModified ->
                    exchange.sendResponseHeaders(304, -1)
                else -> {
                    entry.etag?.let { exchange.responseHeaders.add("ETag", it) }
                    entry.lastModified?.let { exchange.responseHeaders.add("Last-Modified", it) }
                    val bytes = entry.body.toByteArray()
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                    bodiesSent.incrementAndGet()
                }
            }
            exchange.close()
        }
        server.start()
    }

    @After
    fun stopServer() = server.stop(0)

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    private fun repository(resources: Boolean = false) = FilterListRepository(
        dir = tmp.root.resolve("lists"),
        lists = listOf(
            FilterList("a", "List A", listOf(ListSource(url("/a.txt"), "A"), ListSource(url("/b.txt"), "B"))),
        ),
        resourcesUrl = if (resources) url("/resources.json") else null,
    )

    @Test
    fun `first update downloads every list`() = runBlocking {
        served["/a.txt"] = Served("||ads.example^", "\"a1\"", null)
        served["/b.txt"] = Served("##.banner", "\"b1\"", null)

        val result = repository().update()

        assertEquals(listOf(url("/a.txt"), url("/b.txt")), result.downloaded)
        assertTrue(result.changed)
        assertEquals(listOf("||ads.example^", "##.banner"), repository().storedLists().map { it.text })
    }

    @Test
    fun `unchanged lists are not downloaded again`() = runBlocking {
        served["/a.txt"] = Served("||ads.example^", "\"a1\"", null)
        served["/b.txt"] = Served("##.banner", null, "Mon, 09 Nov 2026 10:00:00 GMT")
        repository().update()
        val sentBefore = bodiesSent.get()

        val result = repository().update()

        assertEquals(listOf(url("/a.txt"), url("/b.txt")), result.unchanged)
        assertFalse(result.changed)
        assertEquals(sentBefore, bodiesSent.get())
        assertEquals(listOf("||ads.example^", "##.banner"), repository().storedLists().map { it.text })
    }

    @Test
    fun `a changed list is downloaded again`() = runBlocking {
        served["/a.txt"] = Served("||ads.example^", "\"a1\"", null)
        served["/b.txt"] = Served("##.banner", "\"b1\"", null)
        repository().update()

        served["/a.txt"] = Served("||ads.example^\n||more.example^", "\"a2\"", null)
        val result = repository().update()

        assertEquals(listOf(url("/a.txt")), result.downloaded)
        assertEquals(listOf(url("/b.txt")), result.unchanged)
        assertEquals("||ads.example^\n||more.example^", repository().storedLists().first().text)
    }

    @Test
    fun `a failing source keeps its previous copy`() = runBlocking {
        served["/a.txt"] = Served("||ads.example^", "\"a1\"", null)
        served["/b.txt"] = Served("##.banner", "\"b1\"", null)
        repository().update()

        failing += "/a.txt"
        val result = repository().update()

        assertEquals(listOf(url("/a.txt")), result.failed)
        assertEquals(listOf("||ads.example^", "##.banner"), repository().storedLists().map { it.text })
    }

    @Test
    fun `an unreachable server fails without throwing`() = runBlocking {
        server.stop(0)
        val result = repository().update()
        assertEquals(2, result.failed.size)
        assertTrue(repository().storedLists().isEmpty())
    }

    @Test
    fun `fingerprint changes only when a list is downloaded again`() = runBlocking {
        served["/a.txt"] = Served("||ads.example^", "\"a1\"", null)
        served["/b.txt"] = Served("##.banner", "\"b1\"", null)
        repository().update()
        val first = repository().fingerprint()

        repository().update()
        assertTrue(first.contentEquals(repository().fingerprint()))

        served["/a.txt"] = Served("||ads.example^\n||more.example^", "\"a2\"", null)
        repository().update()
        assertFalse(first.contentEquals(repository().fingerprint()))
    }

    @Test
    fun `update reports progress per catalog entry`() = runBlocking {
        served["/a.txt"] = Served("||ads.example^", "\"a1\"", null)
        served["/b.txt"] = Served("##.banner", "\"b1\"", null)
        val repository = FilterListRepository(
            dir = tmp.root.resolve("lists"),
            lists = listOf(
                FilterList("a", "List A", listOf(ListSource(url("/a.txt"), "A"))),
                FilterList("b", "List B", listOf(ListSource(url("/b.txt"), "B"), ListSource(url("/a.txt"), "A again"))),
            ),
            resourcesUrl = null,
        )
        val progress = mutableListOf<Pair<Int, Int>>()
        assertFalse(repository.hasStoredLists())

        val result = repository.update { done, total -> progress += done to total }

        assertEquals(listOf(1 to 2, 2 to 2), progress)
        // A source shared by two entries is fetched once.
        assertEquals(2, result.downloaded.size)
        assertTrue(repository.hasStoredLists())
    }

    @Test
    fun `resources are downloaded after the lists, outside the list progress`() = runBlocking {
        served["/a.txt"] = Served("||ads.example^", "\"a1\"", null)
        served["/b.txt"] = Served("##.banner", "\"b1\"", null)
        served["/resources.json"] = Served("[]", "\"r1\"", null)
        val progress = mutableListOf<Pair<Int, Int>>()

        val result = repository(resources = true).update { done, total -> progress += done to total }

        assertTrue(url("/resources.json") in result.downloaded)
        assertEquals(listOf(1 to 1), progress)
        assertEquals("[]", repository(resources = true).storedResources())
        // Resources are not part of the cached engine, so they do not change the fingerprint.
        assertTrue(repository().fingerprint().contentEquals(repository(resources = true).fingerprint()))
    }

    @Test
    fun `no resources until they are downloaded`() = runBlocking {
        served["/a.txt"] = Served("||ads.example^", "\"a1\"", null)
        served["/b.txt"] = Served("##.banner", "\"b1\"", null)

        val result = repository(resources = true).update()

        assertEquals(listOf(url("/resources.json")), result.failed)
        assertEquals(null, repository(resources = true).storedResources())
    }

    @Test
    fun `the same content without a 304 counts as unchanged`() = runBlocking {
        served["/a.txt"] = Served("||ads.example^", null, null)
        served["/b.txt"] = Served("##.banner", null, null)
        repository().update()

        val result = repository().update()

        assertEquals(listOf(url("/a.txt"), url("/b.txt")), result.unchanged)
        assertFalse(result.changed)
    }

    @Test
    fun `lists of trusted entries are trusted`() = runBlocking {
        served["/a.txt"] = Served("||ads.example^", "\"a1\"", null)
        served["/b.txt"] = Served("##.banner", "\"b1\"", null)
        val repository = FilterListRepository(
            dir = tmp.root.resolve("lists"),
            lists = listOf(
                FilterList("a", "List A", listOf(ListSource(url("/a.txt"), "A")), trusted = true),
                FilterList("b", "List B", listOf(ListSource(url("/b.txt"), "B"))),
            ),
            resourcesUrl = null,
        )
        repository.update()

        assertEquals(listOf(true, false), repository.storedLists().map { it.trusted })
    }

    @Test
    fun `default catalog has no duplicate or insecure urls`() {
        val urls = DefaultLists.all.flatMap { it.sources }.map { it.url } + DefaultLists.RESOURCES_URL
        assertEquals(urls.size, urls.toSet().size)
        assertTrue(urls.all { it.startsWith("https://") })
    }
}
