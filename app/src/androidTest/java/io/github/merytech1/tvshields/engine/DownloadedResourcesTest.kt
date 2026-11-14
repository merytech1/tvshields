package io.github.merytech1.tvshields.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.merytech1.tvshields.lists.StoredList
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Roadmap 5.1 on the device: the `resources.json` the app downloaded loads through JNI, and the
 * engine recognizes `noopjs`, `set-constant` and the SugarCoat resources. Needs the app to have
 * downloaded its lists once (open it with a network before running this test).
 */
@RunWith(AndroidJUnit4::class)
class DownloadedResourcesTest {

    private val page = "https://news.example.com/article"

    private val json: String by lazy {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "lists/resources.json")
        check(file.isFile) { "No ${file.path}: open the app with a network first" }
        file.readText()
    }

    private fun engine(rules: String, trusted: Boolean = false): AdblockEngine =
        NativeAdblockEngine.fromLists(listOf(StoredList(rules, trusted))).also { it.useResources(json) }

    private fun injectedScript(engine: AdblockEngine) =
        JSONObject(engine.cosmeticResources(page)).getString("injected_script")

    @Test
    fun everyResourceIsAccepted() {
        engine("").use { assertEquals(JSONArray(json).length(), it.useResources(json)) }
    }

    @Test
    fun noopjsIsServedAsRedirect() {
        engine("||ads.example.net/ad.js\$script,redirect=noopjs").use { engine ->
            val decision = engine.checkRequest("https://ads.example.net/ad.js", page, "script")
            assertTrue("$decision", decision is NetworkDecision.Redirect)
            assertTrue((decision as NetworkDecision.Redirect).dataUrl.startsWith("data:application/javascript;base64,"))
        }
    }

    @Test
    fun setConstantIsInjectedWithItsHelpers() {
        engine("news.example.com##+js(set-constant, adBlockDetected, false)").use { engine ->
            val script = injectedScript(engine)
            assertTrue(script.contains("function setConstant("))
            assertTrue(script.contains("function safeSelf("))
            assertTrue(script.contains("""setConstant("adBlockDetected", "false")"""))
        }
    }

    @Test
    fun sugarcoatResourcesAreServed() {
        val names = JSONArray(json).let { array -> (0 until array.length()).map { array.getJSONObject(it).getString("name") } }
        val sugarcoat = names.filter { it.startsWith("async-sugarcoat-") }
        assertTrue("no SugarCoat resources", sugarcoat.isNotEmpty())
        for (name in sugarcoat) {
            engine("||tracker.example.net/analytics.js\$script,important,redirect=$name").use { engine ->
                val decision = engine.checkRequest("https://tracker.example.net/analytics.js", page, "script")
                assertTrue("$name: $decision", decision is NetworkDecision.Redirect)
            }
        }
    }

    @Test
    fun trustedScriptletsNeedATrustedList() {
        val rule = "news.example.com##+js(trusted-set-cookie, consent, yes)"
        engine(rule, trusted = true).use { assertTrue(injectedScript(it).contains("trustedSetCookie")) }
        engine(rule, trusted = false).use { assertFalse(injectedScript(it).contains("trustedSetCookie")) }
    }
}
