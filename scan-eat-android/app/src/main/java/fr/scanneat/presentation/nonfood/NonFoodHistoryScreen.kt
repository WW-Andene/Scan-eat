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
import fr.scanneat.domain.engine.nonconsumable.CleansingBase
import fr.scanneat.domain.engine.nonconsumable.FormulaComplexity
import fr.scanneat.domain.engine.nonconsumable.ShowerGelCleansingBase
import fr.scanneat.domain.engine.nonconsumable.computeCosmeticActives
import fr.scanneat.domain.engine.nonconsumable.computeIntimateWipeQuality
import fr.scanneat.domain.engine.nonconsumable.computeMakeupQuality
import fr.scanneat.domain.engine.nonconsumable.computeShampooQuality
import fr.scanneat.domain.engine.nonconsumable.computeShowerGelQuality
import fr.scanneat.domain.engine.nonconsumable.computeToothpasteQuality
import fr.scanneat.domain.engine.nonconsumable.isLikelyGeneralCosmetic
import fr.scanneat.domain.engine.nonconsumable.isLikelyIntimateWipe
import fr.scanneat.domain.engine.nonconsumable.isLikelyMakeup
import fr.scanneat.domain.engine.nonconsumable.isLikelyShampoo
import fr.scanneat.domain.engine.nonconsumable.isLikelyShowerGel
import fr.scanneat.domain.engine.nonconsumable.isLikelyToothpaste
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
                } else {
                    // Added 13/08/2026: the per-category functional scores
                    // (shampoo/gel douche/dentifrice/cosmétique/hygiène
                    // intime/maquillage) built this session previously only
                    // ever showed up in ScanStateOverlay's live scan dialog
                    // - nothing here in History re-derived them from the now-
                    // persisted ingredientsText (see NonFoodScanEntity's own
                    // doc comment), so they were effectively invisible the
                    // moment a user left the scan screen. Same priority
                    // order ScanStateOverlay checks its six isLikelyX gates
                    // in, first match wins - one compact line, not a full
                    // section, to fit this row's existing shape.
                    val functionalBadge = functionalBadgeFor(item.name, item.brand, item.ingredientsText)
                    if (functionalBadge != null) {
                        Text(functionalBadge.first, style = MaterialTheme.typography.labelSmall, color = functionalBadge.second)
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
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    TablerIcons.Heart, stringResource(R.string.common_favorite),
                    tint = if (item.favorite) AccentCoral else OnBackgroundMuted,
                )
            }
            IconButton(onClick = onDelete) { Icon(TablerIcons.Trash, stringResource(R.string.common_delete), tint = OnBackground.copy(0.5f)) }
        }
    }
}

/**
 * Compact single-line functional-score summary for a History row - see this
 * file's own "Added 13/08/2026" comment above on why this exists. Returns
 * (text, color) for the first of the six per-category scores that applies.
 * History can only show one badge per row, so it picks in the same
 * declaration order ScanStateOverlay.kt renders its dialog sections in
 * (shampoo -> showerGel -> toothpaste -> cosmeticActives -> intimateWipe ->
 * makeup) - note the overlay itself is NOT first-match-wins, it shows every
 * matching section at once; this function is a single-badge approximation
 * of that same order, not a behavioral match. Returns null when
 * [ingredientsText] is unavailable or none of the six curated ingredient
 * lists matched anything (see each compute*Quality's own "consistency fix"
 * doc comment on why that's null, not an empty result).
 */
@Composable
private fun functionalBadgeFor(name: String, brand: String, ingredientsText: String?): Pair<String, androidx.compose.ui.graphics.Color>? {
    if (ingredientsText.isNullOrBlank()) return null
    if (isLikelyShampoo(name, brand)) {
        computeShampooQuality(ingredientsText)?.let {
            val (label, color) = when (it.cleansingBase) {
                CleansingBase.GENTLE  -> stringResource(R.string.cleansing_base_gentle) to semanticGreen()
                CleansingBase.MIXED   -> stringResource(R.string.cleansing_base_mixed) to semanticAmber()
                CleansingBase.HARSH   -> stringResource(R.string.cleansing_base_harsh) to semanticAmber()
                CleansingBase.UNKNOWN -> return@let
            }
            return stringResource(R.string.cleansing_base_label, label) to color
        }
    }
    if (isLikelyShowerGel(name, brand)) {
        computeShowerGelQuality(ingredientsText)?.let {
            val (label, color) = when (it.cleansingBase) {
                ShowerGelCleansingBase.GENTLE  -> stringResource(R.string.cleansing_base_gentle) to semanticGreen()
                ShowerGelCleansingBase.MIXED   -> stringResource(R.string.cleansing_base_mixed) to semanticAmber()
                ShowerGelCleansingBase.HARSH   -> stringResource(R.string.cleansing_base_harsh) to semanticAmber()
                ShowerGelCleansingBase.UNKNOWN -> return@let
            }
            return stringResource(R.string.cleansing_base_label, label) to color
        }
    }
    if (isLikelyToothpaste(name, brand)) {
        computeToothpasteQuality(ingredientsText)?.let {
            return if (it.hasFluoride) stringResource(R.string.toothpaste_has_fluoride) to semanticGreen()
            else stringResource(R.string.toothpaste_no_fluoride) to semanticAmber()
        }
    }
    if (isLikelyGeneralCosmetic(name, brand)) {
        computeCosmeticActives(ingredientsText)?.let {
            if (it.hasNiacinamide) return stringResource(R.string.cosmetic_has_niacinamide) to semanticGreen()
            if (it.hasVitaminC) return stringResource(R.string.cosmetic_has_vitamin_c) to semanticGreen()
            if (it.hasRetinoid) return stringResource(R.string.cosmetic_has_retinoid_caution) to semanticAmber()
        }
    }
    if (isLikelyIntimateWipe(name)) {
        computeIntimateWipeQuality(ingredientsText)?.let {
            if (it.hasFragrance) return stringResource(R.string.intimate_wipe_has_fragrance) to semanticAmber()
            if (it.hasAlcohol) return stringResource(R.string.intimate_wipe_has_alcohol) to semanticAmber()
            if (it.hasPhBuffering) return stringResource(R.string.intimate_wipe_has_ph_buffering) to semanticGreen()
        }
    }
    if (isLikelyMakeup(name, brand)) {
        computeMakeupQuality(ingredientsText)?.let {
            if (it.hasTalc) return stringResource(R.string.makeup_has_talc) to semanticAmber()
            if (it.hasRegulatedPreservative) return stringResource(R.string.makeup_has_regulated_preservative) to semanticGreen()
            if (it.hasComedogenicContested) return stringResource(R.string.makeup_has_comedogenic_contested) to OnBackground.copy(0.7f)
        }
    }
    return null
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
