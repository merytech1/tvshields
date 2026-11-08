package io.github.merytech1.tvshields

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.merytech1.tvshields.ui.start.StartScreen
import io.github.merytech1.tvshields.ui.theme.TvShieldsTheme

/** The single Activity (architecture §3.1); screens are composables. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TvShieldsTheme {
                StartScreen()
            }
        }
    }
}
