package fr.scanneat.presentation.biolism.evolution.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.domain.engine.biolism.BiolismSession
import fr.scanneat.presentation.biolism.data.BioCard
import fr.scanneat.presentation.biolism.data.InfoRow
import fr.scanneat.presentation.biolism.data.formatDuration
import fr.scanneat.presentation.biolism.evolution.BarSparkline
import fr.scanneat.presentation.biolism.evolution.NotEnoughDataNote
import fr.scanneat.presentation.ui.theme.Violet

/**
 * ketoElapsedSec (added alongside this tab) is only populated on sessions
 * saved from now on — older sessions in the capped last-20 history default to
 * 0.0 and are excluded here rather than approximated from the whole session's
 * elapsedSec, which would overstate a partial-session keto toggle.
 */
@Composable
fun KetosisEvolutionCard(sessions: List<BiolismSession>) {
    BioCard(stringResource(R.string.biolism_evo_ketosis_title), defaultOpen = false) {
        val qualifying = sessions.filter { it.ketosis && it.ketoElapsedSec > 0 }.sortedBy { it.timestamp }
        if (qualifying.isEmpty()) {
            NotEnoughDataNote()
            return@BioCard
        }
        val avgMs = (qualifying.map { it.ketoElapsedSec }.average() * 1000).toLong()
        InfoRow(
            stringResource(R.string.biolism_evo_ketosis_avg),
            formatDuration(avgMs),
            stringResource(R.string.biolism_evo_ketosis_sessions, qualifying.size),
            Violet,
        )
        BarSparkline(values = qualifying.map { it.ketoElapsedSec / 3600.0 }, color = Violet)
    }
}
