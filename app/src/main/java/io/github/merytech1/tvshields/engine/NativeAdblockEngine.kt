package io.github.merytech1.tvshields.engine

import io.github.merytech1.tvshields.lists.StoredList
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicBoolean

/** [AdblockEngine] backed by one native engine handle. */
class NativeAdblockEngine private constructor(private val handle: Long) : AdblockEngine {
    private val closed = AtomicBoolean(false)

    override fun checkRequest(url: String, sourceUrl: String, requestType: String, method: String): NetworkDecision {
        val result = NativeEngine.nativeCheckRequest(handle, url, sourceUrl, requestType, method)
            ?: return NetworkDecision.Allow
        return when (result[0]) {
            'B' -> NetworkDecision.Block
            'R' -> NetworkDecision.Redirect(result.substring(1))
            'W' -> NetworkDecision.Rewrite(result.substring(1))
            else -> NetworkDecision.Allow
        }
    }

    override fun cosmeticResources(url: String): String = NativeEngine.nativeCosmeticResources(handle, url)

    override fun hiddenClassIdSelectors(
        classes: List<String>,
        ids: List<String>,
        exceptions: List<String>,
    ): List<String> {
        val json = NativeEngine.nativeHiddenSelectors(
            handle,
            classes.toTypedArray(),
            ids.toTypedArray(),
            exceptions.toTypedArray(),
        )
        val selectors = JSONArray(json)
        return (0 until selectors.length()).mapNotNull { (selectors.opt(it) as? String)?.takeIf(String::isNotBlank) }
    }

    override fun serialize(): ByteArray = NativeEngine.nativeSerialize(handle)

    override fun useResources(json: String): Int = NativeEngine.nativeUseResources(handle, json)

    /** Releases the native engine. Calls still running finish safely (the engine is reference counted). */
    override fun close() {
        if (closed.compareAndSet(false, true)) NativeEngine.nativeDestroy(handle)
    }

    companion object Factory : AdblockEngineFactory {
        override fun fromLists(lists: List<StoredList>): AdblockEngine = NativeAdblockEngine(
            NativeEngine.nativeCreate(
                lists.map { it.text }.toTypedArray(),
                ByteArray(lists.size) { if (lists[it].trusted) TRUSTED else 0 },
            ),
        )

        /** Permission bit of uBO's trusted scriptlets (engine/src/engine.rs `TRUSTED`). */
        private const val TRUSTED: Byte = 1

        override fun deserialize(bytes: ByteArray): AdblockEngine =
            NativeAdblockEngine(NativeEngine.nativeDeserialize(bytes))
    }
}
