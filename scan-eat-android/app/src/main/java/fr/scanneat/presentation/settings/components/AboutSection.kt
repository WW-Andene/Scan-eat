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
internal fun AboutSection(onShowLicenses: () -> Unit, onNoCrashLog: () -> Unit) {
    val context = LocalContext.current
    SettingsSection(stringResource(R.string.settings_section_about), icon = Icons.Default.Info) {
        Text(stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME, ENGINE_VERSION), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
        Text(stringResource(R.string.settings_about_sdk), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
        TextButton(onClick = onShowLicenses, contentPadding = PaddingValues(0.dp)) {
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
        ) {
            Text(stringResource(R.string.settings_about_share_crash_log), style = MaterialTheme.typography.bodySmall, color = AccentCoral)
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
)

@Composable
internal fun OssLicensesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant,
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
