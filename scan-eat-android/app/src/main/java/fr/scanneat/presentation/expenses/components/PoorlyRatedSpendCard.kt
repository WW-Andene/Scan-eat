package fr.scanneat.presentation.expenses.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.dispCurrency
import fr.scanneat.presentation.ui.theme.semanticAmber

/**
 * User-requested: "tu dépenses X€/mois sur des produits mal notés" - see
 * ExpensesViewModel.poorlyRatedSpendMonth's own doc comment for how the
 * grade/spend crossover is computed. Only shown once there's at least one
 * gradeable (barcode-logged) purchase this month, same "don't show a
 * meaningless figure on an empty history" gating AnnualSpendCard already
 * uses for its own projection.
 */
@Composable
internal fun PoorlyRatedSpendCard(amountEuros: Double, currencySymbol: String) {
    ScanEatCard(contentPadding = PaddingValues(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(stringResource(R.string.expenses_poorly_rated_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.expenses_poorly_rated_amount, dispCurrency(amountEuros, currencySymbol)),
            style = MaterialTheme.typography.bodyMedium, color = semanticAmber(),
        )
        Text(
            stringResource(R.string.expenses_poorly_rated_hint),
            style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f),
        )
    }
}
