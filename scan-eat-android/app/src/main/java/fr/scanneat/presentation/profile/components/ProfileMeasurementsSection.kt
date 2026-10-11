package fr.scanneat.presentation.profile.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import fr.scanneat.R
import fr.scanneat.domain.engine.biolism.ETHNICITY_OPTIONS
import fr.scanneat.presentation.biolism.bioProfile.BioInputUnit
import fr.scanneat.presentation.ui.theme.*

/**
 * ProfileScreen's "Body measurements" section — waist/hip/neck + ethnicity,
 * shared with Métabolisme > Mon Profil (BiolismProfileScreen). Extracted
 * verbatim (§T1 composition-root split); surfaced here too so a user who
 * never opens Métabolisme still benefits from Navy BF%/WHtR calculations
 * that need these.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileMeasurementsSection(
    waistCm: String,
    onWaistCmChange: (String) -> Unit,
    hipCm: String,
    onHipCmChange: (String) -> Unit,
    neckCm: String,
    onNeckCmChange: (String) -> Unit,
    useImperial: Boolean,
    ethnicityId: String,
    onEthnicityIdChange: (String) -> Unit,
) {
    ProfileSection(stringResource(R.string.profile_section_measurements)) {
        BioInputUnit(
            stringResource(R.string.profile_field_waist), stringResource(R.string.profile_field_waist_imperial),
            waistCm, useImperial, { it / CM_TO_IN }, { it * CM_TO_IN },
            onWaistCmChange,
        )
        BioInputUnit(
            stringResource(R.string.profile_field_hip), stringResource(R.string.profile_field_hip_imperial),
            hipCm, useImperial, { it / CM_TO_IN }, { it * CM_TO_IN },
            onHipCmChange,
        )
        BioInputUnit(
            stringResource(R.string.profile_field_neck), stringResource(R.string.profile_field_neck_imperial),
            neckCm, useImperial, { it / CM_TO_IN }, { it * CM_TO_IN },
            onNeckCmChange,
        )
        Text(stringResource(R.string.profile_field_ethnicity), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
        val isFrench = Locale.current.language == "fr"
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            ETHNICITY_OPTIONS.forEach { opt ->
                FilterChip(
                    selected = ethnicityId == opt.id,
                    onClick  = { onEthnicityIdChange(opt.id) },
                    label    = { Text(if (isFrench) opt.labelFr else opt.label, maxLines = 1) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                    ),
                )
            }
        }
    }
}
