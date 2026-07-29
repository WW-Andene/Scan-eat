package fr.scanneat.presentation.mealplan.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun WeeklyKcalBanner(weeklyTotalKcal: Int) {
    Surface(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        color = AccentCoral.copy(0.08f),
        modifier = Modifier.fillMaxWidth(),
        // design-aesthetic-audit §DH: had no shadowElevation at all.
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.M, vertical = Spacing.S),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.mealplan_weekly_kcal_title), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
            Text(stringResource(R.string.mealplan_weekly_kcal_value, weeklyTotalKcal), style = MaterialTheme.typography.labelMedium, color = AccentCoral, fontWeight = FontWeight.SemiBold)
        }
    }
}
