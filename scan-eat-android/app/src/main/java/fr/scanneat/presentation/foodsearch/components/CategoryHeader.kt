package fr.scanneat.presentation.foodsearch.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.ChevronUp
import fr.scanneat.R
import fr.scanneat.presentation.foodsearch.FoodSearchCategory
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun categoryLabel(category: FoodSearchCategory): String = stringResource(
    when (category) {
        FoodSearchCategory.SCANNED             -> R.string.foodsearch_category_scanned
        FoodSearchCategory.CUSTOM              -> R.string.foodsearch_category_custom
        FoodSearchCategory.FRUITS              -> R.string.foodsearch_category_fruits
        FoodSearchCategory.VEGETABLES          -> R.string.foodsearch_category_vegetables
        FoodSearchCategory.GRAINS_STARCHES     -> R.string.foodsearch_category_grains
        FoodSearchCategory.PROTEINS            -> R.string.foodsearch_category_proteins
        FoodSearchCategory.LEGUMES_NUTS_SEEDS  -> R.string.foodsearch_category_legumes_nuts
        FoodSearchCategory.DAIRY               -> R.string.foodsearch_category_dairy
        FoodSearchCategory.FATS_OILS           -> R.string.foodsearch_category_fats_oils
        FoodSearchCategory.SWEETS_SNACKS       -> R.string.foodsearch_category_sweets
        FoodSearchCategory.BEVERAGES           -> R.string.foodsearch_category_beverages
        FoodSearchCategory.PREPARED_MEALS      -> R.string.foodsearch_category_prepared_meals
        FoodSearchCategory.OTHER               -> R.string.foodsearch_category_other
    },
)

@Composable
internal fun CategoryHeader(category: FoodSearchCategory, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    // Previously only the chevron icon (contentDescription = null) hinted at the
    // expand/collapse state, and the ~32dp row fell short of the 48dp minimum
    // touch target every other tappable row in the app respects - same fix as
    // PillarsSection's own header row.
    val expandedStateDescription = stringResource(if (expanded) R.string.common_expanded else R.string.common_collapsed)
    Row(
        Modifier.fillMaxWidth().minTouchTarget().clip(RoundedCornerShape(CardRadius.CONTROL)).clickable(onClick = onToggle)
            .semantics(mergeDescendants = true) {
                stateDescription = expandedStateDescription
                role = Role.Button
            }
            .padding(horizontal = Spacing.L, vertical = Spacing.S),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.foodsearch_category_header, categoryLabel(category), count),
            style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold,
        )
        Icon(if (expanded) TablerIcons.ChevronUp else TablerIcons.ChevronDown, null, tint = OnBackground.copy(0.5f))
    }
}
