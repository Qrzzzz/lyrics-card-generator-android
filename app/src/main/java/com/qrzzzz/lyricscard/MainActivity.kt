package com.qrzzzz.lyricscard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.qrzzzz.lyricscard.ui.LyricsCardApp
import com.qrzzzz.lyricscard.ui.LyricsCardViewModelFactory
import com.qrzzzz.lyricscard.ui.SettingsViewModel
import com.qrzzzz.lyricscard.ui.theme.LyricsCardTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container: AppContainer
        get() = (application as LyricsCardApplication).container
    private val settingsViewModel: SettingsViewModel by viewModels {
        LyricsCardViewModelFactory(container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
            LyricsCardTheme(darkTheme = settings.preferences.darkMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LyricsCardApp(
                        container = container,
                        settingsViewModel = settingsViewModel,
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch { container.editorSessions.flushActive() }
    }
}
