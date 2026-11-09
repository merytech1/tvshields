package io.github.merytech1.tvshields.shields

import io.github.merytech1.tvshields.shields.RequestType.DOCUMENT
import io.github.merytech1.tvshields.shields.RequestType.FONT
import io.github.merytech1.tvshields.shields.RequestType.IMAGE
import io.github.merytech1.tvshields.shields.RequestType.MEDIA
import io.github.merytech1.tvshields.shields.RequestType.OTHER
import io.github.merytech1.tvshields.shields.RequestType.SCRIPT
import io.github.merytech1.tvshields.shields.RequestType.STYLESHEET
import io.github.merytech1.tvshields.shields.RequestType.SUB_FRAME
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestTypeGuesserTest {

    // Accept headers as the reference TV's WebView sends them (scripts, fonts and fetch send */*).
    private val pageAccept = "text/html,application/xhtml+xml,application/xml;q=0.9,image/jxl,image/avif," +
        "image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7"
    private val imageAccept = "image/jxl,image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
    private val cssAccept = "text/css,*/*;q=0.1"

    private fun guess(url: String, mainFrame: Boolean = false, accept: String? = "*/*") =
        RequestTypeGuesser.guess(url, mainFrame, accept)

    @Test
    fun `rule 1 - main frame is a document whatever else it looks like`() {
        assertEquals(DOCUMENT, guess("https://example.com/", mainFrame = true, accept = pageAccept))
        assertEquals(DOCUMENT, guess("https://example.com/app.js", mainFrame = true, accept = imageAccept))
    }

    @Test
    fun `rule 2 - accept text css is a stylesheet`() {
        assertEquals(STYLESHEET, guess("https://cdn.example/theme", accept = cssAccept))
    }

    @Test
    fun `rule 2 - accept image is an image`() {
        assertEquals(IMAGE, guess("https://cdn.example/pixel?id=1", accept = imageAccept))
        assertEquals(IMAGE, guess("https://cdn.example/a", accept = "image/png"))
    }

    @Test
    fun `rule 2 - accept text html outside the main frame is a sub frame`() {
        assertEquals(SUB_FRAME, guess("https://ads.example/frame", accept = pageAccept))
    }

    @Test
    fun `rule 2 - only the first accept type counts`() {
        // The page Accept lists image types too; it must not become an image.
        assertEquals(SUB_FRAME, guess("https://ads.example/banner.png", accept = pageAccept))
    }

    @Test
    fun `rule 2 - accept wins over the extension`() {
        assertEquals(IMAGE, guess("https://cdn.example/x.js", accept = imageAccept))
    }

    @Test
    fun `rule 2 - accept is case insensitive and tolerates spaces and parameters`() {
        assertEquals(STYLESHEET, guess("https://cdn.example/a", accept = " Text/CSS ;q=1, */*"))
    }

    @Test
    fun `rule 3 - scripts by extension`() {
        assertEquals(SCRIPT, guess("https://cdn.example/ads.js"))
        assertEquals(SCRIPT, guess("https://cdn.example/module.mjs?v=3"))
        assertEquals(SCRIPT, guess("https://cdn.example/APP.JS#top"))
    }

    @Test
    fun `rule 3 - stylesheets by extension`() {
        assertEquals(STYLESHEET, guess("https://cdn.example/site.css?v=2"))
    }

    @Test
    fun `rule 3 - images by extension`() {
        listOf("png", "jpg", "jpeg", "gif", "webp", "avif", "svg", "ico", "bmp").forEach {
            assertEquals(it, IMAGE, guess("https://cdn.example/a.$it"))
        }
    }

    @Test
    fun `rule 3 - media by extension`() {
        listOf("mp4", "m4s", "webm", "m3u8", "mpd", "ts", "mp3", "m4a", "aac").forEach {
            assertEquals(it, MEDIA, guess("https://video.example/seg/1.$it"))
        }
    }

    @Test
    fun `rule 3 - fonts by extension`() {
        listOf("woff", "woff2", "ttf", "otf", "eot").forEach {
            assertEquals(it, FONT, guess("https://fonts.example/f.$it"))
        }
    }

    @Test
    fun `rule 3 - the extension comes from the path, not the host or query`() {
        assertEquals(OTHER, guess("https://ads.example.js"))
        assertEquals(OTHER, guess("https://ads.example.js/"))
        assertEquals(OTHER, guess("https://api.example/track?file=x.js"))
        assertEquals(OTHER, guess("https://api.example/v1.2/track"))
    }

    @Test
    fun `rule 4 - anything else is other`() {
        assertEquals(OTHER, guess("https://api.example/collect", accept = "*/*"))
        assertEquals(OTHER, guess("https://api.example/data.json", accept = "application/json"))
        assertEquals(OTHER, guess("https://api.example/beacon", accept = null))
    }

    @Test
    fun `wire names match adblock-rust`() {
        assertEquals(
            listOf("document", "sub_frame", "stylesheet", "script", "image", "font", "media", "other"),
            RequestType.entries.map { it.wireName },
        )
    }
}
