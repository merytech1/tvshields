package io.github.merytech1.tvshields.shields

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RedirectResourceTest {

    @Test
    fun `a javascript resource is decoded as utf-8 text`() {
        // "(function() {'use strict';})();", the noop.js the engine test uses.
        val resource = RedirectResource.fromDataUrl(
            "data:application/javascript;base64,KGZ1bmN0aW9uKCkgeyd1c2Ugc3RyaWN0Jzt9KSgpOw==",
        )!!
        assertEquals("application/javascript", resource.mimeType)
        assertEquals("utf-8", resource.encoding)
        assertEquals("(function() {'use strict';})();", resource.bytes.decodeToString())
    }

    @Test
    fun `an image keeps its bytes and has no charset`() {
        // A 1x1 GIF header: "GIF89a" followed by binary data.
        val resource = RedirectResource.fromDataUrl("data:image/gif;base64,R0lGODlhAQABAIAAAP///wAAACH5BAEAAAAALAAAAAABAAEAAAICRAEAOw==")!!
        assertEquals("image/gif", resource.mimeType)
        assertNull(resource.encoding)
        assertEquals("GIF89a", resource.bytes.copyOfRange(0, 6).decodeToString())
    }

    @Test
    fun `only javascript counts as a replaced script`() {
        assertTrue(RedirectResource("application/javascript", ByteArray(0)).isScript)
        assertTrue(RedirectResource("text/javascript", ByteArray(0)).isScript)
        assertFalse(RedirectResource("image/gif", ByteArray(0)).isScript)
        assertFalse(RedirectResource("text/plain", ByteArray(0)).isScript)
    }

    @Test
    fun `an empty resource is valid`() {
        val resource = RedirectResource.fromDataUrl("data:text/plain;base64,")!!
        assertArrayEquals(ByteArray(0), resource.bytes)
        assertEquals("utf-8", resource.encoding)
    }

    @Test
    fun `anything that is not a base64 data url is rejected`() {
        assertNull(RedirectResource.fromDataUrl("https://ads.example/ad.js"))
        assertNull(RedirectResource.fromDataUrl("data:text/plain,hello"))
        assertNull(RedirectResource.fromDataUrl("data:;base64,aGVsbG8="))
        assertNull(RedirectResource.fromDataUrl("data:text/plain;base64"))
        assertNull(RedirectResource.fromDataUrl("data:text/plain;base64,not base64!"))
    }
}
