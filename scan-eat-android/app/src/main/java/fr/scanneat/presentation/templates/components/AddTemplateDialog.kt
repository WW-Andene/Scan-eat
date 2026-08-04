package fr.scanneat.presentation.templates.components

import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.model.MealSlot
import fr.scanneat.presentation.onboarding.enumSaver
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun AddTemplateDialog(onDismiss: () -> Unit, onCreate: (name: String, meal: MealSlot) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var meal by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(MealSlot.LUNCH) }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.templates_add_dialog_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.recipes_field_name)) }, singleLine = true, colors = scanEatTextFieldColors())
                Text(stringResource(R.string.logsheet_meal_label), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    MealSlot.values().forEach { s ->
                        FilterChip(selected = meal == s, onClick = { meal = s },
                            label = { Text(s.label(), style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral, labelColor = OnBackground.copy(0.7f)))
                    }
                }
            }
        },
        confirmButton = {
            // Was passing the raw untrimmed name straight through - a name typed with
            // leading/trailing spaces saved verbatim, looking subtly "off" wherever
            // it's rendered and defeating exact-match lookups elsewhere. Same trim
            // fix RenameDialog already got this session, applied to template creation.
            val nameValid = name.trim().isNotBlank()
            TextButton(onClick = { onCreate(name.trim(), meal) }, enabled = nameValid) {
                Text(stringResource(R.string.common_create), color = if (nameValid) AccentCoral else OnBackground.copy(0.3f))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}
