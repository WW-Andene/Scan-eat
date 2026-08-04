package fr.scanneat.presentation.fasting.components

import compose.icons.TablerIcons
import compose.icons.tablericons.Trophy
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.data.repository.health.FastingState
import fr.scanneat.presentation.biolism.hmsFromSeconds
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun ActiveFastCard(fastingState: FastingState, language: String, personalRecord: Double, onCancel: () -> Unit, onStop: () -> Unit) {
    val fs = fastingState
    val pct = fs.progressFraction.coerceIn(0f, 1f)
    // art-direction-engine §ATMOSPHERE: HydrationRingAndControls' ring already gets a
    // radial-gradient glow backdrop behind it - this ring, the literal focal element
    // of an active fast in progress, had none and read as flatter than its sibling.
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
        Box(
            modifier = Modifier.size(140.dp)
                .background(Brush.radialGradient(listOf(AccentCoral.copy(alpha = 0.18f), Color.Transparent)), CircleShape),
        )
        CircularProgressIndicator(
            progress = { pct }, modifier = Modifier.size(120.dp),
            color = if (pct >= 1f) AccentCoral else AccentCoral.copy(0.6f),
            trackColor = SurfaceVariant, strokeWidth = 10.dp,
        )
    }
    val (h, m, _) = hmsFromSeconds(fs.elapsedMs / 1000)
    Text("${h}h ${m.toString().padStart(2, '0')}m", style = MaterialTheme.typography.headlineMedium, color = AccentCoral, fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.fasting_target_progress, fs.targetHours), style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))

    // Metabolic phase indicator (now bilingual)
    val phase = metabolicPhase(h.toInt())
    val nextPhase = METABOLIC_PHASES.firstOrNull { it.minHours > h }
    Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = AccentCoral.copy(0.1f)) {
        Column(modifier = Modifier.padding(horizontal = Spacing.M, vertical = Spacing.S), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.fasting_phase_label, phase.label(language)), style = MaterialTheme.typography.labelMedium, color = AccentCoral, fontWeight = FontWeight.Bold)
            Text(phase.description(language), style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.7f))
            if (nextPhase != null) {
                Text(stringResource(R.string.fasting_phase_next, nextPhase.label(language), nextPhase.minHours), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.4f))
            }
        }
    }
    // Personal-record chip — shown when the current fast already
    // beats the user's longest historically completed fast. Was ">=", so
    // merely tying a prior best also showed "new record" (hollow/inaccurate
    // for a user repeating the same fast length every week).
    if (personalRecord > 0 && h > personalRecord) {
        Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = Gold.copy(0.15f), border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(0.4f))) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.M, vertical = Spacing.XS),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
            ) {
                // Icon, not the 🏆 emoji baked into the string before.
                Icon(TablerIcons.Trophy, null, tint = Gold, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.fasting_new_record), style = MaterialTheme.typography.labelMedium, color = Gold, fontWeight = FontWeight.Bold)
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
        ScanEatOutlinedButton(onClick = onCancel) {
            Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.7f))
        }
        ScanEatPrimaryButton(onClick = onStop) {
            Text(stringResource(R.string.fasting_finish_button))
        }
    }
}

internal data class MetabolicPhase(val label: String, val description: String, val minHours: Int, val labelEn: String = label, val descriptionEn: String = description) {
    fun label(lang: String) = if (lang == "en") labelEn else label
    fun description(lang: String) = if (lang == "en") descriptionEn else description
}
internal val METABOLIC_PHASES = listOf(
    MetabolicPhase("Glycogène",    "L'organisme consomme ses réserves de glucose",      0,
        "Glycogen",    "Body is burning stored glucose reserves"),
    MetabolicPhase("Transition",   "Début de la gluconéogenèse, glycogène en baisse",   8,
        "Transition",  "Gluconeogenesis begins, glycogen falling"),
    MetabolicPhase("Combustion",   "Les graisses deviennent la source d'énergie principale", 12,
        "Fat Burn",    "Fat becomes the primary energy source"),
    MetabolicPhase("Cétose",       "Production de corps cétoniques, clarté mentale",    16,
        "Ketosis",     "Ketone bodies produced, mental clarity"),
    MetabolicPhase("Cétose prof.", "Cétose profonde, énergie stable et soutenue",       20,
        "Deep Ketosis","Deep ketosis, stable sustained energy"),
    MetabolicPhase("Autophagie",   "Recyclage cellulaire (autophagie) activé",          24,
        "Autophagy",   "Cellular recycling (autophagy) activated"),
)
internal fun metabolicPhase(elapsedHours: Int): MetabolicPhase =
    METABOLIC_PHASES.lastOrNull { elapsedHours >= it.minHours } ?: METABOLIC_PHASES.first()
