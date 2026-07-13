package fr.scanneat.presentation.biolism.tracker.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.biolism.tracker.StepperChip
import fr.scanneat.presentation.biolism.tracker.formatFastingTime
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun FastingRow(
    active: Boolean, fastingHours: Double, onToggle: () -> Unit, onLogMeal: () -> Unit, onAddHours: (Double) -> Unit,
    realFastHours: Double? = null, onImportRealFast: () -> Unit = {},
) {
    val fastFmt = formatFastingTime(fastingHours, stringResource(R.string.biolism_unit_week), stringResource(R.string.biolism_unit_day))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardRadius.CONTROL),
        color = if (active && fastingHours > 0) VioletHaze else VioletTrace,
        border = BorderStroke(1.dp, if (active && fastingHours > 0) VioletBorder else VioletTrace),
    ) {
        Column(Modifier.padding(Spacing.M), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Checkbox(checked = active, onCheckedChange = { onToggle() },
                        colors = CheckboxDefaults.colors(checkedColor = Violet, uncheckedColor = Violet.copy(0.4f)))
                    Column {
                        Text(stringResource(R.string.biolism_fasting_label), style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.Bold)
                        Text(if (active && fastFmt != null) stringResource(R.string.biolism_fasting_status_anchored, fastFmt)
                             else if (active) stringResource(R.string.biolism_fasting_status_prompt)
                             else stringResource(R.string.biolism_fasting_status_disabled),
                            style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
                    }
                }
                if (active) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (fastFmt != null) {
                            Surface(shape = RoundedCornerShape(4.dp), color = VioletHaze, border = BorderStroke(1.dp, VioletGlow)) {
                                Text(fastFmt, modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
                                    style = MaterialTheme.typography.labelSmall, color = Violet, fontWeight = FontWeight.Bold)
                            }
                        }
                        Surface(
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { onLogMeal() },
                            shape = RoundedCornerShape(4.dp),
                            color = VioletHaze,
                            border = BorderStroke(1.dp, Violet.copy(0.4f)),
                        ) {
                            Text(stringResource(R.string.biolism_fasting_log_meal), modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
                                style = MaterialTheme.typography.labelSmall, color = Violet, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (active) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.biolism_fasting_time_label), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
                    listOf("6h" to 6.0, "12h" to 12.0, "24h" to 24.0, "1s" to 168.0, "1m" to 720.0).forEach { (label, h) ->
                        StepperChip(label = label, color = Violet, onMinus = { onAddHours(-h) }, onPlus = { onAddHours(h) })
                    }
                }
            }
            // Bridges to the real Jeûne (Fasting tab) timer, which previously had zero
            // connection to Biolism's own (deliberately separate, manual) fasting input -
            // a one-tap import instead of a forced live sync, since Biolism's toggle is
            // meant to also support exploring a hypothetical fast the user isn't actually
            // running (see biolism_fasting_status_disabled).
            if (realFastHours != null) {
                Surface(
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { onImportRealFast() },
                    shape = RoundedCornerShape(4.dp),
                    color = VioletHaze,
                    border = BorderStroke(1.dp, Violet.copy(0.4f)),
                ) {
                    Text(
                        stringResource(R.string.biolism_fasting_import_real, realFastHours),
                        modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
                        style = MaterialTheme.typography.labelSmall, color = Violet, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
