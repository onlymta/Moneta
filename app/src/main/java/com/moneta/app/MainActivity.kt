package com.moneta.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.moneta.app.data.settings.ThemeMode
import com.moneta.app.ui.navigation.MonetaApp
import com.moneta.app.ui.settings.SettingsViewModel
import com.moneta.app.ui.theme.MonetaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MonetaRoot()
        }
    }
}

/**
 * Reads the persisted theme preference (Section 25) and applies it - SYSTEM
 * follows the device setting as before, LIGHT/DARK force one or the other.
 * This is the one place that setting actually changes app behavior, rather
 * than only being stored.
 */
@Composable
private fun MonetaRoot(settingsViewModel: SettingsViewModel = hiltViewModel()) {
    val prefs by settingsViewModel.preferences.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (prefs.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MonetaTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            MonetaApp()
        }
    }
}
