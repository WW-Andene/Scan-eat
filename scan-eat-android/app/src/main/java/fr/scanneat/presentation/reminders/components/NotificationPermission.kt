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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.ScanEatPrimaryButton

@Composable
internal fun permissionState(): Triple<Boolean, Boolean, () -> Unit> {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var requestedOnce by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = granted
        if (!granted) {
            val activity = context as? Activity
            val canShowRationale = activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS) } ?: true
            if (requestedOnce && !canShowRationale) permanentlyDenied = true
        }
        requestedOnce = true
    }
    return Triple(permissionGranted, permanentlyDenied) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

@Composable
internal fun PermissionBanner(permissionGranted: Boolean, permanentlyDenied: Boolean, onRequest: () -> Unit) {
    val context = LocalContext.current
    if (!permissionGranted) {
        if (permanentlyDenied) {
            ScanEatPrimaryButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) }) {
                Text(stringResource(R.string.scan_open_settings_button))
            }
        } else {
            ScanEatPrimaryButton(onClick = onRequest) { Text(stringResource(R.string.reminders_enable_notifications)) }
        }
    }
}
