package com.qrzzzz.lyricscard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.qrzzzz.lyricscard.ui.AppViewModel
import com.qrzzzz.lyricscard.ui.LyricsCardApp
import com.qrzzzz.lyricscard.ui.theme.LyricsCardTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferences by viewModel.preferences.collectAsState()
            LyricsCardTheme(darkTheme = preferences.darkMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LyricsCardApp(viewModel)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch { viewModel.flushAutosave() }
    }
}
