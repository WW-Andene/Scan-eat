package fr.scanneat.presentation.biolism.bioProfile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import fr.scanneat.R
import fr.scanneat.domain.engine.biolism.*
import fr.scanneat.presentation.biolism.data.TealBadge
import fr.scanneat.presentation.ui.theme.*

/**
 * Read-only recap of the saved BiolismProfile + completeness bar, shown above
 * the editable form. Extracted from BiolismProfileScreen (§T1 composition-root
 * split).
 */
@Composable
internal fun BiolismProfileOverviewCard(
    profile: BiolismProfile,
    language: String,
    completeness: Float,
    dispWeight: (Double) -> String,
    dispHeight: (Double) -> String,
    dispCirc: (Double) -> String,
) {
    val p = profile
    ScanEatCard(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.bioprofile_overview_title), style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
            TealBadge(stringResource(R.string.bioprofile_overview_saved_badge))
        }
        val activityLabel = ACTIVITY_LEVELS.firstOrNull { it.id == p.activityId }?.label(language) ?: "—"
        val ethnicityLabel = ETHNICITY_OPTIONS.firstOrNull { it.id == p.ethnicityId }?.label(language) ?: "—"
        OverviewRow(stringResource(R.string.profile_field_age), if (p.ageYears > 0) "${p.ageYears}" else null)
        OverviewRow(stringResource(R.string.bioprofile_field_weight), if (p.weightKg > 0) dispWeight(p.weightKg) else null)
        OverviewRow(stringResource(R.string.profile_field_height), if (p.heightCm > 0) dispHeight(p.heightCm) else null)
        OverviewRow(stringResource(R.string.bioprofile_section_activity), activityLabel)
        OverviewRow(stringResource(R.string.bioprofile_field_waist), if (p.waistCm > 0) dispCirc(p.waistCm) else null)
        OverviewRow(stringResource(R.string.bioprofile_field_hip), if (p.hipCm > 0) dispCirc(p.hipCm) else null)
        OverviewRow(stringResource(R.string.bioprofile_field_neck), if (p.neckCm > 0) dispCirc(p.neckCm) else null)
        OverviewRow(stringResource(R.string.bioprofile_section_ethnicity), ethnicityLabel)
        if (p.sex == BiolismSex.FEMALE) {
            OverviewRow(stringResource(R.string.bioprofile_field_cycle_day), "${p.cycleDay} / 28")
        }
        ScanEatDivider(color = SeparatorExtraLight)
        val pct = (completeness * 100).toInt()
        Text(stringResource(R.string.bioprofile_completeness_label, pct), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
        LinearProgressIndicator(
            progress = { completeness },
            modifier = Modifier.fillMaxWidth(),
            color = if (completeness >= 1f) semanticGreen() else Gold,
            trackColor = OnBackground.copy(0.1f),
        )
    }
}
