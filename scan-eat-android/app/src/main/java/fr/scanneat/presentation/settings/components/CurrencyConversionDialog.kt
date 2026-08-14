package fr.scanneat.presentation.settings.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.GlassAlertDialog
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.StandardCardAlpha
/**
 * User-requested: changing currency should convert already-logged prices, not
 * just relabel them - this confirms that conversion before it's actually
 * applied, since it rewrites financial history. Extracted from SettingsScreen
 * (§T1 composition-root split) alongside its sibling dialogs (ResetConfirmDialog,
 * OssLicensesDialog).
 */
@Composable
fun CurrencyConversionDialog(
    currentSymbol: String,
    newSymbol: String,
    factor: Double,
    onDismiss: () -> Unit,
    onConvert: () -> Unit,
    onRelabelOnly: () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_currency_convert_title), color = OnBackground) },
        text = {
            Text(
                stringResource(R.string.settings_currency_convert_body, currentSymbol, newSymbol, factor),
                style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.8f),
            )
        },
        confirmButton = {
            TextButton(onClick = onConvert) { Text(stringResource(R.string.settings_currency_convert_confirm), color = AccentCoral) }
        },
        dismissButton = {
            // Relabel-only, the old behavior - keeps every logged number as-is,
            // just changes which symbol is shown next to it.
            TextButton(onClick = onRelabelOnly) { Text(stringResource(R.string.settings_currency_convert_relabel_only), color = OnBackground.copy(0.6f)) }
        },
    )
}
