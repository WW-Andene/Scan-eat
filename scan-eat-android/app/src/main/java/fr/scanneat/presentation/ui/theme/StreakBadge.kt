package fr.scanneat.presentation.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.scanneat.R

/**
 * The "N-day streak" pill, previously hand-rolled independently in Weight/
 * Activity/Medication/Hydration with 4 different accent colors and slightly
 * different padding - same concept, no shared component, so a future style
 * tweak had to be applied 4 times and had already drifted (Weight's own
 * padding/spacing differed slightly from the other three). [accentColor] is
 * each tracker's own established color (e.g. Gold for Weight, Teal for
 * Medication) - this only unifies the shape, not the per-tracker meaning.
 */
@Composable
fun StreakBadge(streakDays: Int, accentColor: Color) {
    Surface(shape = RoundedCornerShape(50), color = accentColor.copy(0.15f)) {
        Row(
            modifier = androidx.compose.ui.Modifier.padding(horizontal = Spacing.M, vertical = Spacing.XS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
        ) {
            // User-supplied doodle icon set (Vecteezy, Free License -
            // attribution given in Settings > About > Licenses, see
            // AboutSection.kt) swapped in for the Notebook theme - a flat
            // Material glyph doesn't match the hand-drawn identity every
            // other Notebook-themed element (post-it cards, crayon bars,
            // lens icon) already carries. Icon()'s default tint blend mode
            // (SrcIn) recolors the whole non-transparent doodle to
            // accentColor regardless of the source PNG's own black ink, so
            // this needs no separate light/dark asset.
            if (LocalThemeName.current == "notebook") {
                Icon(painterResource(R.drawable.doodle_flame), null, tint = accentColor, modifier = androidx.compose.ui.Modifier.size(IconSize.Small))
            } else {
                Icon(Icons.Default.LocalFireDepartment, null, tint = accentColor, modifier = androidx.compose.ui.Modifier.size(IconSize.Small))
            }
            Text(
                stringResource(R.string.common_streak_days_compact, streakDays),
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
