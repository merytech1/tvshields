package io.github.merytech1.tvshields.shields

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CosmeticMessageTest {

    @Test
    fun `parses classes and ids`() {
        val tokens = CosmeticMessage.parse("""{"classes":["ad-banner","promo"],"ids":["sidebar-ad"]}""")

        assertEquals(listOf("ad-banner", "promo"), tokens?.classes)
        assertEquals(listOf("sidebar-ad"), tokens?.ids)
    }

    @Test
    fun `missing lists are empty`() {
        val tokens = CosmeticMessage.parse("""{"classes":["ad"]}""")

        assertEquals(listOf("ad"), tokens?.classes)
        assertEquals(emptyList<String>(), tokens?.ids)
    }

    @Test
    fun `invalid json is rejected`() {
        assertNull(CosmeticMessage.parse("not json"))
    }

    @Test
    fun `oversized messages are rejected`() {
        assertNull(CosmeticMessage.parse(" ".repeat(CosmeticMessage.MAX_MESSAGE_CHARS + 1)))
    }

    @Test
    fun `drops non-string blank and oversized tokens`() {
        val long = "x".repeat(CosmeticMessage.MAX_TOKEN_LENGTH + 1)
        val tokens = CosmeticMessage.parse(
            """{"classes":[1,null,"", "  ", "$long", "ok"],"ids":[]}""",
        )

        assertEquals(listOf("ok"), tokens?.classes)
    }

    @Test
    fun `caps the number of tokens`() {
        val classes = (0 until CosmeticMessage.MAX_TOKENS + 20).joinToString(",") { "\"class-$it\"" }
        val tokens = CosmeticMessage.parse("""{"classes":[$classes],"ids":[]}""")

        assertEquals(CosmeticMessage.MAX_TOKENS, tokens?.classes?.size)
    }

    @Test
    fun `drops duplicate tokens`() {
        val tokens = CosmeticMessage.parse("""{"classes":["ad","ad"],"ids":["ad"]}""")

        assertEquals(listOf("ad"), tokens?.classes)
        assertEquals(listOf("ad"), tokens?.ids)
    }
}
