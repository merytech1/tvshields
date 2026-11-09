package io.github.merytech1.tvshields.shields

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.merytech1.tvshields.engine.EngineLoader
import io.github.merytech1.tvshields.engine.EngineStatus
import kotlinx.coroutines.flow.StateFlow

/** State and actions of the first-launch screen (rules C1). */
class FirstLaunchViewModel(private val loader: EngineLoader) : ViewModel() {

    val status: StateFlow<EngineStatus> = loader.status

    fun retry() = loader.retry()

    fun continueUnprotected() = loader.continueUnprotected()

    companion object {
        fun factory(loader: EngineLoader) = viewModelFactory {
            initializer { FirstLaunchViewModel(loader) }
        }
    }
}
