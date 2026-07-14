package fr.scanneat.presentation

import android.content.pm.ActivityInfo
import android.os.Bundle
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
import fr.scanneat.presentation.shell.TopTab
import fr.scanneat.presentation.ui.theme.ScanEatTheme

// AppCompatActivity (not plain ComponentActivity) is required here: AppCompatDelegate's
// per-app language backport for API 26-32 only applies the locale to activities it
// controls via attachBaseContext. Compose works identically on either base class, so
// this was silently making Settings > Language a no-op on most of this app's minSdk range.
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val splashViewModel: SplashViewModel by viewModels()

    // Health Connect's "See app's privacy policy" link (androidx.health.ACTION_SHOW_
    // PERMISSIONS_RATIONALE on API 34+, VIEW_PERMISSION_USAGE on 26-33 via the
    // manifest's activity-alias) launched the app with no regard for the intent at
    // all, dropping the user on whatever tab they'd normally land on instead of
    // anywhere near the privacy disclosures Settings > Mentions légales actually has.
    private val isPrivacyRationaleIntent: Boolean
        get() = intent?.action == "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" ||
            intent?.action == android.content.Intent.ACTION_VIEW_PERMISSION_USAGE

    // Static launcher shortcuts (res/xml/shortcuts.xml) each carry a distinct custom
    // action matched against the intent-filters added to this activity's manifest
    // entry - same "route by action" mechanism the privacy-rationale intent above
    // already uses, extended to jump straight to a tab instead of always landing on
    // whichever one the user last had open.
    private val shortcutStartRoute: String?
        get() = when (intent?.action) {
            "fr.scanneat.action.SHORTCUT_SCAN"      -> TopTab.Scan.route
            "fr.scanneat.action.SHORTCUT_DIARY"     -> TopTab.Diary.route
            "fr.scanneat.action.SHORTCUT_DASHBOARD" -> TopTab.Dashboard.route
            else -> null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                    MainShell(
                        startOnboarding = splashViewModel.needsOnboarding,
                        startRoute      = if (isPrivacyRationaleIntent) TopTab.Settings.route else shortcutStartRoute,
                    )
                }
            }
        }
    }
}
