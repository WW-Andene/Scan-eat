package fr.scanneat.presentation.foodsearch.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.ChevronUp
import fr.scanneat.R
import fr.scanneat.presentation.foodsearch.FoodSearchItem
import fr.scanneat.presentation.ui.theme.*
import fr.scanneat.util.formatDecimal

@Composable
internal fun FoodSearchRow(
    item: FoodSearchItem,
    onOpenResult: (Long) -> Unit,
    onOpenOnline: ((FoodSearchItem) -> Unit)? = null,
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
                Surface(shape = RoundedCornerShape(CardRadius.BADGE), color = gColor.copy(0.15f), border = BorderStroke(1.dp, gColor.copy(alpha = STATUS_BORDER_ALPHA))) {
                    Text(
                        grade.label, modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.T2),
                        style = MaterialTheme.typography.labelSmall, color = gColor, fontWeight = FontWeight.Bold,
                    )
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
            }
            Spacer(Modifier.height(Spacing.XS))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DetailStat(stringResource(R.string.dashboard_micro_vitd), "${item.vitDUg.formatDecimal()} µg")
                DetailStat(stringResource(R.string.dashboard_micro_b12), "${item.b12Ug.formatDecimal()} µg")
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
