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
    active: Boolean, fatPct: Int, npRq: Double,
    onToggle: () -> Unit, onAddHours: (Double) -> Unit,
) {
    val borderColor = if (active) TealBorder else TealTrace
    val bgColor     = if (active) TealHaze   else TealTrace

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardRadius.CONTROL),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(Modifier.padding(Spacing.M), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                // weight(1f) - without it, this Row (whose description text can run
                // long: "< 50g glucides · graisse = carburant principal · RQ ≈ 0.70")
                // and the fixed-content badge Surface below both measured at their own
                // natural width with nothing to reconcile the two, so the badge got
                // squeezed into whatever sliver of the row was left over and its short
                // "npRQ 0.858" text wrapped character-by-character ("np"/"RQ"/"0,8"/"58").
                // weight(1f) reserves the badge's own natural width first and lets this
                // side wrap its own text within whatever's left, instead of the reverse.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f).padding(end = Spacing.S),
                ) {
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
                        style = MaterialTheme.typography.labelSmall, color = Teal, fontWeight = FontWeight.Bold,
                        softWrap = false)
                }
            }
            if (active) {
                // +/- stepper row
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.biolism_ketosis_time_label), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
                    // "1s"/"1m" (French abbreviations for "1 semaine"/"1 mois") previously
                    // stayed hardcoded regardless of language - same fix already applied to
                    // FastingSection.kt's identical stepper row. "1m" in particular reads as
                    // "1 minute" to an English-locale user.
                    val weekLabel = "1${stringResource(R.string.biolism_unit_week)}"
                    val monthLabel = "1${stringResource(R.string.biolism_unit_month)}"
                    listOf("6h" to 6.0, "12h" to 12.0, "24h" to 24.0, weekLabel to 168.0, monthLabel to 720.0).forEach { (label, h) ->
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
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(CardRadius.CONTROL)).clickable { onToggle() },
        shape = RoundedCornerShape(CardRadius.CONTROL),
        color = if (active) GoldHaze else GoldTrace,
        border = BorderStroke(1.dp, if (active) GoldBorder else GoldTrace),
    ) {
        Row(Modifier.fillMaxWidth().padding(Spacing.M), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            // Same weight(1f) fix as KetosisToggleRow above - reserves the fixed
            // "RQ→0.xxx" badge's own width first so this side wraps instead of
            // squeezing the badge into an unreadable sliver.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f).padding(end = Spacing.S),
            ) {
                // onCheckedChange = null, not { onToggle() } - unlike KetosisToggleRow
                // above (whose Checkbox is the row's only actionable element), this
                // Surface's whole modifier chain is already .clickable { onToggle() }
                // (see below), so the nested Checkbox previously duplicated that same
                // action as its own separately-focusable target: TalkBack announced
                // both the row and the checkbox as independently tappable for the
                // identical toggle, splitting one action into two confusing stops.
                // null makes the checkbox purely decorative, letting the Surface own
                // the single actionable region (the officially recommended Compose
                // pattern for a checkbox embedded in a larger clickable row).
                Checkbox(checked = active, onCheckedChange = null,
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
                    style = MaterialTheme.typography.labelSmall, color = Gold, fontWeight = FontWeight.Bold, softWrap = false)
            }
        }
    }
}
