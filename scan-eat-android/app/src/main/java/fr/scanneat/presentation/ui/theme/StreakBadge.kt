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
            Icon(Icons.Default.LocalFireDepartment, null, tint = accentColor, modifier = androidx.compose.ui.Modifier.size(16.dp))
            Text(
                stringResource(R.string.common_streak_days_compact, streakDays),
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
