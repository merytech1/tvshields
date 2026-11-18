package io.github.merytech1.tvshields.settings

import io.github.merytech1.tvshields.lists.DefaultLists
import io.github.merytech1.tvshields.lists.OptionalLists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AboutTest {

    @Test
    fun `the credited adblock-rust version is the one built`() {
        val lock = File("../engine/Cargo.lock").readText()
        val version = Regex("""name = "adblock"\s+version = "([^"]+)"""").find(lock)!!.groupValues[1]
        assertEquals(version, About.ADBLOCK_VERSION)
    }

    @Test
    fun `the bundled notices are current`() {
        val notices = File("src/main/assets/${About.LICENSES_ASSET}").readText()
        val lock = File("../engine/Cargo.lock").readText()
        val locked = Regex("""name = "([^"]+)"\s+version = "([^"]+)"""").findAll(lock).map { "${it.groupValues[1]} ${it.groupValues[2]}" }.toSet()
        val listed = Regex("""(?m)^([a-z0-9_-]+) (\d+\.\d+\.\d+\S*)$""").findAll(notices).map { it.value }.toList()
        assertTrue("adblock ${About.ADBLOCK_VERSION}" in listed)
        // A crate updated in Cargo.lock means the notices must be generated again (tools/licenses).
        assertEquals(emptyList<String>(), listed.filter { it !in locked })
    }

    @Test
    fun `update hosts are those of the enabled lists and the resources`() {
        val defaults = About.updateHosts(DefaultLists.all)
        assertEquals(
            listOf(
                "raw.githubusercontent.com", "easylist.to", "malware-filter.gitlab.io", "secure.fanboy.co.nz", "github.com",
            ),
            defaults,
        )
        val spanish = OptionalLists.regional.first { it.title == "Spanish website ad blocker" }
        val withSpanish = About.updateHosts(OptionalLists.withEnabled(setOf(spanish.id)))
        assertEquals(defaults.dropLast(1) + "easylist-downloads.adblockplus.org" + "github.com", withSpanish)
    }
}
