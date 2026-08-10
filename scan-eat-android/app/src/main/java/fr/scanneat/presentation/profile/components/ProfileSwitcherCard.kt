package fr.scanneat.presentation.profile.components

import compose.icons.TablerIcons
import compose.icons.tablericons.Plus
import compose.icons.tablericons.X
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.model.Profile
import fr.scanneat.presentation.ui.theme.*

/**
 * R&D audit finding: profileId was threaded through every tracker repository
 * (Diary/Weight/Activity/...) but nothing in the app ever created or switched
 * a second one - pure dead scaffolding, confirmed by a zero-hit grep for
 * switchProfile/createProfile app-wide before this. Real create/switch/
 * rename/delete now backed by UserPreferences' per-id storage (see its own
 * doc comment on the Profile section).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileSwitcherCard(
    profiles: List<Profile>,
    activeId: String,
    onSwitch: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    ProfileSection(stringResource(R.string.profile_switcher_title)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            profiles.forEach { p ->
                val selected = p.id == activeId
                val label = p.name.ifBlank { stringResource(R.string.profile_switcher_unnamed) }
                FilterChip(
                    selected = selected,
                    onClick = { if (!selected) onSwitch(p.id) },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                    // FilterChip's trailingIcon slot is decorative, not its own tap
                    // target - "default" can't be deleted (UserPreferences.
                    // deleteProfile's own doc comment), so only non-default chips
                    // get a delete affordance, wrapped in its own clickable so
                    // tapping it deletes without also triggering the chip's own
                    // onSwitch.
                    trailingIcon = if (p.id != "default") {
                        {
                            Icon(
                                TablerIcons.X, stringResource(R.string.common_delete),
                                modifier = Modifier.size(IconSize.Tiny).clickable { onDelete(p.id) },
                                tint = OnBackground.copy(0.5f),
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                        labelColor = OnBackground.copy(0.7f),
                    ),
                )
            }
            FilterChip(
                selected = false,
                onClick = { showCreate = true },
                label = { Icon(TablerIcons.Plus, stringResource(R.string.profile_switcher_add), modifier = Modifier.size(IconSize.Tiny)) },
                colors = FilterChipDefaults.filterChipColors(labelColor = OnBackground.copy(0.7f)),
            )
        }
    }
    if (showCreate) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            containerColor = SurfaceVariant.copy(alpha = StandardCardAlpha),
            modifier = Modifier.glassPopupSurface(RoundedCornerShape(CardRadius.PROMINENT)),
            shape = RoundedCornerShape(CardRadius.PROMINENT),
            title = { Text(stringResource(R.string.profile_switcher_add_title), color = OnBackground) },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text(stringResource(R.string.profile_field_name)) }, singleLine = true,
                    colors = scanEatTextFieldColors(),
                )
            },
            confirmButton = {
                TextButton(onClick = { onCreate(name.trim()); name = ""; showCreate = false }, enabled = name.isNotBlank()) {
                    Text(stringResource(R.string.common_save), color = if (name.isNotBlank()) AccentCoral else OnBackground.copy(0.3f))
                }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
        )
    }
}
