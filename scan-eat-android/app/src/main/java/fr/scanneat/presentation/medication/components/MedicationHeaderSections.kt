package fr.scanneat.presentation.medication.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
        // New: daily adherence streak badge
        if (streakDays > 0) {
            Surface(shape = RoundedCornerShape(50), color = Teal.copy(0.15f)) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.M, vertical = Spacing.XS),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
                ) {
                    Icon(Icons.Default.LocalFireDepartment, null, tint = Teal, modifier = Modifier.size(16.dp))
                    // stringResource, not a hardcoded "j" (French "jour") suffix -
                    // an English-language user saw this exact French fragment
                    // regardless of the app's own in-app language setting.
                    Text(stringResource(R.string.common_streak_days_compact, streakDays), style = MaterialTheme.typography.labelMedium, color = Teal, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Spacer(Modifier.width(1.dp))
        }
        IconButton(onClick = onOpenCalendar) {
            Icon(Icons.Default.CalendarMonth, stringResource(R.string.medication_cd_calendar), tint = OnBackground.copy(0.6f))
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
    }
    Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = semanticRed().copy(0.1f), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(Spacing.M), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Icon(Icons.Default.Warning, null, tint = semanticRed(), modifier = Modifier.size(18.dp))
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
    val allTaken = active.all { m -> todayTaken.any { it.medicationId == m.id } }
    Surface(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        color = if (allTaken) Teal.copy(0.1f) else SurfaceVariant.copy(alpha = 0.42f),
        modifier = Modifier.fillMaxWidth().glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(CardRadius.CONTROL), glowAlpha = 0.06f),
    ) {
        Column(Modifier.padding(horizontal = Spacing.M, vertical = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            Text(
                stringResource(R.string.medication_today_summary_title),
                style = MaterialTheme.typography.labelSmall,
                color = OnSurface.copy(0.5f),
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
                                if (taken) Icons.Default.Check else Icons.Default.Close,
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
