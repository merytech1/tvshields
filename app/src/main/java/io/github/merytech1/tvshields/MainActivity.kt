package io.github.merytech1.tvshields

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.merytech1.tvshields.browser.BrowserScreen
import io.github.merytech1.tvshields.browser.BrowserViewModel
import io.github.merytech1.tvshields.engine.allowsBrowsing
import io.github.merytech1.tvshields.shields.FirstLaunchScreen
import io.github.merytech1.tvshields.shields.FirstLaunchViewModel
import io.github.merytech1.tvshields.ui.theme.TvShieldsTheme

/** The single Activity (architecture §3.1); screens are composables. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as TvShieldsApplication).container
        setContent {
            TvShieldsTheme {
                val setup: FirstLaunchViewModel = viewModel(factory = FirstLaunchViewModel.factory(container.engineLoader))
                val status by setup.status.collectAsState()
                if (status.allowsBrowsing) {
                    val browser: BrowserViewModel = viewModel(factory = BrowserViewModel.factory(container.searchResolver))
                    BrowserScreen(browser, container.shields, container.cosmetics)
                } else {
                    FirstLaunchScreen(status, onRetry = setup::retry, onContinue = setup::continueUnprotected)
                }
            }
        }
    }
}
