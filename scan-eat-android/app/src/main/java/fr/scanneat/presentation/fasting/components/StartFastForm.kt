package fr.scanneat.presentation.fasting.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StartFastForm(
    targetHours: Int, onTargetHoursChange: (Int) -> Unit,
    customMode: Boolean, onCustomModeChange: (Boolean) -> Unit,
    customStart: String, onCustomStartChange: (String) -> Unit,
    customEnd: String, onCustomEndChange: (String) -> Unit,
    customHours: Double?,
    onStart: (Int) -> Unit,
) {
    Text(stringResource(R.string.fasting_start_title), style = MaterialTheme.typography.titleMedium, color = OnSurface, fontWeight = FontWeight.SemiBold)
    Text(stringResource(R.string.fasting_target_duration_label), style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        listOf(12, 16, 18, 20, 24).forEach { h ->
            FilterChip(
                selected = !customMode && targetHours == h, onClick = { onCustomModeChange(false); onTargetHoursChange(h) },
                label = { Text(stringResource(R.string.fasting_hours_chip, h), style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                    labelColor = OnBackground.copy(0.7f),
                ),
            )
        }
        FilterChip(
            selected = customMode, onClick = { onCustomModeChange(true) },
            label = { Text(stringResource(R.string.fasting_custom_chip), style = MaterialTheme.typography.labelMedium) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                labelColor = OnBackground.copy(0.7f),
            ),
        )
    }
    if (customMode) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = customStart, onValueChange = onCustomStartChange,
                label = { Text(stringResource(R.string.fasting_custom_start_label)) },
                singleLine = true, modifier = Modifier.width(110.dp),
                textStyle = MaterialTheme.typography.bodySmall,
            )
            Text("→", style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.5f))
            OutlinedTextField(
                value = customEnd, onValueChange = onCustomEndChange,
                label = { Text(stringResource(R.string.fasting_custom_end_label)) },
                singleLine = true, modifier = Modifier.width(110.dp),
                textStyle = MaterialTheme.typography.bodySmall,
            )
        }
        if (customHours != null) {
            Text(stringResource(R.string.fasting_target_progress, customHours.toInt()), style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
        }
    }
    val effectiveHours = if (customMode) customHours?.toInt() else targetHours
    ScanEatPrimaryButton(
        onClick = { effectiveHours?.let(onStart) },
        enabled = effectiveHours != null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.fasting_start_button, effectiveHours ?: targetHours))
    }
}
