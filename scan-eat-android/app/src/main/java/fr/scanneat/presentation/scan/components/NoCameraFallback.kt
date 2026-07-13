package fr.scanneat.presentation.scan.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.ScanEatPrimaryButton
import fr.scanneat.presentation.ui.theme.Spacing

/**
 * Shown when the camera is unavailable (permission denied, hardware error) —
 * with a barcode, so this isn't a dead-end message — it's a working substitute input.
 */
@Composable
internal fun NoCameraFallback(
    titleRes: Int,
    bodyRes: Int,
    onSubmit: (String) -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.XXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.QrCodeScanner, null, tint = OnBackground, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium, color = OnBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(bodyRes), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        if (onRetry != null) {
            ScanEatPrimaryButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
            Spacer(Modifier.height(16.dp))
        }
        ManualBarcodeEntry(onSubmit = onSubmit)
    }
}

/**
 * Same digit-count validation analyzeFrame applies to camera-detected barcodes
 * (8/12/13/14 digits, covering EAN-8/UPC-A/EAN-13/GTIN-14) so a manually typed
 * code reaches ScanRepository in exactly the shape it already expects.
 */
@Composable
internal fun ManualBarcodeEntry(onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val digits = remember(text) { text.filter { it.isDigit() } }
    val isValid = digits.length in listOf(8, 12, 13, 14)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.scan_manual_entry_label)) },
            placeholder = { Text(stringResource(R.string.scan_manual_entry_hint)) },
            singleLine = true,
            isError = text.isNotEmpty() && !isValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(220.dp),
        )
        Spacer(Modifier.height(12.dp))
        ScanEatPrimaryButton(onClick = { onSubmit(digits) }, enabled = isValid) {
            Text(stringResource(R.string.scan_manual_entry_submit))
        }
    }
}
