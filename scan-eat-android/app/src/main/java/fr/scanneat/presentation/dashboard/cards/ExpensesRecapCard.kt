package fr.scanneat.presentation.dashboard.cards

import compose.icons.TablerIcons
import compose.icons.tablericons.FileInvoice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.presentation.expenses.ExpensesViewModel
import fr.scanneat.presentation.ui.theme.*
import fr.scanneat.util.formatDecimal

/**
 * Dashboard glance at this week's spend — reuses ExpensesViewModel directly
 * (own hiltViewModel() instance) rather than threading price data through
 * DashboardViewModel's already-large combine() chain, since this card's data
 * need (this week's price_log total + the weekly budget target) is entirely
 * self-contained and has no other Dashboard card depending on it.
 */
@Composable
fun ExpensesRecapCard(onClick: () -> Unit, viewModel: ExpensesViewModel = hiltViewModel()) {
    val weekTotal = viewModel.weekTotal.collectAsStateWithLifecycle()
    val budgetWeekly = viewModel.budgetWeeklyEuros.collectAsStateWithLifecycle()
    val currencySymbol = viewModel.currencySymbol.collectAsStateWithLifecycle()

    // Nothing to show yet if the user has never logged a price and never set a
    // budget - showing a permanent "0,00 €" card to every user who hasn't
    // touched this feature would just be Dashboard noise.
    if (weekTotal.value <= 0.0 && budgetWeekly.value == null) return

    ScanEatCard(
        contentPadding = PaddingValues(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.S),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            Icon(TablerIcons.FileInvoice, null, tint = OnSurface.copy(0.6f))
            Text(stringResource(R.string.expenses_week_title), style = MaterialTheme.typography.labelMedium, color = OnSurface.copy(0.7f), fontWeight = FontWeight.SemiBold)
        }
        Text(
            dispCurrency(weekTotal.value, currencySymbol.value),
            style = MaterialTheme.typography.titleLarge,
            color = OnBackground,
            fontWeight = FontWeight.Bold,
        )
        budgetWeekly.value?.takeIf { it > 0 }?.let { budget ->
            val pct = (weekTotal.value / budget).toFloat().coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { pct },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = if (weekTotal.value > budget) AccentCoral else semanticGreen(),
                trackColor = OnSurface.copy(0.1f),
            )
            Text(
                stringResource(R.string.expenses_of_budget, budget, currencySymbol.value),
                style = MaterialTheme.typography.labelSmall,
                color = OnSurface.copy(0.5f),
            )
        }
    }
}
