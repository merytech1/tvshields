package io.github.merytech1.tvshields.lists

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListCreditsTest {

    private val sources = (DefaultLists.all + OptionalLists.all).flatMap { it.sources }

    @Test
    fun `every list source has an author and a license`() {
        val missing = sources.filter { ListCredits.of(it) == null }.map { it.url }
        assertEquals(emptyList<String>(), missing)
        assertTrue(sources.mapNotNull(ListCredits::of).all { it.author.isNotBlank() && it.license.isNotBlank() })
    }

    @Test
    fun `the most specific prefix wins`() {
        val arabic = OptionalLists.regional.flatMap { it.sources }.first { it.url.endsWith("/Liste_AR.txt") }
        val spanish = OptionalLists.regional.flatMap { it.sources }.first { it.url.endsWith("/easylistspanish.txt") }
        assertEquals("CC-BY-SA-3.0", ListCredits.of(arabic)!!.license)
        assertEquals("The EasyList authors", ListCredits.of(spanish)!!.author)
    }

    @Test
    fun `sources are grouped by credit in list order`() {
        val groups = ListCredits.grouped(DefaultLists.all.first())
        assertEquals(listOf("uBlock Origin", "The EasyList authors", "malware-filter, from URLhaus (abuse.ch)", "Brave Software"), groups.map { it.first.author })
        assertEquals(DefaultLists.all.first().sources.size, groups.sumOf { it.second.size })
    }
}
