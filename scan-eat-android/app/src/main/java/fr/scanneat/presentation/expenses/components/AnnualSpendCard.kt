package fr.scanneat.presentation.expenses.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import fr.scanneat.R
import fr.scanneat.domain.engine.expense.AnnualSpendProjection
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.dispCurrency
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.semanticAmber
import fr.scanneat.presentation.ui.theme.semanticGreen

/**
 * User-requested: "empreinte financière annuelle" - see
 * annualSpendProjection()'s own doc comment. Only shown once there's a real
 * month-to-date total to extrapolate from (see ExpensesScreen's own gating),
 * so a brand-new profile with zero purchases logged doesn't see a
 * meaningless "€0/year" projection.
 */
@Composable
internal fun AnnualSpendCard(projection: AnnualSpendProjection, currencySymbol: String) {
    ScanEatCard(contentPadding = PaddingValues(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(stringResource(R.string.expenses_annual_projection_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(R.string.expenses_annual_projection_amount, dispCurrency(projection.projectedAnnualEuros, currencySymbol)),
            style = MaterialTheme.typography.bodyMedium, color = OnSurface.copy(0.85f),
        )
        projection.budgetAnnualEuros?.let { budget ->
            Text(
                stringResource(R.string.expenses_annual_projection_budget, dispCurrency(budget, currencySymbol)) +
                    if (projection.overBudget) " · " + stringResource(R.string.expenses_over_budget_suffix) else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (projection.overBudget) semanticAmber() else semanticGreen(),
            )
        }
    }
}
