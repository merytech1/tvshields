package io.github.merytech1.tvshields.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** JNI smoke test (roadmap 2.5): loads libtvshields_engine.so on the device and calls every kind of entry point. */
@RunWith(AndroidJUnit4::class)
class NativeEngineTest {

    private val page = "https://news.example.com/article"
    private val rules = "||ads.example.net^\nnews.example.com##.sponsored\n##.ad-banner"
    private var handle = 0L

    @Before
    fun create() {
        handle = NativeEngine.nativeCreate(arrayOf(rules))
    }

    @After
    fun destroy() {
        NativeEngine.nativeDestroy(handle)
    }

    private fun check(engine: Long, url: String) =
        NativeEngine.nativeCheckRequest(engine, url, page, "script", "GET")

    @Test
    fun blocksAndAllowsRequests() {
        assertEquals("B", check(handle, "https://ads.example.net/banner.js"))
        assertNull(check(handle, "https://cdn.example.org/app.js"))
    }

    @Test
    fun returnsCosmeticSelectors() {
        val json = NativeEngine.nativeCosmeticResources(handle, page)
        assertTrue(json, json.contains(".sponsored"))
    }

    @Test
    fun survivesSerialization() {
        val copy = NativeEngine.nativeDeserialize(NativeEngine.nativeSerialize(handle))
        try {
            assertEquals("B", check(copy, "https://ads.example.net/banner.js"))
        } finally {
            NativeEngine.nativeDestroy(copy)
        }
    }

    @Test
    fun unknownHandleThrows() {
        val gone = NativeEngine.nativeCreate(arrayOf(rules))
        NativeEngine.nativeDestroy(gone)
        assertThrows(RuntimeException::class.java) { check(gone, "https://ads.example.net/x.js") }
    }
}
