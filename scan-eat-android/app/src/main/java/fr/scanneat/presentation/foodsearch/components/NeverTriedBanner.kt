package fr.scanneat.presentation.foodsearch.components

import compose.icons.TablerIcons
import compose.icons.tablericons.Bulb
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import fr.scanneat.domain.model.ProductCategory
import fr.scanneat.presentation.expenses.components.displayLabel
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.ScanEatCard
import fr.scanneat.presentation.ui.theme.Spacing

/**
 * User-requested: "what have I never tried" - product categories this
 * profile has never once scanned (see FoodSearchViewModel.neverTriedCategories'
 * own doc comment). Purely informational (no fabricated "recommended
 * product" - this app has no per-ProductCategory curated exemplar list to
 * draw one from honestly) - capped at 6 so a brand-new profile with almost
 * no scan history doesn't get a wall of 20+ category chips on first open.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NeverTriedBanner(categories: List<ProductCategory>) {
    ScanEatCard(contentPadding = PaddingValues(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Icon(TablerIcons.Bulb, null, tint = AccentCoral, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.foodsearch_never_tried_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            categories.take(6).forEach { category ->
                FilterChip(
                    selected = false,
                    onClick = {},
                    label = { Text(category.displayLabel(), style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(labelColor = OnSurface.copy(0.7f)),
                )
            }
        }
    }
}
