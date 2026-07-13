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
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun KetosisToggleRow(
    active: Boolean, ketoAdapted: Boolean, fatPct: Int, npRq: Double,
    ketoHours: Double, onToggle: () -> Unit, onAddHours: (Double) -> Unit,
) {
    val borderColor = if (active) TealBorder else TealTrace
    val bgColor     = if (active) TealHaze   else TealTrace

    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onToggle() },
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(Modifier.padding(Spacing.M), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Checkbox(checked = active, onCheckedChange = { onToggle() },
                        colors = CheckboxDefaults.colors(checkedColor = Teal, uncheckedColor = Teal.copy(0.4f)))
                    Column {
                        Text(stringResource(R.string.biolism_ketosis_label), style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.biolism_ketosis_desc),
                            style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
                    }
                }
                Surface(shape = RoundedCornerShape(4.dp), color = TealHaze,
                    border = BorderStroke(1.dp, TealGlow)) {
                    Text(if (active) stringResource(R.string.biolism_ketosis_oxi_active, fatPct, npRq)
                         else stringResource(R.string.biolism_ketosis_nprq_inactive, npRq),
                        modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
                        style = MaterialTheme.typography.labelSmall, color = Teal, fontWeight = FontWeight.Bold)
                }
            }
            if (active) {
                // +/- stepper row
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.biolism_ketosis_time_label), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
                    listOf("6h" to 6.0, "12h" to 12.0, "24h" to 24.0, "1s" to 168.0, "1m" to 720.0).forEach { (label, h) ->
                        StepperChip(label = label, color = Teal, onMinus = { onAddHours(-h) }, onPlus = { onAddHours(h) })
                    }
                }
            }
        }
    }
}

@Composable
internal fun AdaptedToggleRow(active: Boolean, ketoHours: Double, onToggle: () -> Unit) {
    val threeWeeks = ketoHours >= 504.0
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onToggle() },
        shape = RoundedCornerShape(12.dp),
        color = if (active) GoldHaze else GoldTrace,
        border = BorderStroke(1.dp, if (active) GoldBorder else GoldTrace),
    ) {
        Row(Modifier.fillMaxWidth().padding(Spacing.M), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Checkbox(checked = active, onCheckedChange = { onToggle() },
                    colors = CheckboxDefaults.colors(checkedColor = Gold, uncheckedColor = Gold.copy(0.4f)))
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.biolism_ketosis_adapted_label), style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.Bold)
                        if (threeWeeks) {
                            Surface(shape = RoundedCornerShape(4.dp), color = GoldHaze, border = BorderStroke(1.dp, GoldGlow)) {
                                Text(stringResource(R.string.biolism_ketosis_auto_badge), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall, color = Gold, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text(stringResource(R.string.biolism_ketosis_3weeks_required), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
                        }
                    }
                    Text(stringResource(R.string.biolism_ketosis_adapted_desc), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
                }
            }
            Surface(shape = RoundedCornerShape(4.dp), color = GoldHaze, border = BorderStroke(1.dp, GoldGlow)) {
                Text(if (active) "RQ→0.715" else "RQ→0.720", modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
                    style = MaterialTheme.typography.labelSmall, color = Gold, fontWeight = FontWeight.Bold)
            }
        }
    }
}
