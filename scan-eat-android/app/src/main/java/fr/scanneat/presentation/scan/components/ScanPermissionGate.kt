package fr.scanneat.presentation.scan.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.ScanEatPrimaryButton
import fr.scanneat.presentation.ui.theme.Spacing

@Composable
internal fun ScanPermissionRequestColumn(
    permanentlyDenied: Boolean,
    manualEntryOpen: Boolean,
    onOpenAppSettings: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenManualEntry: () -> Unit,
    onQuickScan: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.XXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.CameraAlt, null, tint = OnBackground, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(Spacing.L))
        Text(stringResource(R.string.scan_camera_permission_title), style = MaterialTheme.typography.titleMedium,
            color = OnBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.S))
        Text(stringResource(R.string.camera_permission_rationale),
            style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.XL))
        if (permanentlyDenied) {
            ScanEatPrimaryButton(onClick = onOpenAppSettings) {
                Text(stringResource(R.string.scan_open_settings_button))
            }
        } else {
            ScanEatPrimaryButton(onClick = onRequestPermission) {
                Text(stringResource(R.string.common_allow))
            }
        }
        Spacer(Modifier.height(Spacing.L))
        if (!manualEntryOpen) {
            TextButton(onClick = onOpenManualEntry) {
                Text(stringResource(R.string.scan_manual_entry_toggle), color = OnBackground.copy(0.8f))
            }
        } else {
            ManualBarcodeEntry(onSubmit = onQuickScan)
        }
    }
}
