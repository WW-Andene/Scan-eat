package fr.scanneat.presentation.biolism.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.presentation.biolism.hmsFromSeconds
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.IconInactive
import fr.scanneat.presentation.ui.theme.Teal
import fr.scanneat.presentation.ui.theme.TextSecondary
import fr.scanneat.presentation.ui.theme.Violet
import fr.scanneat.presentation.ui.theme.Warm
import fr.scanneat.presentation.ui.theme.semanticRed
import java.util.Locale

// Danger/Severe genuinely mean "bad" (organ strain, prolonged-ketosis risk, etc.) —
// routed through the colorblind-safe semantic palette instead of the fixed red-family
// literals, which previously stayed identical across every colorblind mode despite
// being exactly the kind of red/orange hue those modes exist to disambiguate.
@Composable
internal fun colorFromToken(token: String): Color = when (token) {
    "Gold"        -> Gold
    "Teal"        -> Teal
    "Violet"      -> Violet
    "Warm"        -> Warm
    "Danger"      -> semanticRed()
    "Severe"      -> semanticRed()
    "IconInactive"-> IconInactive
    else          -> TextSecondary
}

// OrganPct.name (computeOrganPcts) is a stable English key also used to look
// up eliaBase deltas in OrganHeatCard - it can't be localized at the source
// without breaking that lookup, so the raw literal was rendered directly with
// no stringResource() at all, unlike every other label in this card. Same
// "token -> localized label" indirection as colorFromToken above.
@Composable
internal fun organLabel(name: String): String = when (name) {
    "Liver"           -> stringResource(R.string.biolism_organ_liver)
    "Skeletal Muscle" -> stringResource(R.string.biolism_organ_muscle)
    "Brain"           -> stringResource(R.string.biolism_organ_brain)
    "Residual"        -> stringResource(R.string.biolism_organ_residual)
    "Kidneys"         -> stringResource(R.string.biolism_organ_kidneys)
    "Heart"           -> stringResource(R.string.biolism_organ_heart)
    else              -> name
}

internal fun formatDuration(ms: Long): String {
    val (h, m, sec) = hmsFromSeconds(ms / 1000)
    return if (h > 0) "%dh %02dm".format(Locale.US, h, m) else "%dm %02ds".format(Locale.US, m, sec)
}

internal fun isToday(iso: String): Boolean {
    return try { iso.startsWith(java.time.LocalDate.now().toString()) } catch (e: Exception) { false }
}
