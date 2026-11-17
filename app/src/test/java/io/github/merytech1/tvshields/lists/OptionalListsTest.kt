package io.github.merytech1.tvshields.lists

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OptionalListsTest {

    @Test
    fun `offers the lists named in filter-lists md`() {
        assertEquals(
            listOf("Annoying distractions blocker", "Newsletter popup blocker", "AI suggestions blocker"),
            OptionalLists.annoyances.map { it.title },
        )
        assertEquals(
            listOf("Allow Facebook Embeds", "Allow X/Twitter Embeds", "Allow LinkedIn Embeds"),
            OptionalLists.embeds.map { it.title },
        )
        assertEquals(33, OptionalLists.regional.size)
        val spanish = OptionalLists.regional.filter { "Spanish" in it.title }.map { it.title }
        assertEquals(listOf("Spanish website ad blocker", "Spanish and Portuguese website ad blocker"), spanish)
    }

    @Test
    fun `ids are unique and never clash with the default lists`() {
        val ids = (DefaultLists.all + OptionalLists.all).map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `every source is https`() {
        assertTrue(OptionalLists.all.flatMap { it.sources }.all { it.url.startsWith("https://") })
    }

    @Test
    fun `enabled lists come after the defaults, in catalog order, unknown ids ignored`() {
        val newsletter = OptionalLists.annoyances[1]
        val spanish = OptionalLists.regional.first { it.title == "Spanish website ad blocker" }

        val lists = OptionalLists.withEnabled(setOf(spanish.id, "removed-from-catalog", newsletter.id))

        assertEquals(DefaultLists.all + listOf(newsletter, spanish), lists)
        assertEquals(DefaultLists.all, OptionalLists.withEnabled(emptySet()))
    }
}
