package io.github.merytech1.tvshields.shields

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShieldsPerfTest {

    @Test
    fun `disabled perf runs the block and logs nothing`() {
        val lines = mutableListOf<String>()
        val perf = ShieldsPerf(enabled = false, log = lines::add)

        assertEquals(42, perf.measure(ShieldsPerf.Op.REQUEST) { 42 })
        perf.pageStarted()
        perf.pageFinished("https://news.example/")

        assertTrue(lines.isEmpty())
    }

    @Test
    fun `a page line counts each operation and then starts over`() {
        val lines = mutableListOf<String>()
        val perf = ShieldsPerf(enabled = true, log = lines::add)

        perf.pageStarted()
        repeat(3) { perf.measure(ShieldsPerf.Op.REQUEST) { } }
        perf.measure(ShieldsPerf.Op.COSMETIC) { }
        perf.pageFinished("https://news.example/")
        perf.pageFinished("https://news.example/next")

        assertTrue(lines[0], lines[0].contains("requests n=3 "))
        assertTrue(lines[0], lines[0].contains("cosmetic n=1 "))
        assertTrue(lines[0], lines[0].contains("generic n=0 "))
        assertTrue(lines[0], lines[0].endsWith("https://news.example/"))
        assertTrue(lines[1], lines[1].contains("requests n=0 "))
    }

    @Test
    fun `time on the ui thread is reported apart`() {
        val lines = mutableListOf<String>()
        var ui = false
        val perf = ShieldsPerf(enabled = true, isUiThread = { ui }, log = lines::add)

        perf.measure(ShieldsPerf.Op.COSMETIC) { Thread.sleep(5) }
        ui = true
        perf.measure(ShieldsPerf.Op.COSMETIC) { Thread.sleep(5) }
        perf.pageFinished("https://news.example/")

        val cosmetic = lines.single().substringAfter("cosmetic ").substringBefore(";")
        val total = cosmetic.substringAfter("total=").substringBefore(" ms").toDouble()
        val onUi = cosmetic.substringAfter("ui=").substringBefore(" ms").toDouble()
        assertTrue(cosmetic, onUi >= 4.0 && onUi < total)
    }

    @Test
    fun `a failing block is still counted and rethrown`() {
        val lines = mutableListOf<String>()
        val perf = ShieldsPerf(enabled = true, log = lines::add)

        val thrown = runCatching { perf.measure(ShieldsPerf.Op.REQUEST) { error("boom") } }
        perf.pageFinished("https://news.example/")

        assertTrue(thrown.isFailure)
        assertTrue(lines.single(), lines.single().contains("requests n=1 "))
    }
}
