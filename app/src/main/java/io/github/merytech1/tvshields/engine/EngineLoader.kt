package io.github.merytech1.tvshields.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

/** What [EngineLoader.refresh] did, as Settings reports it. */
enum class ListRefresh {
    /** A new engine is in use (some lists may still have failed: they keep their old copy). */
    UPDATED,

    /** Nothing new, and every list answered. */
    UP_TO_DATE,

    /** Nothing new, but some lists could not be checked. */
    PARTLY_FAILED,

    /** No list could be reached: most likely no connection. */
    OFFLINE,

    /** The engine could not be built. */
    FAILED,
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
    /** How long a replaced engine stays alive for requests that already picked it up. */
    private val closeDelayMs: Long = 10_000,
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
            log("Engine ${loaded.source.name.lowercase()} in ${loaded.millis} ms, ${loaded.resources} resources")
            // A refresh may already have swapped in a newer engine; keep that one.
            if (!_engine.compareAndSet(null, loaded.engine)) loaded.engine.close()
            _status.value = EngineStatus.Ready
            // Lists stored by a version without resources (roadmap 5.1), or resources whose
            // download failed: fetch them now instead of waiting for the daily update.
            if (loaded.resources == 0) refresh()
        }
    }

    /**
     * Updates the lists and, if they changed, swaps in a new engine (roadmap 3.6). Requests keep
     * using the old engine until the swap, then the new one: browsing never waits and never sees
     * no engine. Lists that arrive after "Continue without protection" turn Shields on.
     * Also applies lists turned on or off in Settings. Never throws for engine errors.
     */
    suspend fun refresh(): ListRefresh =
        // Once started it finishes, even if the caller is cancelled: an engine built and cached
        // but never swapped in would leave the old one in use until the next launch.
        withContext(NonCancellable) { refreshNow() }

    private suspend fun refreshNow(): ListRefresh {
        val result = try {
            repository.refresh(rebuild = _engine.value == null)
        } catch (e: RuntimeException) {
            log("List update failed: $e")
            return ListRefresh.FAILED
        }
        val update = result.update
        log("Lists: ${update.downloaded.size} downloaded, ${update.unchanged.size} unchanged, ${update.failed.size} failed")
        val loaded = result.engine ?: return when {
            update.failed.isEmpty() -> ListRefresh.UP_TO_DATE
            update.unchanged.isEmpty() && update.downloaded.isEmpty() -> ListRefresh.OFFLINE
            else -> ListRefresh.PARTLY_FAILED
        }
        val old = _engine.getAndUpdate { loaded.engine }
        log("Engine rebuilt in ${loaded.millis} ms, ${loaded.resources} resources, and swapped")
        // While the first-launch screen is up, its own load finishes the job.
        if (_status.value == EngineStatus.Unprotected || _status.value == EngineStatus.Ready) {
            _status.value = EngineStatus.Ready
        }
        if (old != null) {
            scope.launch {
                delay(closeDelayMs)
                old.close()
            }
        }
        return ListRefresh.UPDATED
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
