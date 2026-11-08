package io.github.merytech1.tvshields

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.merytech1.tvshields.browser.BrowserScreen
import io.github.merytech1.tvshields.browser.BrowserViewModel
import io.github.merytech1.tvshields.ui.theme.TvShieldsTheme

/** The single Activity (architecture §3.1); screens are composables. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as TvShieldsApplication).container
        setContent {
            TvShieldsTheme {
                val browser: BrowserViewModel = viewModel(factory = BrowserViewModel.factory(container.searchResolver))
                BrowserScreen(browser)
            }
        }
    }
}
