package fr.scanneat.presentation.onboarding.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun ColumnScope.ValuePropositionPage(onNext: () -> Unit) {
    Text(stringResource(R.string.onboarding_value_title), style = MaterialTheme.typography.headlineSmall, color = OnBackground, fontWeight = FontWeight.Bold)

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
        ValueCard(
            icon    = Icons.Default.Fingerprint,
            title   = stringResource(R.string.onboarding_value_transparency_title),
            body    = stringResource(R.string.onboarding_value_transparency_body),
        )
        ValueCard(
            icon    = Icons.Default.MonitorHeart,
            title   = stringResource(R.string.onboarding_value_biolism_title),
            body    = stringResource(R.string.onboarding_value_biolism_body),
        )
    }

    // New: feature-domain preview — previously page 1 only showed 2 abstract
    // value cards with no concrete preview of what the app actually tracks;
    // users arrived at profile setup with no idea what domains were covered.
    FeatureDomainChips()

    Spacer(Modifier.weight(1f))
    ScanEatPrimaryButton(
        onClick = onNext,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.onboarding_continue_button), style = MaterialTheme.typography.titleMedium) }
}
