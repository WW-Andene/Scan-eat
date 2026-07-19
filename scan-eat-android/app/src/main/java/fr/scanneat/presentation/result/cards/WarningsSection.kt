package fr.scanneat.presentation.result.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.semanticAmber
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.Spacing

@Composable
internal fun WarningsSection(warnings: List<String>) {
    ScanEatCard(
        shape = RoundedCornerShape(CardRadius.CONTROL), color = semanticAmber().copy(0.1f),
        contentPadding = PaddingValues(Spacing.M),
    ) {
        Text(stringResource(R.string.result_notes_title), style = MaterialTheme.typography.labelMedium,
            color = semanticAmber(), fontWeight = FontWeight.SemiBold)
        warnings.forEach { Text(stringResource(R.string.result_warning_item, it), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f)) }
    }
}
