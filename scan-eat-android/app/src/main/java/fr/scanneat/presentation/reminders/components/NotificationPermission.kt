package fr.scanneat.presentation.reminders.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.ScanEatPrimaryButton
import fr.scanneat.presentation.ui.theme.ShadowTint
import fr.scanneat.presentation.ui.theme.Spacing
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow

@Composable
internal fun permissionState(): Triple<Boolean, Boolean, () -> Unit> {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var permanentlyDenied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (!granted) {
            val activity = context as? Activity
            // shouldShowRequestPermissionRationale() already correctly distinguishes
            // "never asked yet" (true right after a first honest denial) from
            // "permanently denied" (false) - the previous extra `requestedOnce &&`
            // gate assumed the first callback in *this* session could never reflect
            // a permanent denial from an *earlier* session, which is wrong: a user
            // who already permanently denied notifications before, then reopens the
            // app and taps "Enable Notifications" for the first time this session,
            // gets canShowRationale=false immediately (Android silently denies with
            // no dialog) - but requestedOnce was still false at that point, so this
            // legitimate signal was discarded and the button silently no-opped,
            // requiring a second, seemingly-identical tap before "Open Settings"
            // ever appeared.
            val canShowRationale = activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS) } ?: true
            if (!canShowRationale) permanentlyDenied = true
        }
    }
    return Triple(permissionGranted, permanentlyDenied) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

/**
 * art-direction-engine §COMPONENTS: this was a bare ScanEatPrimaryButton floating
 * directly in the card - no icon, no explanatory text, no surface of its own -
 * unlike every other "this needs your attention" moment in the app (ErrorBanner,
 * CautionBanner), which all get an icon + framed Surface treatment. A permission
 * gate that silences every reminder in this card until resolved deserves the same
 * material identity, not an unstyled button that reads as just another action.
 */
@Composable
internal fun PermissionBanner(permissionGranted: Boolean, permanentlyDenied: Boolean, onRequest: () -> Unit) {
    val context = LocalContext.current
    if (!permissionGranted) {
        Surface(
            modifier = Modifier.fillMaxWidth()
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(CardRadius.CONTROL), ambientColor = ShadowTint, spotColor = ShadowTint)
                .clip(RoundedCornerShape(CardRadius.CONTROL)),
            color = AccentCoral.copy(alpha = 0.10f), shape = RoundedCornerShape(CardRadius.CONTROL),
            shadowElevation = 0.dp,
        ) {
            Column(Modifier.padding(Spacing.M)) {
                // User-reported: the bell previously sat in a CenterVertically Row
                // spanning the title+subtitle+button stack, so it read as
                // vertically centered on the whole card instead of anchored to the
                // top of the text it introduces. Top-aligned against the
                // title/subtitle Column only (the button below is now a sibling,
                // not part of that Row, so it no longer pulls the icon's center down).
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.NotificationsActive, null, tint = AccentCoral)
                    Spacer(Modifier.width(Spacing.S))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.reminders_permission_needed_title),
                            style = MaterialTheme.typography.bodySmall, color = OnBackground, fontWeight = FontWeight.SemiBold,
                        )
                        // ScanPermissionGate's camera rationale explains WHY before the OS
                        // prompt appears - this banner only ever showed a bare title + button,
                        // the one first-run permission flow in the app with no "why" before
                        // asking, inconsistent with the camera flow's own explain-before-ask pattern.
                        Text(
                            stringResource(R.string.reminders_permission_rationale),
                            style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f),
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.S))
                // User-reported: previously nested inside the icon-indented text
                // Column (Modifier.weight(1f), no fillMaxWidth), so the button sat
                // narrow and offset by the icon+spacer width instead of spanning the
                // card's full inner width - moved out to its own full-width row here.
                if (permanentlyDenied) {
                    ScanEatPrimaryButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.scan_open_settings_button))
                    }
                } else {
                    ScanEatPrimaryButton(onClick = onRequest, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reminders_enable_notifications)) }
                }
            }
        }
    }
}
