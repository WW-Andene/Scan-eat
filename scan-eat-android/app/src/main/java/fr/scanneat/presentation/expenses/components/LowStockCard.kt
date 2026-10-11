package fr.scanneat.presentation.expenses.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import fr.scanneat.R
import fr.scanneat.data.repository.expense.PriceEntry
import fr.scanneat.presentation.ui.theme.*

/**
 * R&D audit finding: PriceRepository.deductStock's "remaining stock" concept
 * (drawn down as portions are logged from a purchased lot) never surfaced
 * anywhere as a "running low" nudge - see ExpensesViewModel.lowStockItems'
 * own doc comment for the threshold. One tap re-adds the item to the
 * grocery list at its originally-purchased quantity.
 */
@Composable
internal fun LowStockCard(items: List<PriceEntry>, onAddToGrocery: (PriceEntry) -> Unit) {
    if (items.isEmpty()) return
    ScanEatCard(contentPadding = PaddingValues(Spacing.M), emphasis = CardEmphasis.SECONDARY) {
        Text(
            stringResource(R.string.expenses_low_stock_title),
            style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f), fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(Spacing.XS))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            items.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(entry.productName, style = MaterialTheme.typography.bodySmall, color = OnBackground, maxLines = 1)
                    TextButton(onClick = { onAddToGrocery(entry) }) {
                        Text(stringResource(R.string.expenses_low_stock_add), style = MaterialTheme.typography.labelSmall, color = AccentCoral)
                    }
                }
            }
        }
    }
}
