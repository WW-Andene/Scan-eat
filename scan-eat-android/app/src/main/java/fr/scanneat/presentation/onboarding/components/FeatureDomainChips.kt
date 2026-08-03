package fr.scanneat.presentation.onboarding.components

import compose.icons.TablerIcons
import compose.icons.tablericons.Clock
import compose.icons.tablericons.Droplet
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun FeatureDomainChips() {
    val domains = listOf(
        Icons.Rounded.QrCodeScanner to R.string.onboarding_domain_scan,
        Icons.Rounded.RestaurantMenu to R.string.onboarding_domain_diet,
        TablerIcons.Droplet to R.string.onboarding_domain_hydration,
        TablerIcons.Clock to R.string.onboarding_domain_fasting,
        Icons.Rounded.Scale to R.string.onboarding_domain_weight,
        Icons.Rounded.DirectionsRun to R.string.onboarding_domain_activity,
        Icons.Rounded.Medication to R.string.onboarding_domain_medication,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(
            stringResource(R.string.onboarding_domains_title),
            style = MaterialTheme.typography.labelSmall,
            color = OnBackground.copy(0.5f),
        )
        domains.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                row.forEach { (icon, label) ->
                    Surface(shape = RoundedCornerShape(50), color = SurfaceVariant.copy(alpha = 0.42f), shadowElevation = 3.dp) {
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(icon, null, tint = AccentCoral, modifier = Modifier.size(14.dp))
                            Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.8f))
                        }
                    }
                }
            }
        }
    }
}
