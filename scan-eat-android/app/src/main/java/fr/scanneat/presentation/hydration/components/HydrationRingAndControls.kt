package fr.scanneat.presentation.hydration.components

import compose.icons.tablericons.Minus
import compose.icons.tablericons.Plus
import compose.icons.tablericons.CircleCheck
import compose.icons.TablerIcons
import compose.icons.tablericons.Edit
import compose.icons.tablericons.Droplet
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.health.HYD_GLASS_ML
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun HydrationRingAndControls(
    intakeMl: Int,
    goalMl: Int,
    glasses: Int,
    goalGlasses: Int,
    pct: Float,
    onEditGoal: () -> Unit,
    onRemoveGlass: () -> Unit,
    onAddGlass: () -> Unit,
) {
    // User-reported: every top-level composable below (ring, glass grid, goal-
    // reached banner, -/+ row, footer text) was emitted directly into this
    // function's caller - a single `item { HydrationRingAndControls(...) }` in
    // HydrationScreen's LazyColumn - with no Column of its own. A LazyColumn
    // item's content stacks multiple root children top-to-bottom with zero gap
    // between them (the LazyColumn's own verticalArrangement=spacedBy(Spacing.M)
    // only applies BETWEEN separate item{} blocks, not within one), so the -/+
    // buttons ended up glued directly against the glass grid above and the
    // footer text below. Explicit Column + spacedBy gives this its own internal
    // rhythm, matching the outer LazyColumn's own Spacing.M gap.
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
    // Big ring
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(196.dp)) {
        Box(
            modifier = Modifier
                .size(196.dp)
                .background(
                    Brush.radialGradient(listOf(semanticBlue().copy(alpha = 0.2f), Color.Transparent)),
                    CircleShape,
                ),
        )
        CircularProgressIndicator(
            progress = { pct.coerceIn(0f, 1f) },
            modifier = Modifier.size(196.dp),
            color = semanticBlue(),
            trackColor = SurfaceVariant,
            strokeWidth = 12.dp,
        )
        val editGoalCd = stringResource(R.string.hydration_edit_goal_title)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(onClick = onEditGoal)
                .semantics(mergeDescendants = true) { contentDescription = editGoalCd },
        ) {
            Text("$intakeMl", style = MaterialTheme.typography.headlineLarge, color = semanticBlue(), fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.T2)) {
                Text(stringResource(R.string.hydration_goal_ml, goalMl), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
                Icon(TablerIcons.Edit, null, tint = OnBackground.copy(0.5f), modifier = Modifier.size(IconSize.Micro))
            }
            Text(stringResource(R.string.hydration_glasses_count, glasses, goalGlasses), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
        }
    }

    // Glass grid — filled up to current intake, gold accent past goal
    val totalGlassCells = maxOf(goalGlasses, glasses)
    if (totalGlassCells > 0) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            (0 until totalGlassCells).chunked(8).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    row.forEach { i ->
                        val filled = i < glasses
                        val overGoal = i >= goalGlasses
                        Icon(
                            TablerIcons.Droplet,
                            contentDescription = null,
                            tint = when {
                                !filled -> OnBackground.copy(0.15f)
                                overGoal -> Gold
                                else -> semanticBlue()
                            },
                            modifier = Modifier.size(IconSize.Inline),
                        )
                    }
                }
            }
        }
    }

    if (pct >= 1f) {
        // User-reported: same two-layer Box(glassSheen)+Surface(shadow/clip)
        // construction already fixed elsewhere (see ScanActionControls.kt's
        // own comment) - collapsed into one Box.
        Box(
            Modifier
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(CardRadius.CONTROL))
                .clip(RoundedCornerShape(CardRadius.CONTROL))
                .background(semanticGreen().copy(0.15f), RoundedCornerShape(CardRadius.CONTROL))
                .glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(CardRadius.CONTROL)),
        ) {
            Row(Modifier.padding(Spacing.M), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Icon(TablerIcons.CircleCheck, null, tint = semanticGreen(), modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.hydration_goal_reached), style = MaterialTheme.typography.bodyMedium, color = semanticGreen())
            }
        }
    }

    // User-reported: this row's gap was a bare 24.dp literal, not one of the
    // app's Spacing tokens (XS/SM/M/L/XL/XXL) every other spacedBy() in the
    // app draws from - standardized to the nearest token.
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XL), verticalAlignment = Alignment.CenterVertically) {
        FloatingActionButton(
            onClick = onRemoveGlass,
            containerColor = if (intakeMl > 0) SurfaceVariant else SurfaceVariant.copy(alpha = 0.4f),
            shape = CircleShape,
            modifier = Modifier.size(48.dp),
        ) { Icon(TablerIcons.Minus, stringResource(R.string.common_remove), tint = if (intakeMl > 0) OnSurface else OnSurface.copy(0.3f)) }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.hydration_glass_ml, HYD_GLASS_ML), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.5f))
            Text(stringResource(R.string.hydration_per_glass_label), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
        }

        FloatingActionButton(
            onClick = onAddGlass,
            containerColor = semanticBlue(),
            shape = CircleShape,
            modifier = Modifier.size(48.dp),
        ) { Icon(TablerIcons.Plus, stringResource(R.string.common_add), tint = Color.Black) }
    }

    Text(
        stringResource(R.string.hydration_goal_footer, goalMl),
        style = MaterialTheme.typography.bodySmall,
        color = OnBackground.copy(0.4f),
    )
    }
}
