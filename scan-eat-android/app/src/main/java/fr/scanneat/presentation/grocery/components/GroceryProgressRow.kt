package fr.scanneat.presentation.grocery.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun GroceryProgressRow(itemCount: Int, checkedProgress: Pair<Int, Int>) {
    val (checked, total) = checkedProgress
    Text(pluralStringResource(R.plurals.grocery_item_count, itemCount, itemCount),
        style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
    if (total > 0 && checked > 0) {
        Spacer(Modifier.height(Spacing.XS))
        androidx.compose.material3.LinearProgressIndicator(
            progress = { (checked.toFloat() / total).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
            color = semanticGreen(),
            trackColor = OnBackground.copy(0.08f),
        )
        if (checked == total) {
            Text(
                stringResource(R.string.grocery_all_done),
                style = MaterialTheme.typography.labelSmall,
                color = semanticGreen(),
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            Text(
                stringResource(R.string.grocery_checked_progress, checked, total),
                style = MaterialTheme.typography.labelSmall,
                color = OnBackground.copy(0.4f),
            )
        }
    }
}
