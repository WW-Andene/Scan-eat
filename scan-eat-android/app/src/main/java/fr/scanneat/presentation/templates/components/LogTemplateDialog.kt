package fr.scanneat.presentation.templates.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.planning.MealTemplate
import fr.scanneat.domain.model.MealSlot
import fr.scanneat.presentation.onboarding.enumSaver
import fr.scanneat.presentation.ui.theme.*
import fr.scanneat.util.formatDecimal

@Composable
internal fun LogTemplateDialog(template: MealTemplate, onDismiss: () -> Unit, onConfirm: (slot: MealSlot, portion: Float) -> Unit) {
    val t = template
    var slot by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(t.meal) }
    var portion by rememberSaveable { mutableFloatStateOf(1f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant,
        shape = RoundedCornerShape(CardRadius.PROMINENT),
        title = { Text(stringResource(R.string.templates_log_dialog_title, t.name), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(stringResource(R.string.logsheet_meal_label), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    MealSlot.values().forEach { s ->
                        FilterChip(selected = slot == s, onClick = { slot = s },
                            label = { Text(s.label(), style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral, labelColor = OnBackground.copy(0.7f)))
                    }
                }
                // Portion scale slider — previously the template was always logged at
                // 100% with no way to adjust for a half portion or a double serving.
                ScanEatDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.templates_log_portion_label), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
                    Text(stringResource(R.string.templates_log_portion_value, portion.toDouble().formatDecimal(1), (t.totalKcal * portion).toInt()), style = MaterialTheme.typography.labelSmall, color = AccentCoral)
                }
                Slider(
                    value = portion, onValueChange = { portion = it },
                    valueRange = 0.25f..3f, steps = 10,
                    colors = SliderDefaults.colors(thumbColor = AccentCoral, activeTrackColor = AccentCoral),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(slot, portion) }) { Text(stringResource(R.string.common_log), color = AccentCoral) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}
