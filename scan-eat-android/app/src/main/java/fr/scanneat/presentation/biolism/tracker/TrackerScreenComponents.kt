package fr.scanneat.presentation.biolism.tracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.biolism.hmsFromSeconds
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.minTouchTarget
import fr.scanneat.util.formatDecimal

// Shared helpers used by 2+ cards/*.kt files. Section-specific composables
// (SubstrateLegendItem etc.) stay colocated in their own card file.

@Composable
internal fun StepperChip(label: String, color: Color, onMinus: () -> Unit, onPlus: () -> Unit) {
    val decreaseDescription = stringResource(R.string.common_stepper_decrease, label)
    val increaseDescription = stringResource(R.string.common_stepper_increase, label)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(0.08f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "−",
            modifier = Modifier
                .minTouchTarget()
                .clickable(onClickLabel = decreaseDescription) { onMinus() }
                .semantics { role = Role.Button; contentDescription = decreaseDescription }
                .wrapContentSize()
                .padding(horizontal = 6.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold,
        )
        Text(label, modifier = Modifier.padding(horizontal = Spacing.XS),
            style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
        Text(
            "+",
            modifier = Modifier
                .minTouchTarget()
                .clickable(onClickLabel = increaseDescription) { onPlus() }
                .semantics { role = Role.Button; contentDescription = increaseDescription }
                .wrapContentSize()
                .padding(horizontal = 6.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold,
        )
    }
}

internal fun formatElapsed(sec: Double): String {
    val (h, m, s) = hmsFromSeconds(sec.toLong())
    return if (h > 0) "${h}h ${m.toString().padStart(2,'0')}m ${s.toString().padStart(2,'0')}s"
    else if (m > 0) "${m}m ${s.toString().padStart(2,'0')}s"
    else "${s}s"
}

internal fun formatFastingTime(fh: Double, weekUnit: String, dayUnit: String, monthUnit: String): String? {
    if (fh <= 0) return null
    return when {
        // Previously hardcoded the English abbreviation "mo" here even though
        // weekUnit/dayUnit were already correctly localized parameters - reachable
        // in practice via FastingRow's own "+1 month" (720h) stepper button.
        fh >= 720  -> "${(fh / 720).formatDecimal()}$monthUnit"
        fh >= 168  -> "${(fh / 168).toInt()}$weekUnit ${((fh % 168) / 24).toInt()}$dayUnit"
        fh >= 48   -> "${(fh / 24).toInt()}$dayUnit ${(fh % 24).toInt()}h"
        fh >= 1    -> "${fh.toInt()}h ${((fh % 1) * 60).toInt()}m"
        else       -> "${(fh * 60).toInt()}m"
    }
}
