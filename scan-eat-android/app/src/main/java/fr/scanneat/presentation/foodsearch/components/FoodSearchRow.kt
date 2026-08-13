package fr.scanneat.presentation.foodsearch.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.ChevronUp
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Star
import fr.scanneat.R
import fr.scanneat.presentation.foodsearch.FoodSearchItem
import fr.scanneat.presentation.ui.theme.*
import fr.scanneat.util.formatDecimal

@Composable
internal fun FoodSearchRow(
    item: FoodSearchItem,
    onOpenResult: (Long) -> Unit,
    onOpenOnline: ((FoodSearchItem) -> Unit)? = null,
    // User-requested: favorite/log a result directly from search, without
    // first navigating to the full Result screen - null (the default) keeps
    // every other FoodSearchRow call site unchanged.
    onToggleFavorite: ((FoodSearchItem) -> Unit)? = null,
    onLog: ((FoodSearchItem) -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val onClick = when {
        item.scanId != null -> ({ onOpenResult(item.scanId) })
        // Online (Open Food Facts search) result, never scanned/saved by this
        // user yet - tapping persists it first (see
        // FoodSearchViewModel.openOnlineItem) then opens the real Result
        // screen, same as item.scanId != null above.
        item.barcode != null && onOpenOnline != null -> ({ onOpenOnline(item) })
        else -> ({ expanded = !expanded })
    }
    ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), onClick = onClick) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium, color = OnSurface, fontWeight = FontWeight.Medium)
                Text(
                    stringResource(R.string.foodsearch_macro_line, item.kcal.roundToIntSafe(), item.proteinG.formatDecimal(), item.carbsG.formatDecimal(), item.fatG.formatDecimal()),
                    style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f),
                )
            }
            // Only ever set for a scanned product (see FoodSearchItem's own doc) -
            // the one visual cue distinguishing "your real scanned product, tap for
            // its full score" from "a generic curated reference, tap to expand macros."
            item.grade?.let { grade ->
                val gColor = gradeColor(grade)
                Surface(shape = RoundedCornerShape(CardRadius.BADGE), color = gColor.copy(0.15f), border = BorderStroke(2.dp, gColor.copy(alpha = STATUS_BORDER_ALPHA))) {
                    Text(
                        grade.label, modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.T2),
                        style = MaterialTheme.typography.labelSmall, color = gColor, fontWeight = FontWeight.Bold,
                    )
                }
            }
            // User-requested: favorite/log a result right from search, instead of
            // only from the full Result screen (only reachable for a scanned row).
            // Same star tint-only pattern as ScanHistoryRow's identical button.
            val haptics = LocalHapticFeedback.current
            if (onToggleFavorite != null) {
                IconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onToggleFavorite(item) }, modifier = Modifier.size(IconSize.Inline + Spacing.M)) {
                    Icon(
                        TablerIcons.Star,
                        stringResource(if (item.favorite) R.string.result_cd_unfavorite else R.string.result_cd_favorite),
                        tint = if (item.favorite) Gold else OnSurface.copy(0.3f),
                        modifier = Modifier.size(IconSize.Compact),
                    )
                }
            }
            if (onLog != null) {
                IconButton(onClick = { onLog(item) }, modifier = Modifier.size(IconSize.Inline + Spacing.M)) {
                    Icon(TablerIcons.Plus, stringResource(R.string.logsheet_title), tint = AccentCoral, modifier = Modifier.size(IconSize.Compact))
                }
            }
            // Rows with no grade (i.e. not a scanned product) expand in place instead
            // of navigating away - nothing signaled that distinction before, so a tap
            // on one row type could unexpectedly navigate while the same tap on
            // another type expanded a detail panel, with no visible cue why.
            if (item.grade == null) {
                Icon(
                    if (expanded) TablerIcons.ChevronUp else TablerIcons.ChevronDown,
                    null, tint = OnSurface.copy(0.5f),
                )
            }
        }
        if (expanded) {
            HorizontalDivider(color = OnSurface.copy(0.08f), modifier = Modifier.padding(vertical = Spacing.XS))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailStat(stringResource(R.string.dashboard_micro_fiber), "${item.fiberG.formatDecimal()} g")
                DetailStat(stringResource(R.string.dashboard_micro_iron), "${item.ironMg.formatDecimal()} mg")
                DetailStat(stringResource(R.string.dashboard_micro_calcium), "${item.calciumMg.formatDecimal()} mg")
                DetailStat(stringResource(R.string.dashboard_micro_vitc), "${item.vitCMg.formatDecimal()} mg")
            }
            Spacer(Modifier.height(Spacing.XS))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailStat(stringResource(R.string.dashboard_micro_vitd), "${item.vitDUg.formatDecimal()} µg")
                DetailStat(stringResource(R.string.dashboard_micro_b12), "${item.b12Ug.formatDecimal()} µg")
                DetailStat(stringResource(R.string.dashboard_micro_vita), "${item.vitAUg.formatDecimal()} µg")
                DetailStat(stringResource(R.string.dashboard_micro_folate), "${item.b9Ug.formatDecimal()} µg")
            }
            // User-reported (2nd round): "cover them" - magnesium/potassium/zinc were
            // wired into the diary/dashboard but this row's own detail panel still
            // stopped at the original 6 stats above.
            Spacer(Modifier.height(Spacing.XS))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailStat(stringResource(R.string.dashboard_micro_magnesium), "${item.magnesiumMg.formatDecimal()} mg")
                DetailStat(stringResource(R.string.dashboard_micro_potassium), "${item.potassiumMg.formatDecimal()} mg")
                DetailStat(stringResource(R.string.dashboard_micro_zinc), "${item.zincMg.formatDecimal()} mg")
                DetailStat(stringResource(R.string.result_nutri_salt), "${item.saltG.formatDecimal()} g")
            }
        }
    }
}

@Composable
internal fun DetailStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelMedium, color = AccentCoral, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
    }
}

private fun Double.roundToIntSafe(): Int = kotlin.math.round(this).toInt()
