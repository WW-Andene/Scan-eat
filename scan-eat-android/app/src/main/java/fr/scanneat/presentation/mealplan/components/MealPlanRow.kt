package fr.scanneat.presentation.mealplan.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.planning.*
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun MealPlanRow(meal: String, slot: MealPlanSlot?, onEdit: (String) -> Unit, onClear: () -> Unit, onAssign: () -> Unit, onLog: (MealPlanSlot) -> Unit, warning: String? = null) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(slot) { mutableStateOf((slot as? MealPlanSlot.NoteSlot)?.text ?: "") }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Text(meal, style = MaterialTheme.typography.labelMedium, color = OnSurface.copy(0.6f), modifier = Modifier.width(72.dp))
        if (editing) {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                modifier = Modifier.weight(1f), singleLine = true,
                colors = scanEatTextFieldColors(),
            )
            // IconButtons left at their default 48dp touch target (Material/WCAG
            // minimum) below - a UI/UX audit found this row forcing every control
            // to 32dp. The inner Icon's own smaller size keeps the glyph compact.
            IconButton(onClick = { onEdit(text); editing = false }) {
                Icon(Icons.Default.Check, stringResource(R.string.common_ok), tint = AccentCoral, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { editing = false }) {
                Icon(Icons.Default.Close, stringResource(R.string.common_cancel), tint = OnSurface.copy(0.4f), modifier = Modifier.size(16.dp))
            }
        } else {
            val label = when (slot) {
                is MealPlanSlot.NoteSlot     -> slot.text
                is MealPlanSlot.RecipeSlot   -> stringResource(R.string.mealplan_recipe_prefix, slot.name)
                is MealPlanSlot.TemplateSlot -> stringResource(R.string.mealplan_template_prefix, slot.name)
                null -> stringResource(R.string.common_dash)
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when (slot) {
                    is MealPlanSlot.RecipeSlot   -> Icon(Icons.Default.RestaurantMenu, null, tint = OnSurface.copy(0.4f), modifier = Modifier.size(14.dp))
                    is MealPlanSlot.TemplateSlot -> Icon(Icons.AutoMirrored.Filled.ListAlt, null, tint = OnSurface.copy(0.4f), modifier = Modifier.size(14.dp))
                    else -> {}
                }
                Text(label, style = MaterialTheme.typography.bodySmall, color = if (slot != null) OnSurface else OnSurface.copy(0.3f))
                // Same checkUserAllergens()/checkDiet() warning Recipes/Templates/Grocery/
                // Diary already show for the exact same items - this weekly grid previously
                // showed zero allergen/diet warning no matter what was planned here.
                if (warning != null) {
                    Icon(Icons.Default.WarningAmber, contentDescription = warning, tint = semanticAmber(), modifier = Modifier.size(12.dp))
                }
            }
            // Editing as free text only makes sense for a note (or an empty slot) — a
            // Recipe/Template assignment has no text to edit, and the text field always
            // started blank for them, so confirming it used to silently wipe the
            // assignment. Recipe/Template slots use the clear (X) button to remove instead.
            if (slot == null || slot is MealPlanSlot.NoteSlot) {
                IconButton(onClick = { editing = true }) {
                    Icon(Icons.Default.Edit, stringResource(R.string.common_edit), tint = OnSurface.copy(0.4f), modifier = Modifier.size(16.dp))
                }
            }
            // A planned Recipe/Template slot previously only ever persisted the plan
            // itself — nothing connected it to the diary, so the day arrived and the
            // plan stayed purely decorative. Only meaningful once a real recipe/
            // template is assigned; a note has no nutrition to log.
            if (slot is MealPlanSlot.RecipeSlot || slot is MealPlanSlot.TemplateSlot) {
                IconButton(onClick = { onLog(slot) }) {
                    Icon(Icons.Default.Add, stringResource(R.string.common_log), tint = AccentCoral, modifier = Modifier.size(18.dp))
                }
            }
            // Lets a saved Recipe/Template actually be planned onto this slot — until
            // now MealPlanSlot.RecipeSlot/TemplateSlot could only ever be produced by
            // deserializing a backup, never by anything reachable from the UI.
            IconButton(onClick = onAssign) {
                Icon(Icons.Default.RestaurantMenu, stringResource(R.string.mealplan_assign_cd), tint = OnSurface.copy(0.4f), modifier = Modifier.size(16.dp))
            }
            if (slot != null) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, stringResource(R.string.common_clear), tint = OnSurface.copy(0.3f), modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
