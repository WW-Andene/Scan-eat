package fr.scanneat.presentation.settings.components

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
 * existing - unusable outside the eurozone. A short free-text symbol (not a
 * currency-code dropdown) since ScanEat only ever displays a symbol, never
 * performs FX conversion.
 */
@Composable
internal fun CurrencySection(currencySymbol: String, onChange: (String) -> Unit) {
    var text by remember(currencySymbol) { mutableStateOf(currencySymbol) }
    SettingsSection(stringResource(R.string.settings_section_currency), icon = Icons.Default.Payments) {
        Text(stringResource(R.string.settings_currency_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
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
