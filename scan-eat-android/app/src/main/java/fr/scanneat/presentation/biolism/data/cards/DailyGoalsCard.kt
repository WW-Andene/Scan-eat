package fr.scanneat.presentation.biolism.data.cards

import androidx.compose.runtime.Composable
import fr.scanneat.domain.engine.biolism.BiolismProfile
import fr.scanneat.domain.engine.biolism.BiolismSession
import fr.scanneat.domain.engine.biolism.MetabolicResult
import fr.scanneat.presentation.biolism.data.*
import fr.scanneat.presentation.ui.theme.*

@Composable
fun DailyGoalsCard(met: MetabolicResult, profile: BiolismProfile, sessions: List<BiolismSession>) {
    BioCard("Objectifs quotidiens") {
        InfoRow("BMR (au repos)", "%.1f kcal/j".format(met.bmrDay), "besoin métabolique de base", TextSecondary)
        InfoRow("TDEE (actif)", "%.1f kcal/j".format(met.tdeeDay), profile.activityMeta.label, Gold)
        InfoRow("Sessions aujourd'hui", "${sessions.count { isToday(it.timestamp) }}", "enregistrées aujourd'hui", Teal)
    }
}
