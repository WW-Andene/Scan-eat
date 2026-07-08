package fr.scanneat.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import dagger.hilt.android.AndroidEntryPoint
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.presentation.shell.MainShell
import fr.scanneat.presentation.ui.theme.ScanEatTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Single blocking read on first frame — determines start destination only
        val apiKey    = runBlocking { prefs.groqApiKey.first() }
        val serverUrl = runBlocking { prefs.serverUrl.first() }
        val needsOnboarding = apiKey.isBlank() && serverUrl.isBlank()

        setContent {
            val theme = prefs.theme.collectAsState(initial = "oled").value
            ScanEatTheme(theme = theme) {
                MainShell(startOnboarding = needsOnboarding, theme = theme)
            }
        }
    }
}
