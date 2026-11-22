package io.github.merytech1.tvshields

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.merytech1.tvshields.browser.BrowserScreen
import io.github.merytech1.tvshields.browser.BrowserViewModel
import io.github.merytech1.tvshields.engine.allowsBrowsing
import io.github.merytech1.tvshields.settings.Settings
import io.github.merytech1.tvshields.settings.SettingsViewModel
import io.github.merytech1.tvshields.shields.FirstLaunchScreen
import io.github.merytech1.tvshields.shields.FirstLaunchViewModel
import io.github.merytech1.tvshields.ui.theme.TvShieldsTheme
import kotlinx.coroutines.launch

/** The single Activity (architecture §3.1); screens are composables. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as TvShieldsApplication).container
        // The first frame already has the chosen mode and accent: no flash of the default theme.
        lifecycleScope.launch {
            container.settings.current()
            show(container)
        }
    }

    private fun show(container: AppContainer) {
        setContent {
            val appearance by container.settings.settings.collectAsState()
            val current = appearance ?: Settings()
            TvShieldsTheme(mode = current.uiMode, accent = current.accent) {
                val setup: FirstLaunchViewModel = viewModel(factory = FirstLaunchViewModel.factory(container.engineLoader))
                val status by setup.status.collectAsState()
                if (status.allowsBrowsing) {
                    val browser: BrowserViewModel = viewModel(factory = BrowserViewModel.factory(container.searchResolver, container.siteShields))
                    val settings: SettingsViewModel = viewModel(
                        factory = SettingsViewModel.factory(container.settings, container.engineLoader::refresh),
                    )
                    val engine by container.engineLoader.engine.collectAsState()
                    BrowserScreen(
                        browser,
                        container.shields,
                        container.cosmetics,
                        engineReady = engine != null,
                        settings = settings,
                        window = window,
                    )
                } else {
                    FirstLaunchScreen(status, onRetry = setup::retry, onContinue = setup::continueUnprotected)
                }
            }
        }
    }
}
