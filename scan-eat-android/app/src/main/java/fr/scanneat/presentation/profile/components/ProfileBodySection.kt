package fr.scanneat.presentation.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.domain.model.Sex
import fr.scanneat.presentation.biolism.bioProfile.BioInputUnit
import fr.scanneat.presentation.ui.theme.*

/**
 * ProfileScreen's "Body" section — height/weight/goal-weight (metric/imperial
 * toggle) plus the sex-conditional menstruating checkbox. Extracted verbatim
 * (§T1 composition-root split); same app-wide metric/imperial preference as
 * the Weight tab.
 */
@Composable
internal fun ProfileBodySection(
    heightCm: String,
    onHeightCmChange: (String) -> Unit,
    weightKg: String,
    onWeightKgChange: (String) -> Unit,
    goalWeightKg: String,
    onGoalWeightKgChange: (String) -> Unit,
    useImperial: Boolean,
    onUseImperialChange: (Boolean) -> Unit,
    sex: Sex,
    isMenstruating: Boolean,
    onIsMenstruatingChange: (Boolean) -> Unit,
) {
    ProfileSection(stringResource(R.string.profile_section_body)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                listOf(false to stringResource(R.string.bioprofile_unit_metric), true to stringResource(R.string.bioprofile_unit_imperial)).forEach { (imperial, label) ->
                    FilterChip(
                        selected = useImperial == imperial,
                        onClick = { onUseImperialChange(imperial) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral),
                    )
                }
            }
        }
        BioInputUnit(
            stringResource(R.string.profile_field_height), stringResource(R.string.profile_field_height_imperial),
            heightCm, useImperial, { it / CM_TO_IN }, { it * CM_TO_IN },
            onHeightCmChange,
        )
        BioInputUnit(
            stringResource(R.string.profile_field_weight), stringResource(R.string.profile_field_weight_imperial),
            weightKg, useImperial, { it * KG_TO_LB }, { it / KG_TO_LB },
            onWeightKgChange,
        )
        BioInputUnit(
            stringResource(R.string.profile_field_goal_weight), stringResource(R.string.profile_field_goal_weight_imperial),
            goalWeightKg, useImperial, { it * KG_TO_LB }, { it / KG_TO_LB },
            onGoalWeightKgChange,
        )
        if (sex == Sex.FEMALE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = isMenstruating,
                    onCheckedChange = onIsMenstruatingChange,
                    colors = CheckboxDefaults.colors(checkedColor = AccentCoral),
                )
                Text(stringResource(R.string.profile_menstruating_checkbox), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.8f))
            }
        }
    }
}
