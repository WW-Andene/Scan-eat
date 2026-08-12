package fr.scanneat.presentation.nonfood

import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Droplet
import compose.icons.tablericons.Heart
import compose.icons.tablericons.Trash
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.repository.nonfood.NonFoodScanItem
import fr.scanneat.domain.engine.nonconsumable.FormulaComplexity
import fr.scanneat.presentation.ui.theme.*
import java.text.DateFormat
import java.util.Date

/**
 * User-requested: history + favorites for non-food scans (shampoo/gel
 * douche/cosmétiques...), same as food's ScanHistoryScreen - see
 * NonFoodScanEntity's own doc comment for why this is a separate, smaller
 * screen rather than a filter mode bolted onto the food one (different
 * shape of data - no score/grade, no Product/ScoreAudit).
 */
@Composable
fun NonFoodHistoryScreen(viewModel: NonFoodHistoryViewModel = hiltViewModel(), onBack: () -> Unit) {
    val items = viewModel.filtered.collectAsStateWithLifecycle()
    val favoritesOnly = viewModel.favoritesOnly.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val logFailedMessage = stringResource(R.string.common_log_failed)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(logFailedMessage)
            viewModel.clearActionFailed()
        }
    }

    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.nonfood_history_title), color = OnBackground) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) } },
        actions = {
            FilterChip(
                selected = favoritesOnly.value,
                onClick = { viewModel.setFavoritesOnly(!favoritesOnly.value) },
                label = { Text(stringResource(R.string.common_favorites), style = MaterialTheme.typography.labelSmall) },
                leadingIcon = { Icon(TablerIcons.Heart, null, modifier = Modifier.size(IconSize.Compact)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral),
            )
        },
        snackbarHost = { ScanEatSnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold).padding(horizontal = Spacing.L),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            item { Spacer(Modifier.height(Spacing.L)) }
            if (items.value.isEmpty()) {
                item {
                    EmptyListState(
                        TablerIcons.Droplet,
                        stringResource(if (favoritesOnly.value) R.string.nonfood_history_empty_favorites else R.string.nonfood_history_empty),
                    )
                }
            } else {
                items(items.value, key = { it.id }) { item ->
                    NonFoodHistoryRow(item = item, onToggleFavorite = { viewModel.toggleFavorite(item) }, onDelete = { viewModel.delete(item.id) })
                }
            }
            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }
}

@Composable
private fun NonFoodHistoryRow(item: NonFoodScanItem, onToggleFavorite: () -> Unit, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        color = SurfaceVariant.copy(alpha = StandardCardAlpha),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.M),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge, color = OnBackground)
                val dateStr = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(item.scannedAt))
                val brandPart = item.brand.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
                Text("$dateStr$brandPart", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
                if (item.prohibitedCount > 0) {
                    Text(
                        stringResource(R.string.nonfood_history_prohibited_badge, item.prohibitedCount),
                        style = MaterialTheme.typography.labelSmall, color = semanticRed(),
                    )
                } else if (item.restrictedCount > 0) {
                    Text(
                        stringResource(R.string.nonfood_history_restricted_badge, item.restrictedCount),
                        style = MaterialTheme.typography.labelSmall, color = semanticAmber(),
                    )
                } else if (item.complexity != null) {
                    val complexityColor = when (item.complexity) {
                        FormulaComplexity.SIMPLE -> semanticGreen()
                        FormulaComplexity.MODERATE -> semanticAmber()
                        else -> semanticRed()
                    }
                    Text(
                        stringResource(R.string.nonconsumable_ingredient_count, item.ingredientCount ?: 0, complexityLabel(item.complexity)),
                        style = MaterialTheme.typography.labelSmall, color = complexityColor,
                    )
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    TablerIcons.Heart, stringResource(R.string.common_favorite),
                    tint = if (item.favorite) AccentCoral else OnBackground.copy(0.4f),
                )
            }
            IconButton(onClick = onDelete) { Icon(TablerIcons.Trash, stringResource(R.string.common_delete), tint = OnBackground.copy(0.5f)) }
        }
    }
}

@Composable
private fun complexityLabel(complexity: FormulaComplexity): String = stringResource(
    when (complexity) {
        FormulaComplexity.SIMPLE   -> R.string.nonconsumable_complexity_simple
        FormulaComplexity.MODERATE -> R.string.nonconsumable_complexity_moderate
        FormulaComplexity.COMPLEX  -> R.string.nonconsumable_complexity_complex
        FormulaComplexity.UNKNOWN  -> R.string.nonconsumable_transparency_no_data
    },
)
