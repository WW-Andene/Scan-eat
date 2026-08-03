package fr.scanneat.presentation.hydration.components

import compose.icons.tablericons.Minus
import compose.icons.tablericons.Plus
import compose.icons.tablericons.CircleCheck
import compose.icons.TablerIcons
import compose.icons.tablericons.Edit
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
    // Big ring
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .background(
                    Brush.radialGradient(listOf(semanticBlue().copy(alpha = 0.2f), Color.Transparent)),
                    CircleShape,
                ),
        )
        CircularProgressIndicator(
            progress = { pct.coerceIn(0f, 1f) },
            modifier = Modifier.size(180.dp),
            color = semanticBlue(),
            trackColor = SurfaceVariant,
            strokeWidth = 14.dp,
        )
        val editGoalCd = stringResource(R.string.hydration_edit_goal_title)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(onClick = onEditGoal)
                .semantics(mergeDescendants = true) { contentDescription = editGoalCd },
        ) {
            Text("$intakeMl", style = MaterialTheme.typography.headlineLarge, color = semanticBlue(), fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.hydration_goal_ml, goalMl), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
                Icon(TablerIcons.Edit, null, tint = OnBackground.copy(0.4f), modifier = Modifier.size(12.dp))
            }
            Text(stringResource(R.string.hydration_glasses_count, glasses, goalGlasses), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
        }
    }

    // Glass grid — filled up to current intake, gold accent past goal
    val totalGlassCells = maxOf(goalGlasses, glasses)
    if (totalGlassCells > 0) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (0 until totalGlassCells).chunked(8).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { i ->
                        val filled = i < glasses
                        val overGoal = i >= goalGlasses
                        Icon(
                            Icons.Rounded.Opacity,
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
        Box(Modifier.glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(CardRadius.CONTROL))) {
            Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = semanticGreen().copy(0.15f), shadowElevation = 3.dp) {
                Row(Modifier.padding(Spacing.M), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Icon(TablerIcons.CircleCheck, null, tint = semanticGreen(), modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.hydration_goal_reached), style = MaterialTheme.typography.bodyMedium, color = semanticGreen())
                }
            }
        }
    }

    // Controls
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        FloatingActionButton(
            onClick = onRemoveGlass,
            containerColor = if (intakeMl > 0) SurfaceVariant else SurfaceVariant.copy(alpha = 0.4f),
            shape = CircleShape,
            modifier = Modifier.size(56.dp),
        ) { Icon(TablerIcons.Minus, stringResource(R.string.common_remove), tint = if (intakeMl > 0) OnSurface else OnSurface.copy(0.3f)) }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.hydration_glass_ml, HYD_GLASS_ML), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.5f))
            Text(stringResource(R.string.hydration_per_glass_label), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
        }

        FloatingActionButton(
            onClick = onAddGlass,
            containerColor = semanticBlue(),
            shape = CircleShape,
            modifier = Modifier.size(56.dp),
        ) { Icon(TablerIcons.Plus, stringResource(R.string.common_add), tint = Color.Black) }
    }

    Text(
        stringResource(R.string.hydration_goal_footer, goalMl),
        style = MaterialTheme.typography.bodySmall,
        color = OnBackground.copy(0.4f),
    )
}
