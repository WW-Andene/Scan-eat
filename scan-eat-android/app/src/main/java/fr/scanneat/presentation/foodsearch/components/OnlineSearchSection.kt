package fr.scanneat.presentation.foodsearch.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.Search
import fr.scanneat.R
import fr.scanneat.presentation.foodsearch.FoodSearchItem
import fr.scanneat.presentation.foodsearch.OnlineSearchState
import fr.scanneat.presentation.ui.theme.*

/**
 * "Rechercher en ligne" - explicit-only (never auto-fires on typing, see
 * FoodSearchViewModel.searchOnline's own doc comment), so it's a button + its
 * own result state rather than folding into the always-live local search
 * above. Shown whenever the query box isn't empty, regardless of whether the
 * three local sources already found something - a name/ingredient/additive
 * this user has never scanned or added is still a legitimate reason to look
 * further even if a same-named local item exists.
 */
@Composable
internal fun OnlineSearchSection(
    query: String,
    state: OnlineSearchState,
    results: List<FoodSearchItem>,
    onSearchOnline: () -> Unit,
    onOpenItem: (FoodSearchItem) -> Unit,
) {
    Column(Modifier.padding(horizontal = Spacing.L, vertical = Spacing.XS)) {
        if (state == OnlineSearchState.IDLE) {
            Text(
                stringResource(R.string.foodsearch_online_hint),
                style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f),
            )
            Spacer(Modifier.height(Spacing.XS))
        }
        when (state) {
            OnlineSearchState.LOADING -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AccentCoral)
                Spacer(Modifier.width(Spacing.S))
                Text(stringResource(R.string.foodsearch_online_loading), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
            }
            // F21 (docs/design-audit-step8-components-shape.md): was a bare Text in
            // MaterialTheme.colorScheme.error — this app's default Material error color,
            // not ErrorBanner's shared semanticRed()/icon/surface language every other
            // persistent (non-transient) error in the app now uses. The retry button
            // is already rendered right below regardless of state, so no actionLabel here.
            OnlineSearchState.ERROR -> ErrorBanner(message = stringResource(R.string.foodsearch_online_error))
            OnlineSearchState.EMPTY -> Text(
                stringResource(R.string.foodsearch_online_empty, query),
                style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f),
            )
            else -> {}
        }
        if (state == OnlineSearchState.IDLE || state == OnlineSearchState.ERROR || state == OnlineSearchState.EMPTY) {
            Spacer(Modifier.height(Spacing.XS))
            OutlinedButton(onClick = onSearchOnline, shape = RoundedCornerShape(CardRadius.CONTROL)) {
                Icon(TablerIcons.Search, null, modifier = Modifier.size(IconSize.Small))
                Spacer(Modifier.width(Spacing.XS))
                Text(stringResource(R.string.foodsearch_online_search_button), style = MaterialTheme.typography.labelMedium)
            }
        }
        if (state == OnlineSearchState.SUCCESS && results.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.S))
            Text(
                stringResource(R.string.foodsearch_online_section_header, results.size),
                style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(Spacing.XS))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                results.forEach { item -> FoodSearchRow(item, onOpenResult = {}, onOpenOnline = onOpenItem) }
            }
        }
    }
}
