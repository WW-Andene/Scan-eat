package fr.scanneat.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint
import fr.scanneat.presentation.shell.MainShell
import fr.scanneat.presentation.ui.theme.ScanEatTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val splashViewModel: SplashViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        splashScreen.setKeepOnScreenCondition { !splashViewModel.ready.value }

        setContent {
            val ready = splashViewModel.ready.collectAsState().value
            if (ready) {
                val theme = splashViewModel.theme.collectAsState().value
                SideEffect {
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = theme == "light"
                    insetsController.isAppearanceLightNavigationBars = theme == "light"
                }

                ScanEatTheme(theme = theme) {
                    MainShell(startOnboarding = splashViewModel.needsOnboarding)
                }
            }
        }
    }
}
