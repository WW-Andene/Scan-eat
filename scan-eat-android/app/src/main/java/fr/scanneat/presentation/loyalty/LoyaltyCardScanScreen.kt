package fr.scanneat.presentation.loyalty

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import fr.scanneat.R
import fr.scanneat.presentation.scan.components.CameraPreview
import fr.scanneat.presentation.scan.rememberCameraPermissionState
import fr.scanneat.presentation.ui.theme.*

/**
 * User-requested: read a loyalty card's own barcode with the camera, the
 * same way ScanScreen reads a product's - reuses the exact same CameraPreview
 * + ML Kit BarcodeScanning pipeline (see CameraPreview.kt), just without
 * ScanViewModel's product-lookup/scoring machinery, since there's no public
 * database of loyalty-card codes to look up the way OpenFoodFacts covers
 * products. The captured value is only ever the raw code the barcode itself
 * encodes - nothing about the store, offers, or points balance is read from
 * it (loyalty barcodes don't carry that; a store's own app talks to it over
 * a private API scan-eat has no access to), which is why a store name still
 * has to be picked by hand below.
 */
@Composable
fun LoyaltyCardScanScreen(onBack: () -> Unit, viewModel: LoyaltyCardsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val cameraPermission = rememberCameraPermissionState()
    val hasCameraHardware = cameraPermission.hasCameraHardware
    var hasCamera by cameraPermission.hasCamera
    var cameraUnavailable by cameraPermission.cameraUnavailable
    var permanentlyDenied by cameraPermission.permanentlyDenied

    var capturedCode by remember { mutableStateOf<String?>(null) }
    var storeText by rememberSaveable { mutableStateOf("") }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold)) {
        if (hasCamera && !cameraUnavailable) {
            CameraPreview(
                onBarcodeDetected = { code ->
                    // First barcode wins - CameraPreview keeps calling this every frame
                    // a code is in view, so without this guard the dialog below would
                    // reset storeText/re-open on every frame instead of staying put
                    // once the user starts typing the store name.
                    if (capturedCode == null) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        capturedCode = code
                    }
                },
                onPhotoCaptured = {},
                onCameraError = { cameraUnavailable = true },
                topInset = topInset,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = topInset + Spacing.M, start = Spacing.M, end = Spacing.M),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, colors = IconButtonDefaults.iconButtonColors(containerColor = SurfaceVariant.copy(alpha = 0.7f))) {
                    Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground)
                }
                Spacer(Modifier.width(Spacing.M))
                Text(
                    stringResource(R.string.loyalty_scan_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnBackground,
                )
            }
        } else if (!hasCameraHardware || cameraUnavailable) {
            LoyaltyScanNoCameraColumn(onBack = onBack)
        } else {
            LoyaltyScanPermissionColumn(
                permanentlyDenied = permanentlyDenied,
                onBack = onBack,
                onOpenAppSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                },
                onRequestPermission = cameraPermission.requestPermission,
            )
        }
    }

    capturedCode?.let { code ->
        AlertDialog(
            onDismissRequest = { capturedCode = null },
            title = { Text(stringResource(R.string.loyalty_scan_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Text(code.chunked(4).joinToString(" "), style = MaterialTheme.typography.headlineSmall, color = OnBackground)
                    OutlinedTextField(
                        value = storeText,
                        onValueChange = { storeText = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.loyalty_store_placeholder)) },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = storeText.isNotBlank(),
                    onClick = {
                        viewModel.addCard(storeText, code)
                        onBack()
                    },
                ) { Text(stringResource(R.string.common_add)) }
            },
            dismissButton = {
                // Re-scan, not cancel-to-blank - a misread/wrong card is far more
                // likely than the user wanting to abandon the flow entirely once a
                // code has already been captured.
                TextButton(onClick = { capturedCode = null; storeText = "" }) { Text(stringResource(R.string.loyalty_scan_retry)) }
            },
        )
    }
}

@Composable
private fun LoyaltyScanNoCameraColumn(onBack: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(Spacing.L),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.CameraAlt, null, tint = OnBackground.copy(0.5f), modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(Spacing.M))
        Text(stringResource(R.string.loyalty_scan_no_camera_body), style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.8f))
        Spacer(Modifier.height(Spacing.L))
        TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
    }
}

@Composable
private fun LoyaltyScanPermissionColumn(
    permanentlyDenied: Boolean,
    onBack: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRequestPermission: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(Spacing.L),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.CameraAlt, null, tint = OnBackground.copy(0.5f), modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(Spacing.M))
        Text(stringResource(R.string.loyalty_scan_permission_body), style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.8f))
        Spacer(Modifier.height(Spacing.L))
        Button(
            onClick = if (permanentlyDenied) onOpenAppSettings else onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = AccentCoral),
        ) { Text(stringResource(if (permanentlyDenied) R.string.scan_open_settings_button else R.string.loyalty_scan_grant_permission)) }
        Spacer(Modifier.height(Spacing.S))
        TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
    }
}
