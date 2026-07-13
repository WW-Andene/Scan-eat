package fr.scanneat.presentation.result

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant

/**
 * Multi-destination "save to..." picker for a scanned product. Every
 * destination is independently checkable (a star, not a single radio) and the
 * dialog stays open across taps — only "Save" or dismissal closes it, so
 * picking several destinations doesn't take several separate re-opens.
 */
@Composable
fun SaveDestinationsPopup(
    alreadyFavorite: Boolean,
    onSave: (Set<SaveDestination>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember {
        mutableStateOf(if (alreadyFavorite) setOf(SaveDestination.FAVORIS) else emptySet())
    }

    fun toggle(d: SaveDestination) {
        selected = if (d in selected) selected - d else selected + d
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant,
        title = { Text(stringResource(R.string.save_destinations_title), color = OnBackground) },
        text = {
            Column {
                DestinationRow(SaveDestination.COURSES, stringResource(R.string.save_destinations_courses), selected) { toggle(it) }
                DestinationRow(SaveDestination.MES_ALIMENTS, stringResource(R.string.save_destinations_mes_aliments), selected) { toggle(it) }
                DestinationRow(SaveDestination.REPAS, stringResource(R.string.save_destinations_repas), selected) { toggle(it) }
                DestinationRow(SaveDestination.FAVORIS, stringResource(R.string.save_destinations_favoris), selected) { toggle(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected) }, enabled = selected.isNotEmpty()) {
                Text(stringResource(R.string.common_save), color = AccentCoral)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) }
        },
    )
}

@Composable
private fun DestinationRow(
    destination: SaveDestination,
    label: String,
    selected: Set<SaveDestination>,
    onToggle: (SaveDestination) -> Unit,
) {
    val checked = destination in selected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle(destination) })
            .padding(vertical = Spacing.XS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle(destination) }, colors = CheckboxDefaults.colors(checkedColor = Gold))
        Icon(if (checked) Icons.Default.Star else Icons.Default.StarBorder, null, tint = Gold, modifier = Modifier.padding(end = Spacing.XS))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OnBackground)
    }
}
