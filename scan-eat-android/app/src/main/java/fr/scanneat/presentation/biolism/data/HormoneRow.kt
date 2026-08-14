package fr.scanneat.presentation.biolism.data

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.domain.engine.biolism.HormoneReading
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.STATUS_BORDER_ALPHA
import fr.scanneat.presentation.ui.theme.Spacing
import java.util.Locale

@Composable
internal fun HormoneRow(name: String, h: HormoneReading, note: String) {
    val color = colorFromToken(h.colorToken)
    val barPct = (h.value / (h.refHigh * 1.3)).coerceIn(0.0, 1.0).toFloat()
    // Spacing.XS, matching InfoRow's own vertical padding - this and InfoRow render
    // the same "label/value row in a list" shape in the same cards (e.g.
    // HormonesCard), and previously used a different literal padding (6.dp vs
    // InfoRow's Spacing.XS/4dp) for no reason, giving the two row types a visibly
    // different rhythm next to each other.
    Column(Modifier.padding(vertical = Spacing.XS)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text(name, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.7f), fontWeight = FontWeight.Medium)
                // Bumped from 0.3f - a UI/UX audit flagged this as real informational
                // content (not decorative) rendered too faint against the dark surface.
                Text(note, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.45f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                // A formula-derived estimate (see HormoneEstimator), not a lab
                // measurement - one-decimal precision here (unlike the whole-number
                // reference range just below) implied a level of accuracy this
                // doesn't have. Whole numbers, matching the ref range's own format.
                Text("%.0f ${h.unit}".format(Locale.US, h.value), style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Bold)
                Surface(shape = RoundedCornerShape(2.dp), color = color.copy(0.15f),
                    border = BorderStroke(2.dp, color.copy(alpha = STATUS_BORDER_ALPHA))) {
                    Text(h.label, modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.T2),
                        style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        // design-aesthetic-audit §DC3: the reference band below was a hardcoded
        // Color.White at low alpha - readable on the dark/OLED themes it was
        // eyeballed against, but nearly invisible on the Light theme's near-white
        // background (0xFFF6F1EC). OnBackground already flips between near-white
        // and near-black per theme; captured here since DrawScope (inside Canvas
        // below) isn't itself a @Composable context and can't read it directly.
        val referenceBandColor = OnBackground
        // Fix 12: Canvas draws track, normal-range band, and value bar correctly
        Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
            val w   = size.width
            val h3  = size.height
            val top = 1.5f    // half-height — used for RoundedCornerShape approximation via cornerRadius
            val scale = h.refHigh * 1.3
            val loFrac = (h.refLow  / scale).toFloat().coerceIn(0f, 1f)
            val hiFrac = (h.refHigh / scale).toFloat().coerceIn(0f, 1f)
            val valFrac = barPct.coerceIn(0f, 1f)
            // Track
            drawRoundRect(color.copy(alpha = 0.06f), cornerRadius = CornerRadius(top))
            // Normal reference band
            drawRect(
                color = referenceBandColor.copy(alpha = 0.12f),
                topLeft = Offset(w * loFrac, 0f),
                size    = Size(w * (hiFrac - loFrac), h3),
            )
            // Value bar
            if (valFrac > 0f) {
                drawRoundRect(
                    color       = color.copy(alpha = 0.75f),
                    size        = Size(w * valFrac, h3),
                    cornerRadius = CornerRadius(top),
                )
            }
        }
        // Bumped from 0.25f - a UI/UX audit flagged a clinically meaningful reference
        // range rendered at the lowest alpha found anywhere in the app.
        Text(stringResource(R.string.biolism_common_ref_range, h.refLow, h.refHigh, h.unit), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
    }
}
