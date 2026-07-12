package fr.scanneat.presentation

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.SideEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

        // This is the app's only Activity, and every screen behind it can show
        // health/nutrition history or a raw API key (Settings) — none of that
        // belongs in a screenshot, screen recording, or the Recents thumbnail.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        // The manifest locks phones to portrait, which is the right call for this
        // UI - but on tablets/foldables (sw >= 600dp) a fixed-orientation activity
        // gets letterboxed/pillarboxed by the OS's large-screen compat handling
        // instead of filling the window. Releasing the lock there avoids that,
        // even though the layout itself isn't width-adaptive yet.
        if (resources.configuration.smallestScreenWidthDp >= 600) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        splashScreen.setKeepOnScreenCondition { !splashViewModel.ready.value }

        setContent {
            val ready = splashViewModel.ready.collectAsStateWithLifecycle().value
            if (ready) {
                val theme = splashViewModel.theme.collectAsStateWithLifecycle().value
                val dyslexicFont = splashViewModel.dyslexicFont.collectAsStateWithLifecycle().value
                val colorblindMode = splashViewModel.colorblindMode.collectAsStateWithLifecycle().value
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
