package com.qrzzzz.lyricscard

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.qrzzzz.lyricscard.ui.LyricsCardApp
import com.qrzzzz.lyricscard.ui.LyricsCardViewModelFactory
import com.qrzzzz.lyricscard.ui.SettingsViewModel
import com.qrzzzz.lyricscard.ui.theme.LyricsCardTheme
import com.qrzzzz.lyricscard.data.AppThemeMode
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container: AppContainer
        get() = (application as LyricsCardApplication).container
    private val settingsViewModel: SettingsViewModel by viewModels {
        LyricsCardViewModelFactory(container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
            val systemDarkTheme = isSystemInDarkTheme()
            val darkTheme = when (settings.preferences.themeMode) {
                AppThemeMode.SYSTEM -> systemDarkTheme
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            SideEffect { applyEdgeToEdge(darkTheme) }
            LyricsCardTheme(darkTheme = darkTheme) {
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

    private fun applyEdgeToEdge(darkTheme: Boolean) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
                detectDarkMode = { darkTheme },
            ),
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = LIGHT_NAVIGATION_BAR_SCRIM,
                darkScrim = DARK_NAVIGATION_BAR_SCRIM,
                detectDarkMode = { darkTheme },
            ),
        )
    }

    companion object {
        // Mirrors AndroidX's contrast protection for legacy three-button navigation.
        internal const val LIGHT_NAVIGATION_BAR_SCRIM: Int = 0xE6FFFFFF.toInt()
        internal const val DARK_NAVIGATION_BAR_SCRIM: Int = 0x801B1B1B.toInt()
    }
}
