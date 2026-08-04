package fr.scanneat.presentation.expenses.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.data.repository.expense.PriceEntry
import fr.scanneat.domain.engine.expense.ValueScore
import fr.scanneat.presentation.ui.theme.*
import fr.scanneat.util.formatDecimal
import java.time.format.DateTimeFormatter

@Composable
internal fun ExpenseEntryRow(entry: PriceEntry, dateFmt: DateTimeFormatter, onEdit: () -> Unit, onDelete: () -> Unit) {
    // Tapping the row now opens the edit dialog - previously the only action
    // available on a logged entry was delete, so a mistyped price or name
    // could only be fixed by deleting and re-adding it from scratch.
    ScanEatCard(contentPadding = PaddingValues(horizontal = Spacing.M, vertical = Spacing.S), onClick = onEdit) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.productName, style = MaterialTheme.typography.bodyMedium, color = OnBackground, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.date.format(dateFmt), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                    Text("${entry.priceEuros.formatDecimal(2)} €", style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
                    entry.pricePerKg?.let {
                        Text("${it.formatDecimal(2)} €/kg", style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.4f))
                    }
                    entry.valueScore?.let { score ->
                        val (label, color) = when (score) {
                            ValueScore.GREAT   -> stringResource(R.string.result_price_value_great) to semanticGreen()
                            ValueScore.GOOD    -> stringResource(R.string.result_price_value_good) to semanticGreen()
                            ValueScore.AVERAGE -> stringResource(R.string.result_price_value_average) to semanticAmber()
                            ValueScore.POOR    -> stringResource(R.string.result_price_value_poor) to AccentCoral
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, stringResource(R.string.common_delete), tint = OnSurface.copy(0.5f))
            }
        }
    }
}
