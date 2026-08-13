package fr.scanneat.presentation.settings.components

import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.BuildConfig
import fr.scanneat.CrashLogger
import fr.scanneat.R
import fr.scanneat.domain.engine.scoring.ENGINE_VERSION
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun AboutSection(
    onShowLicenses: () -> Unit,
    onNoCrashLog: () -> Unit,
    // User-requested: "mode debug export pour signaler un bug avec les
    // dernières actions" - theme/language/colorblindMode/apiMode/
    // recentScanSummaries are the app-state context a bug report actually
    // needs (what was configured, what was scanned right before the bug),
    // passed in rather than re-injecting SettingsViewModel's dependencies
    // here - SettingsScreen already collects every one of these StateFlows
    // for its own controls.
    theme: String,
    language: String,
    colorblindMode: String,
    apiMode: String,
    recentScanSummaries: List<String>,
) {
    val context = LocalContext.current
    SettingsSection(stringResource(R.string.settings_section_about), icon = Icons.Default.Info) {
        Text(stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME, ENGINE_VERSION), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
        Text(stringResource(R.string.settings_about_sdk), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
        // colors= explicit: without it, TextButton's ripple/indication layer
        // uses Material3's default colorScheme.primary-derived tint - the
        // label text below was already manually colored AccentCoral, but
        // the ripple wasn't, a half-themed button. Same fix applied to
        // every other TextButton in this file below.
        TextButton(onClick = onShowLicenses, contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.textButtonColors(contentColor = AccentCoral)) {
            Text(stringResource(R.string.settings_about_licenses_button), style = MaterialTheme.typography.bodySmall, color = AccentCoral)
        }
        // CrashLogger.install() (ScanEatApp.kt) persists uncaught exceptions to
        // last_crash.txt, but nothing anywhere ever surfaced that file to the
        // user - it just sat in internal storage a normal user has no way to
        // reach, making the whole crash-logging feature write-only. Shared as
        // plain text (not a file attachment) so no FileProvider/manifest
        // <provider> entry is needed, mirroring ResultScreen's existing
        // ACTION_SEND text-share pattern.
        TextButton(
            onClick = {
                val log = CrashLogger.readLastCrash(context)
                if (log.isNullOrBlank()) {
                    // Routed through the screen's own snackbar (onNoCrashLog, wired to
                    // ScanEatSnackbarHost's announced/liveRegion snackbar) rather than a
                    // bare Toast - a Toast gives TalkBack users no reliable announcement.
                    onNoCrashLog()
                } else {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, log)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, null))
                }
            },
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = AccentCoral),
        ) {
            Text(stringResource(R.string.settings_about_share_crash_log), style = MaterialTheme.typography.bodySmall, color = AccentCoral)
        }
        // Broader than the crash-log share above: bundles app/device info,
        // current settings, and the last few scans so a bug report has real
        // reproduction context even when nothing actually crashed (a wrong
        // score, a UI glitch) - the crash-log share only ever has something
        // to send after an uncaught exception.
        val versionLine = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME, ENGINE_VERSION)
        val sdkLine = stringResource(R.string.settings_about_sdk)
        val diagnosticHeader = stringResource(R.string.settings_diagnostic_header)
        val diagnosticSettingsLine = stringResource(R.string.settings_diagnostic_settings, theme, language, colorblindMode, apiMode)
        val diagnosticRecentScansHeader = stringResource(R.string.settings_diagnostic_recent_scans_header)
        val diagnosticNoRecentScans = stringResource(R.string.settings_diagnostic_no_recent_scans)
        val diagnosticCrashHeader = stringResource(R.string.settings_diagnostic_crash_header)
        val diagnosticNoCrash = stringResource(R.string.settings_diagnostic_no_crash)
        TextButton(
            onClick = {
                val report = buildString {
                    appendLine(diagnosticHeader)
                    appendLine(versionLine)
                    appendLine(sdkLine)
                    appendLine(diagnosticSettingsLine)
                    appendLine()
                    appendLine(diagnosticRecentScansHeader)
                    if (recentScanSummaries.isEmpty()) appendLine(diagnosticNoRecentScans)
                    else recentScanSummaries.forEach { appendLine("- $it") }
                    appendLine()
                    appendLine(diagnosticCrashHeader)
                    append(CrashLogger.readLastCrash(context) ?: diagnosticNoCrash)
                }
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, report)
                }
                context.startActivity(Intent.createChooser(sendIntent, null))
            },
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = AccentCoral),
        ) {
            Text(stringResource(R.string.settings_about_export_diagnostic), style = MaterialTheme.typography.bodySmall, color = AccentCoral)
        }
    }
}

