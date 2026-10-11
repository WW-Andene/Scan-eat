package fr.scanneat.presentation.dashboard.cards

import compose.icons.TablerIcons
import compose.icons.tablericons.ChartBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.dashboard.DiversityLevel
import fr.scanneat.domain.engine.dashboard.FoodDiversityResult
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.IconSize
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.semanticAmber
import fr.scanneat.presentation.ui.theme.semanticGreen

/**
 * User-requested: a diversity signal independent of any single product's own
 * score - a user can eat well-scored food every day and still have a
 * nutritionally narrow diet (the same 3-4 products on repeat), which nothing
 * on Dashboard measured before this. See foodDiversityScore()'s own doc
 * comment for the count/level logic.
 */
@Composable
internal fun FoodDiversityCard(diversity: FoodDiversityResult) {
    val (color, labelRes) = when (diversity.level) {
        DiversityLevel.LOW      -> semanticAmber() to R.string.dashboard_diversity_low
        DiversityLevel.MODERATE -> semanticAmber() to R.string.dashboard_diversity_moderate
        DiversityLevel.GOOD     -> semanticGreen() to R.string.dashboard_diversity_good
        DiversityLevel.HIGH     -> semanticGreen() to R.string.dashboard_diversity_high
    }
    ScanEatCard(contentPadding = PaddingValues(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Icon(TablerIcons.ChartBar, null, tint = AccentCoral, modifier = Modifier.size(IconSize.Small))
            Text(stringResource(R.string.dashboard_diversity_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
        }
        Text(
            stringResource(R.string.dashboard_diversity_count, diversity.distinctCount),
            style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.85f),
        )
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall, color = color)
    }
}
