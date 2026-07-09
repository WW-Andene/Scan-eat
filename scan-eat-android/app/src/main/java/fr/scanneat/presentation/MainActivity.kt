package fr.scanneat.presentation

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import dagger.hilt.android.AndroidEntryPoint
import fr.scanneat.presentation.shell.MainShell
import fr.scanneat.presentation.ui.theme.ScanEatTheme

// AppCompatActivity (not plain ComponentActivity) is required here: AppCompatDelegate's
// per-app language backport for API 26-32 only applies the locale to activities it
// controls via attachBaseContext. Compose works identically on either base class, so
// this was silently making Settings > Language a no-op on most of this app's minSdk range.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

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
                val dyslexicFont = splashViewModel.dyslexicFont.collectAsState().value
                val colorblindMode = splashViewModel.colorblindMode.collectAsState().value
                SideEffect {
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = theme == "light"
                    insetsController.isAppearanceLightNavigationBars = theme == "light"
                }

                ScanEatTheme(theme = theme, dyslexicFont = dyslexicFont, colorblindMode = colorblindMode) {
                    MainShell(startOnboarding = splashViewModel.needsOnboarding)
                }
            }
        }
    }
}