/** Every dependency this app ships in its release APK (test/debug-only artifacts like JUnit/Espresso/MockK excluded — they never reach a user's device) with its license, for Play Store OSS-attribution compliance. Static, not derived from the version catalog at build time, since the catalog has no license metadata to derive from — verify this list by hand against gradle/libs.versions.toml when dependencies change. */
private val OSS_LIBRARIES = listOf(
    "Kotlin Coroutines" to "Apache License 2.0",
    "Kotlin Serialization" to "Apache License 2.0",
    "AndroidX Core / AppCompat / Splashscreen" to "Apache License 2.0",
    "AndroidX Work" to "Apache License 2.0",
    "Jetpack Compose" to "Apache License 2.0",
    "AndroidX Navigation" to "Apache License 2.0",
    "AndroidX Lifecycle" to "Apache License 2.0",
    "Dagger Hilt" to "Apache License 2.0",
    "AndroidX Room" to "Apache License 2.0",
    "AndroidX DataStore" to "Apache License 2.0",
    "Retrofit (Square)" to "Apache License 2.0",
    "OkHttp (Square)" to "Apache License 2.0",
    "Moshi (Square)" to "Apache License 2.0",
    "AndroidX CameraX" to "Apache License 2.0",
    "AndroidX Health Connect" to "Apache License 2.0",
    "AndroidX Glance" to "Apache License 2.0",
    "Guava" to "Apache License 2.0",
    "Haze (Chris Banes)" to "Apache License 2.0",
    "Google ML Kit — Barcode Scanning" to "Google APIs Terms of Service (proprietary)",
    // Bundled font resources (res/font/) - shipped in the APK like any other
    // asset, so they belong on this same attribution list. OpenDyslexic was
    // already shipped (Settings > Accessibility > dyslexic font) but missing
    // here; Caveat (Google Fonts, SIL OFL) added alongside it for the
    // Notebook theme's handwritten accent typeface.
    "OpenDyslexic" to "SIL Open Font License 1.1",
    "Caveat (Google Fonts)" to "SIL Open Font License 1.1",
    // Confirmed free for personal AND commercial use in writing by the
    // author (Khurasan) at download time - unlike "Rainy Calm" (the user's
    // first choice, Personal Use Only, correctly excluded) and several
    // other candidate handwriting fonts whose commercial terms couldn't be
    // confirmed and were excluded pending the user's decision.
    "Mayonice (Khurasan)" to "Free for personal & commercial use",
    "Foxlite Script (Khurasan)" to "Free for personal & commercial use",
    // Added per explicit user instruction after being told the commercial
    // license status is unconfirmed - dafont marks it with a € badge
    // (their "needs a commercial license" marker, not the green "100%
    // Free" tag), and dafont.com/fontget.com aren't reachable from this
    // build environment's network egress to check the exact wording
    // directly. Listed honestly rather than mislabeled as "free" like its
    // two Khurasan neighbors above.
    "I eat crayons (FontPanda)" to "License unconfirmed - used at the app owner's own risk",
    // Doodle icon set (res/drawable-nodpi/doodle_*.png), individually
    // cropped from the single licensed sheet below - Vecteezy's Free
    // License requires attribution rather than a specific license name, so
    // this entry IS that attribution (not a real license name, unlike every
    // other row here) - see Vecteezy's own license terms for what "Free
    // License" permits.
    "Doodle icon set — Vecteezy.com" to "Vecteezy Free License (attribution)",
)

@Composable
internal fun OssLicensesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant.copy(alpha = StandardCardAlpha),
        modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
        shape = RoundedCornerShape(CardRadius.PROMINENT),
        title = { Text(stringResource(R.string.settings_licenses_dialog_title), color = OnBackground) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.S),
            ) {
                items(OSS_LIBRARIES, key = { it.first }) { (name, license) ->
                    Column {
                        Text(name, style = MaterialTheme.typography.bodyMedium, color = OnBackground)
                        Text(license, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
                    }
                }
                item {
                    Text(
                        stringResource(R.string.settings_licenses_apache_note),
                        style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f),
                        modifier = Modifier.padding(top = Spacing.S),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close), color = AccentCoral) } },
    )
}
