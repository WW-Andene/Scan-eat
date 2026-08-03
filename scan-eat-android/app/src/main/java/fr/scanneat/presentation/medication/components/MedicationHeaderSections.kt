package fr.scanneat.presentation.medication.components

import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.Calendar
import compose.icons.TablerIcons
import compose.icons.tablericons.Check
import compose.icons.tablericons.X
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.health.Medication
import fr.scanneat.data.repository.health.MedicationLogEntry
import fr.scanneat.presentation.medication.DrugGroup
import fr.scanneat.presentation.medication.InteractionWarning
import fr.scanneat.presentation.medication.MedicationViewModel.DayAdherence
import fr.scanneat.presentation.ui.theme.*
import java.time.LocalDate

@Composable
internal fun MedicationStreakRow(streakDays: Int, onOpenCalendar: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        // Daily adherence streak badge
        if (streakDays > 0) {
            StreakBadge(streakDays, Teal)
        } else {
            Spacer(Modifier.width(1.dp))
        }
        IconButton(onClick = onOpenCalendar) {
            Icon(TablerIcons.Calendar, stringResource(R.string.medication_cd_calendar), tint = OnBackground.copy(0.6f))
        }
    }
}

@Composable
internal fun MedicationInteractionWarningBanner(warning: InteractionWarning) {
    val message = when (warning) {
        is InteractionWarning.GroupDuplicate -> {
            val groupLabel = when (warning.group) {
                DrugGroup.ANTICOAGULANTS -> stringResource(R.string.medication_group_anticoagulants)
                DrugGroup.ANTIPLATELETS  -> stringResource(R.string.medication_group_antiplatelets)
                DrugGroup.NSAIDS         -> stringResource(R.string.medication_group_nsaids)
                DrugGroup.SSRI_SNRI      -> stringResource(R.string.medication_group_ssri_snri)
                DrugGroup.MAOI           -> stringResource(R.string.medication_group_maoi)
            }
            stringResource(R.string.medication_interaction_group_dup, groupLabel)
        }
        is InteractionWarning.AnticoagNsaid -> stringResource(R.string.medication_interaction_anticoag_nsaid)
        is InteractionWarning.SsriMaoi      -> stringResource(R.string.medication_interaction_ssri_maoi)
        is InteractionWarning.AnticoagAntiplatelet -> stringResource(R.string.medication_interaction_anticoag_antiplatelet)
        is InteractionWarning.NsaidAntiplatelet -> stringResource(R.string.medication_interaction_nsaid_antiplatelet)
    }
    Surface(
        shape = RoundedCornerShape(CardRadius.CONTROL), color = semanticRed().copy(0.1f),
        modifier = Modifier.fillMaxWidth()
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(CardRadius.CONTROL), ambientColor = ShadowTint, spotColor = ShadowTint)
            .clip(RoundedCornerShape(CardRadius.CONTROL)),
        // app-audit §E5: a drug-interaction warning (anticoagulant/NSAID, MAOI/SSRI,
        // etc.) is exactly the safety-relevant surface ErrorBanner/CautionBanner
        // already got real elevation for - this one was still flat.
        shadowElevation = 0.dp,
        // art-direction-engine §CARDS: CautionBanner (health-condition caution) frames
        // its semantic tint with a matching border for extra material weight - this
        // banner, for an active drug interaction (arguably higher severity), had none.
        border = BorderStroke(1.dp, semanticRed().copy(alpha = 0.35f)),
    ) {
        Row(modifier = Modifier.padding(Spacing.M), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Icon(TablerIcons.AlertTriangle, null, tint = semanticRed(), modifier = Modifier.size(18.dp))
            Column {
                Text(stringResource(R.string.medication_interaction_title), style = MaterialTheme.typography.labelMedium, color = semanticRed(), fontWeight = FontWeight.Bold)
                Text(message, style = MaterialTheme.typography.bodySmall, color = semanticRed().copy(0.8f))
                // Was semanticRed().copy(0.6f) - a safety-relevant CTA (anticoagulant/
                // NSAID interaction) rendered at reduced contrast over an already-tinted
                // (semanticRed 0.1f) background, likely below WCAG AA 4.5:1. Full-opacity
                // to match the title/icon above it; alpha is reserved for the message body.
                Text(stringResource(R.string.medication_interaction_cta), style = MaterialTheme.typography.labelSmall, color = semanticRed(), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun MedicationTodaySummaryCard(medications: List<Medication>, todayTaken: List<MedicationLogEntry>) {
    val active = medications.filter { it.active }
    val allTaken = active.isNotEmpty() && active.all { m -> todayTaken.any { it.medicationId == m.id } }
    Surface(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        color = if (allTaken) Teal.copy(0.1f) else SurfaceVariant.copy(alpha = 0.42f),
        modifier = Modifier.fillMaxWidth().glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(CardRadius.CONTROL), glowAlpha = 0.06f)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(CardRadius.CONTROL), ambientColor = ShadowTint, spotColor = ShadowTint)
            .clip(RoundedCornerShape(CardRadius.CONTROL)),
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(horizontal = Spacing.M, vertical = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            // Was the static "Prise du jour" title regardless of completion - only
            // the card's background tint changed color, unlike Hydration/Activity's
            // explicit "goal reached" copy for the equivalent all-done moment.
            Text(
                stringResource(if (allTaken) R.string.medication_all_taken_today else R.string.medication_today_summary_title),
                style = MaterialTheme.typography.labelSmall,
                color = if (allTaken) Teal else OnSurface.copy(0.5f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS), modifier = Modifier.fillMaxWidth()) {
                active.forEach { m ->
                    val taken = todayTaken.any { it.medicationId == m.id }
                    Surface(shape = RoundedCornerShape(50), color = if (taken) Teal.copy(0.2f) else OnSurface.copy(0.08f)) {
                        Row(
                            Modifier.padding(horizontal = Spacing.S, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                if (taken) TablerIcons.Check else TablerIcons.X,
                                null,
                                tint = if (taken) Teal else OnSurface.copy(0.35f),
                                modifier = Modifier.size(10.dp),
                            )
                            Text(
                                m.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (taken) Teal else OnSurface.copy(0.5f),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MedicationWeeklyAdherenceChart(weeklyAdherence: List<DayAdherence>) {
    ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), contentPadding = PaddingValues(Spacing.M)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            Text(stringResource(R.string.medication_7day_chart_title), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
            // design-aesthetic-audit §DDV: this is the app's one chart whose bars encode
            // meaning purely through color (teal=full, amber=partial, red=missed, gray=no
            // data) with no textual fallback anywhere on screen - a WCAG 1.4.1 "use of
            // color" gap for colorblind users. Every other legend-needing surface in the
            // app (Calendar, Dashboard) already reuses this same LegendDot component.
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                fr.scanneat.presentation.calendar.components.LegendDot(Teal, stringResource(R.string.medication_legend_full))
                fr.scanneat.presentation.calendar.components.LegendDot(semanticAmber(), stringResource(R.string.medication_legend_partial))
                fr.scanneat.presentation.calendar.components.LegendDot(semanticRed(), stringResource(R.string.medication_legend_missed))
                // The gray "no data" bar (day.pct == null, e.g. a recently-added
                // medication with under 7 days of history) had no legend entry at
                // all, contradicting this legend's own purpose.
                fr.scanneat.presentation.calendar.components.LegendDot(OnSurface.copy(0.12f), stringResource(R.string.medication_legend_no_data))
            }
            Row(modifier = Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                weeklyAdherence.forEach { day ->
                    val frac = (day.pct ?: 0) / 100f
                    val barColor = when {
                        day.pct == null -> OnSurface.copy(0.12f)
                        day.pct >= 100   -> Teal
                        day.pct > 0      -> semanticAmber()
                        else             -> semanticRed()
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight(frac.coerceAtLeast(0.04f)).background(barColor, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                weeklyAdherence.forEach { day ->
                    Text(
                        day.date.dayOfWeek.name.take(1),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = OnSurface.copy(if (day.date == LocalDate.now()) 0.8f else 0.4f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}
