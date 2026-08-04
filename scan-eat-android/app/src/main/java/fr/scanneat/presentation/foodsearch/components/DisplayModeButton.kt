package fr.scanneat.presentation.foodsearch.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.foodsearch.SearchDisplayMode
import fr.scanneat.presentation.ui.theme.*

internal fun modeLabel(mode: SearchDisplayMode): Int = when (mode) {
    SearchDisplayMode.PRODUCTS -> R.string.foodsearch_mode_products
    SearchDisplayMode.LINKS    -> R.string.foodsearch_mode_links
    SearchDisplayMode.BOTH     -> R.string.foodsearch_mode_both
}

/**
 * Single button, three states - one tap cycles Produits -> Liens -> Produits
 * et liens -> Produits, rather than three separate toggle chips (fewer taps
 * to reach any of the three, and no extra row of controls competing with the
 * filter chips already on this screen).
 */
@Composable
internal fun DisplayModeButton(mode: SearchDisplayMode, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(CardRadius.CONTROL),
        // User-reported: shorter than the filter popup's trigger pill on this
        // same screen (CollapsibleFilterBar's Surface uses heightIn(min = 48.dp);
        // this button was left at Material's default OutlinedButton min height).
        modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.XS).heightIn(min = 48.dp),
    ) {
        Icon(Icons.Rounded.SwapHoriz, null, modifier = Modifier.size(IconSize.Small))
        Spacer(Modifier.width(Spacing.XS))
        Text(stringResource(R.string.foodsearch_mode_button, stringResource(modeLabel(mode))), style = MaterialTheme.typography.labelMedium)
    }
}
