package fr.scanneat.presentation.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

/**
 * Expenses previously hardcoded "€" at every price display (history, budget
 * card, dashboard recap, PriceEntryCard) regardless of this setting not even
 * existing - unusable outside the eurozone. Still no currency-code dropdown
 * (ScanEat only ever displays a symbol, never performs FX conversion, so
 * there's nothing a "USD"/"EUR" code would let it do that a raw symbol
 * doesn't), but user-reported: a pure free-text field with zero presets
 * meant even the two most common symbols required typing them out by hand
 * every time this screen was reached, with no indication of what a
 * "reasonable" value looks like. € and $ are now one-tap chips (same
 * FilterChip pattern as LanguageSection's fr/en picker); "Autre" reveals the
 * free-text field for anything else, pre-filled with the current symbol so
 * a custom value already set isn't silently discarded when this section
 * re-renders.
 */
@Composable
internal fun CurrencySection(currencySymbol: String, onChange: (String) -> Unit) {
    val presets = listOf("€", "$")
    var text by remember(currencySymbol) { mutableStateOf(currencySymbol) }
    var showCustom by remember(currencySymbol) { mutableStateOf(currencySymbol !in presets) }
    SettingsSection(stringResource(R.string.settings_section_currency), icon = Icons.Default.Payments) {
        Text(stringResource(R.string.settings_currency_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            presets.forEach { symbol ->
                FilterChip(
                    selected = !showCustom && currencySymbol == symbol,
                    onClick  = { showCustom = false; onChange(symbol) },
                    label    = { Text(symbol) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                    ),
                )
            }
            FilterChip(
                selected = showCustom,
                onClick  = { showCustom = true },
                label    = { Text(stringResource(R.string.settings_currency_other)) },
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                ),
            )
        }
        if (showCustom) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(3); onChange(text) },
                label = { Text(stringResource(R.string.settings_currency_label)) },
                singleLine = true,
                shape = RoundedCornerShape(CardRadius.CONTROL),
                colors = scanEatTextFieldColors(),
            )
        }
    }
}
