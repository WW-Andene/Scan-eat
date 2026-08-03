package fr.scanneat.presentation.result.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import fr.scanneat.R
import fr.scanneat.data.repository.expense.PriceEntry
import fr.scanneat.domain.engine.expense.ValueScore
import fr.scanneat.presentation.ui.theme.*
import fr.scanneat.util.formatDecimal

/**
 * Manual price entry for the scanned product — no OCR price-tag detection
 * (unreliable to build honestly), so this is a plain "what did you pay" input,
 * paired with a value-score badge once weight is known (priceEuros / weightG
 * vs. the category's typical price/kg, see ValueScoreEstimator).
 */
@Composable
internal fun PriceEntryCard(
    entries: List<PriceEntry>,
    onSave: (priceEuros: Double, weightG: Double?) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    ScanEatCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.M)) {
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
            Text(stringResource(R.string.result_price_empty), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
        } else {
            entries.take(3).forEach { entry ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("${entry.priceEuros.formatDecimal(2)} €", style = MaterialTheme.typography.bodyMedium, color = OnBackground)
                        entry.pricePerKg?.let { perKg ->
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.CenterVertically) {
                                Text("${perKg.formatDecimal(2)} €/kg", style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                                entry.valueScore?.let { ValueScoreBadge(it) }
                            }
                        }
                    }
                    IconButton(onClick = { onDelete(entry.id) }) {
                        Icon(Icons.Rounded.Delete, stringResource(R.string.common_delete), tint = OnSurface.copy(0.4f))
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
    var priceText by remember { mutableStateOf("") }
    var weightText by remember { mutableStateOf("") }
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
                OutlinedTextField(
                    value = priceText, onValueChange = { priceText = it },
                    label = { Text(stringResource(R.string.result_price_field_euros)) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
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
        containerColor = SurfaceVariant,
        shape = RoundedCornerShape(CardRadius.PROMINENT),
    )
}
