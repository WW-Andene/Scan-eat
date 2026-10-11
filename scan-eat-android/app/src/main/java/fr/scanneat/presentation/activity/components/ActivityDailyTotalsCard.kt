package fr.scanneat.presentation.activity.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun ActivityDailyTotalsCard(totalKcal: Int, totalMin: Int) {
    // design-aesthetic-audit §DTA: was a raw PaddingValues(16.dp) literal instead of
    // the Spacing.L token every other card's contentPadding already uses for this
    // exact value.
    ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), contentPadding = PaddingValues(Spacing.L)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$totalKcal", style = MaterialTheme.typography.titleLarge, color = Warm, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.activity_kcal_burned_label), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$totalMin", style = MaterialTheme.typography.titleLarge, color = AccentCoral, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.activity_minutes_label), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
            }
        }
    }
}
