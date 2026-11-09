package io.github.merytech1.tvshields.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where Shields stand while the app starts (rules C1). */
sealed interface EngineStatus {
    /** Loading from stored lists or the cache: browsing can start. */
    data object Loading : EngineStatus

    /** First launch: downloading the lists. Pages wait. */
    data class Downloading(val done: Int, val total: Int) : EngineStatus

    /** No lists and the download failed. Pages wait for a retry or [EngineLoader.continueUnprotected]. */
    data object NoLists : EngineStatus

    data object Ready : EngineStatus

    /** The user chose to browse without lists, or the engine could not be built. */
    data object Unprotected : EngineStatus
}

/** Pages may load in these states; the others show the first-launch screen. */
val EngineStatus.allowsBrowsing get() = this == EngineStatus.Loading || this == EngineStatus.Ready || this == EngineStatus.Unprotected

/**
 * Loads the engine when the app starts and tracks [status]. With no lists stored, the first launch
 * downloads them before any page loads (rules C1).
 *
 * @param hasStoredLists checked once, synchronously, so the first frame already knows whether a
 * download is needed.
 */
class EngineLoader(
    private val repository: EngineRepository,
    hasStoredLists: () -> Boolean,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit = {},
) {
    private val _status = MutableStateFlow<EngineStatus>(
        if (hasStoredLists()) EngineStatus.Loading else EngineStatus.Downloading(0, 0),
    )
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private val _engine = MutableStateFlow<AdblockEngine?>(null)

    /** The blocking engine, null until loaded. */
    val engine: StateFlow<AdblockEngine?> = _engine.asStateFlow()

    fun start() {
        scope.launch {
            val loaded = try {
                repository.load { done, total -> _status.value = EngineStatus.Downloading(done, total) }
            } catch (e: RuntimeException) {
                // The native engine failed: retrying will not help, so browsing goes on unprotected.
                log("Engine load failed: $e")
                _status.value = EngineStatus.Unprotected
                return@launch
            }
            if (loaded == null) {
                log("No filter lists available")
                _status.value = EngineStatus.NoLists
                return@launch
            }
            log("Engine ${loaded.source.name.lowercase()} in ${loaded.millis} ms")
            _engine.value = loaded.engine
            _status.value = EngineStatus.Ready
        }
    }

    fun retry() {
        if (_status.value != EngineStatus.NoLists) return
        _status.value = EngineStatus.Downloading(0, 0)
        start()
    }

    fun continueUnprotected() {
        if (_status.value == EngineStatus.NoLists) _status.value = EngineStatus.Unprotected
    }
}
