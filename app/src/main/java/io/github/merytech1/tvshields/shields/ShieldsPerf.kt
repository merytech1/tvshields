package io.github.merytech1.tvshields.shields

import java.util.concurrent.atomic.AtomicLong

/**
 * Timing counters for what Shields cost per page (rules T7, roadmap 4.4 and 8.4). Off by default:
 * a disabled instance only runs the measured block. Thread-safe.
 *
 * @param isUiThread tells engine work done on the UI thread apart, since that is what can stutter.
 * @param log receives one summary line per page.
 */
class ShieldsPerf(
    private val enabled: Boolean,
    private val isUiThread: () -> Boolean = { false },
    private val log: (String) -> Unit = {},
) {
    enum class Op(val label: String) {
        /** One network request checked by the engine (any WebView thread). */
        REQUEST("requests"),

        /** URL-specific cosmetic resources for a page. */
        COSMETIC("cosmetic"),

        /** One batch of classes and ids from the page (background thread). */
        GENERIC("generic"),
    }

    private class Counter {
        val count = AtomicLong()
        val nanos = AtomicLong()
        val maxNanos = AtomicLong()
        val uiNanos = AtomicLong()

        fun add(elapsed: Long, ui: Boolean) {
            count.incrementAndGet()
            nanos.addAndGet(elapsed)
            if (ui) uiNanos.addAndGet(elapsed)
            while (true) {
                val max = maxNanos.get()
                if (elapsed <= max || maxNanos.compareAndSet(max, elapsed)) break
            }
        }

        fun reset() = listOf(count, nanos, maxNanos, uiNanos).forEach { it.set(0) }
    }

    private val counters = Op.entries.associateWith { Counter() }
    private val pageStart = AtomicLong()

    fun <T> measure(op: Op, block: () -> T): T {
        if (!enabled) return block()
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            counters.getValue(op).add(System.nanoTime() - start, isUiThread())
        }
    }

    fun pageStarted() {
        if (enabled) pageStart.set(System.nanoTime())
    }

    /** Logs the page's totals and starts over. Requests made after this count for the next line. */
    fun pageFinished(url: String) {
        if (!enabled) return
        val started = pageStart.getAndSet(0)
        val load = if (started == 0L) "?" else ((System.nanoTime() - started) / 1_000_000).toString()
        val parts = Op.entries.joinToString("; ") { op ->
            val c = counters.getValue(op)
            val line = "${op.label} n=${c.count.get()} total=${ms(c.nanos.get())} ms " +
                "max=${ms(c.maxNanos.get())} ms ui=${ms(c.uiNanos.get())} ms"
            c.reset()
            line
        }
        log("load=$load ms; $parts; $url")
    }

    private fun ms(nanos: Long) = "%.2f".format(java.util.Locale.ROOT, nanos / 1_000_000.0)

    companion object {
        val Disabled = ShieldsPerf(enabled = false)
    }
}
