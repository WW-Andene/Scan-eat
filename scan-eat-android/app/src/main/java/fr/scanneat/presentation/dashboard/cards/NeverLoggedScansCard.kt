package fr.scanneat.presentation.dashboard.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import fr.scanneat.R
import fr.scanneat.domain.model.ScanResult
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.CardRadius
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.ScanEatCard

/**
 * DashboardViewModel already injected both ConsumptionRepository and
 * ScanRepository but never cross-referenced them - the app's core loop
 * (scan -> decide -> log) had no follow-through nudge, so a user who scans
 * several products at the store and only logs some of them got no dashboard
 * signal that the rest were never actually recorded to their diary.
 * [onLogClick] opens the same LogSheet portion/meal-slot picker every other
 * log action in the app uses (see DashboardViewModel.logNeverLoggedScan).
 */
@Composable
internal fun NeverLoggedScansCard(scans: List<ScanResult>, onLogClick: (ScanResult) -> Unit) {
    if (scans.isEmpty()) return
    ScanEatCard(
        color = SurfaceVariant,
        contentPadding = PaddingValues(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Text(
            stringResource(R.string.dashboard_never_logged_title),
            style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold,
        )
        scans.take(3).forEach { scan ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    scan.product.name, style = MaterialTheme.typography.bodyMedium, color = OnSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = RoundedCornerShape(CardRadius.CONTROL), color = AccentCoral.copy(0.15f),
                    modifier = Modifier.clickable { onLogClick(scan) },
                ) {
                    Text(
                        stringResource(R.string.dashboard_never_logged_action),
                        style = MaterialTheme.typography.labelSmall, color = AccentCoral, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(PaddingValues(horizontal = Spacing.S, vertical = Spacing.XS)),
                    )
                }
            }
        }
    }
}
