package fr.scanneat.presentation.biolism.data.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.domain.engine.biolism.BiolismProfile
import fr.scanneat.domain.engine.biolism.BiolismSession
import fr.scanneat.domain.engine.biolism.MetabolicResult
import fr.scanneat.domain.engine.biolism.label
import fr.scanneat.presentation.biolism.data.*
import fr.scanneat.presentation.ui.theme.*

@Composable
fun DailyGoalsCard(met: MetabolicResult, profile: BiolismProfile, sessions: List<BiolismSession>, lang: String = "fr") {
    BioCard(stringResource(R.string.biolism_goals_title)) {
        InfoRow(stringResource(R.string.biolism_goals_bmr_label), stringResource(R.string.biolism_goals_kcal_per_day, met.bmrDay), stringResource(R.string.biolism_goals_bmr_note), TextSecondary)
        // Previously the bare English activityMeta.label regardless of app language.
        InfoRow(stringResource(R.string.biolism_goals_tdee_label), stringResource(R.string.biolism_goals_kcal_per_day, met.tdeeDay), profile.activityMeta.label(lang), Gold)
        InfoRow(stringResource(R.string.biolism_goals_sessions_label), "${sessions.count { isToday(it.timestamp) }}", stringResource(R.string.biolism_goals_sessions_note), Teal)
    }
}
