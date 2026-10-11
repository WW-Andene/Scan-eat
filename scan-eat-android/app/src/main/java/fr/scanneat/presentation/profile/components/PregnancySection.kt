package fr.scanneat.presentation.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.domain.engine.scoring.PregnancyTrimester
import fr.scanneat.domain.engine.scoring.pregnancyTrimester
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.semanticAmber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * User-requested: trimester-adapted calorie/protein/iron/folate targets
 * instead of the same static "pregnancy" caution the whole pregnancy - see
 * Profile.pregnancyStartDate/dailyTargets' own doc comments. Only reachable
 * when "pregnancy" is checked in the conditions selector above (this
 * section's own call site in ProfileScreen).
 */
@Composable
internal fun PregnancySection(startDate: LocalDate?, onDateChange: (LocalDate?) -> Unit) {
    var showDatePicker by remember { mutableStateOf(false) }
    val trimester = startDate?.let { pregnancyTrimester(it, LocalDate.now()) }

    ProfileSection(stringResource(R.string.profile_section_pregnancy)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(stringResource(R.string.profile_pregnancy_disclaimer), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
            Row(
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    startDate?.let { it.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) } ?: stringResource(R.string.profile_pregnancy_no_date),
                    style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.8f),
                )
                Row {
                    if (startDate != null) {
                        TextButton(onClick = { onDateChange(null) }) { Text(stringResource(R.string.pantry_clear_expiry), color = OnBackground.copy(0.6f)) }
                    }
                    TextButton(onClick = { showDatePicker = true }) { Text(stringResource(R.string.profile_pregnancy_set_date), color = AccentCoral) }
                }
            }
            trimester?.let {
                Text(
                    stringResource(
                        when (it) {
                            PregnancyTrimester.FIRST  -> R.string.profile_pregnancy_trimester_1
                            PregnancyTrimester.SECOND -> R.string.profile_pregnancy_trimester_2
                            PregnancyTrimester.THIRD  -> R.string.profile_pregnancy_trimester_3
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium, color = semanticAmber(),
                )
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = (startDate ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onDateChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.common_save), color = AccentCoral) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = AccentCoral,
                    todayDateBorderColor      = AccentCoral,
                    todayContentColor         = AccentCoral,
                ),
            )
        }
    }
}
