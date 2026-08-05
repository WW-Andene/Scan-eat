package fr.scanneat.presentation.result.cards

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import fr.scanneat.R
import fr.scanneat.data.repository.expense.PriceEntry
import fr.scanneat.domain.engine.expense.ValueScore
import fr.scanneat.presentation.ui.theme.*
import fr.scanneat.util.formatDecimal

// User-requested: pre-fill the price field from a photographed price tag
// instead of typing it every time - matches the app's existing "digits with a
// decimal separator" price format (e.g. "3,99" or "0.79"), taking the first
// match in the recognized text. Deliberately simple (no currency-symbol
// anchoring, no picking the "right" one among several prices on a promo tag)
// since the field stays fully editable afterward - a wrong/partial match costs
// the user a tap to correct, not a broken feature.
private val PRICE_PATTERN = Regex("""\d{1,4}[.,]\d{2}""")

private fun extractPrice(text: String): String? =
    PRICE_PATTERN.find(text)?.value?.replace(',', '.')

/**
 * Manual price entry for the scanned product — no OCR price-tag detection
 * (unreliable to build honestly), so this is a plain "what did you pay" input,
 * paired with a value-score badge once weight is known (priceEuros / weightG
 * vs. the category's typical price/kg, see ValueScoreEstimator).
 */
@Composable
internal fun PriceEntryCard(
    entries: List<PriceEntry>,
    currencySymbol: String,
    onSave: (priceEuros: Double, weightG: Double?) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    ScanEatCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.L)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Icon(Icons.Rounded.LocalOffer, null, tint = OnSurface.copy(0.6f))
                Text(stringResource(R.string.result_price_title), style = MaterialTheme.typography.labelMedium, color = OnSurface.copy(0.7f), fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = { showDialog = true }) {
                Text(stringResource(R.string.result_price_add), color = AccentCoral, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (entries.isEmpty()) {
            Text(stringResource(R.string.result_price_empty), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        } else {
            entries.take(3).forEach { entry ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(dispCurrency(entry.priceEuros, currencySymbol), style = MaterialTheme.typography.bodyMedium, color = OnBackground)
                        entry.pricePerKg?.let { perKg ->
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.CenterVertically) {
                                Text("${dispCurrency(perKg, currencySymbol)}/kg", style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                                entry.valueScore?.let { ValueScoreBadge(it) }
                            }
                        }
                    }
                    IconButton(onClick = { onDelete(entry.id) }) {
                        Icon(Icons.Rounded.Delete, stringResource(R.string.common_delete), tint = OnSurface.copy(0.5f))
                    }
                }
            }
        }
    }

    if (showDialog) {
        PriceInputDialog(
            onConfirm = { price, weight -> onSave(price, weight); showDialog = false },
            onDismiss = { showDialog = false },
        )
    }
}

@Composable
private fun ValueScoreBadge(score: ValueScore) {
    val (label, color) = when (score) {
        ValueScore.GREAT   -> stringResource(R.string.result_price_value_great) to semanticGreen()
        ValueScore.GOOD    -> stringResource(R.string.result_price_value_good) to semanticGreen()
        ValueScore.AVERAGE -> stringResource(R.string.result_price_value_average) to semanticAmber()
        ValueScore.POOR    -> stringResource(R.string.result_price_value_poor) to AccentCoral
    }
    Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun PriceInputDialog(onConfirm: (Double, Double?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var priceText by remember { mutableStateOf("") }
    var weightText by remember { mutableStateOf("") }
    val noPriceFoundMessage = stringResource(R.string.result_price_scan_not_found)
    val scanPriceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()?.let { image ->
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
                .addOnSuccessListener { result ->
                    val found = extractPrice(result.text)
                    if (found != null) priceText = found
                    else Toast.makeText(context, noPriceFoundMessage, Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { Toast.makeText(context, noPriceFoundMessage, Toast.LENGTH_SHORT).show() }
        } ?: Toast.makeText(context, noPriceFoundMessage, Toast.LENGTH_SHORT).show()
    }
    // Bounded, not just "> 0" - an unbounded price field previously accepted any
    // value (including something like a pasted "999999999"), which then flows
    // straight into the price/kg computation and value-score comparison. Upper
    // bounds are generous (a real grocery item, even luxury/bulk) rather than tight.
    val price = priceText.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.01..9999.99 }
    val weight = weightText.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.1..50000.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.result_price_add), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    OutlinedTextField(
                        value = priceText, onValueChange = { priceText = it },
                        label = { Text(stringResource(R.string.result_price_field_euros)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                        shape = RoundedCornerShape(CardRadius.CONTROL),
                        colors = scanEatTextFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                    // Pre-fills priceText above from a photographed price tag - the
                    // field stays a plain editable OutlinedTextField either way, so a
                    // wrong/partial OCR read is just as easy to fix as typing it
                    // yourself, never a "trust the scan or start over" choice.
                    IconButton(onClick = { scanPriceLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Rounded.CameraAlt, stringResource(R.string.result_price_scan_cd), tint = OnBackground.copy(0.6f))
                    }
                }
                OutlinedTextField(
                    value = weightText, onValueChange = { weightText = it },
                    label = { Text(stringResource(R.string.result_price_field_weight)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
                Text(stringResource(R.string.result_price_field_weight_hint), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
            }
        },
        confirmButton = {
            TextButton(onClick = { price?.let { onConfirm(it, weight) } }, enabled = price != null) {
                Text(stringResource(R.string.common_save), color = AccentCoral)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
        containerColor = SurfaceVariant.copy(alpha = 0.85f),
        modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
        shape = RoundedCornerShape(CardRadius.PROMINENT),
    )
}
