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
 * Full-screen fallback shown in place of the camera preview: either the device has
 * no camera hardware at all (`FEATURE_CAMERA_ANY` false, see ScanScreen's own
 * hasCameraHardware check), or CameraPreview's own bind attempt failed at runtime.
 * Either way the barcode-entry path via ManualBarcodeEntry below is the only way
 * left to reach ScanViewModel.score() with a barcode, so this isn't a dead-end
 * message — it's a working substitute input.
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
        Spacer(Modifier.height(Spacing.L))
        Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium, color = OnBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.S))
        Text(stringResource(bodyRes), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.XL))
        if (onRetry != null) {
            ScanEatPrimaryButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
            Spacer(Modifier.height(Spacing.L))
        }
        ManualBarcodeEntry(onSubmit = onSubmit)
    }
}

/**
 * Standard GS1 mod-10 check digit, length-agnostic: the rightmost non-check
 * digit always carries weight 3, alternating leftward - the same rule
 * ScanRepository's ean13CheckDigit (12-digit payload, left-indexed 1,3,1,3,...)
 * and upcCheckDigit (11-digit payload, left-indexed 3,1,3,1,...) already
 * implement per-length; this is the general form so a single check covers
 * EAN-8/UPC-A/EAN-13/GTIN-14 without duplicating one function per length here.
 */
private fun hasValidGs1CheckDigit(digits: String): Boolean {
    val checkDigit = digits.last() - '0'
    val sum = digits.dropLast(1).reversed().foldIndexed(0) { i, acc, c ->
        acc + (c - '0') * if (i % 2 == 0) 3 else 1
    }
    return (10 - (sum % 10)) % 10 == checkDigit
}

/**
 * Digit-count validation matches analyzeFrame's camera-detected barcodes
 * (8/12/13/14 digits, covering EAN-8/UPC-A/EAN-13/GTIN-14), plus a checksum
 * check the camera path never needed (ML Kit's own decode already guarantees
 * a structurally valid code) - a manually typed code has no such guarantee,
 * so a typo'd digit previously round-tripped all the way to OFF/the server
 * before coming back a generic "not found" instead of being caught locally.
 */
@Composable
internal fun ManualBarcodeEntry(onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val digits = remember(text) { text.filter { it.isDigit() } }
    val hasLikelyLength = digits.length in listOf(8, 12, 13, 14)
    val isValid = hasLikelyLength && hasValidGs1CheckDigit(digits)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.scan_manual_entry_label)) },
            placeholder = { Text(stringResource(R.string.scan_manual_entry_hint)) },
            singleLine = true,
            isError = text.isNotEmpty() && !isValid,
            // Only shown once the length is otherwise plausible - a still-shorter
            // in-progress entry shouldn't flash "invalid" before the user has even
            // finished typing a full-length code.
            supportingText = if (hasLikelyLength && !isValid) {
                { Text(stringResource(R.string.scan_manual_entry_invalid)) }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(220.dp),
        )
        Spacer(Modifier.height(Spacing.M))
        ScanEatPrimaryButton(onClick = { onSubmit(digits) }, enabled = isValid) {
            Text(stringResource(R.string.scan_manual_entry_submit))
        }
    }
}
