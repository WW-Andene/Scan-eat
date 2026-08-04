package fr.scanneat.presentation.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

/**
 * Freemium status + upgrade entry point - everything in the app is free except
 * Biolism (metabolism tracking) and AI-powered photo/label scanning (see
 * UserPreferences.isPremium's own doc comment for why those two specifically).
 *
 * No payment processor is wired up yet - onUpgrade() is a temporary manual
 * toggle standing in for a real Google Play Billing purchase flow, which needs
 * Play Console product configuration before it can be built. Every gated
 * screen reads the isPremium flag itself, not this toggle, so replacing this
 * call site with a real purchase flow later requires no change to them.
 */
@Composable
internal fun PremiumSection(isPremium: Boolean, onSetPremium: (Boolean) -> Unit) {
    SettingsSection(stringResource(R.string.settings_section_premium), icon = Icons.Default.WorkspacePremium) {
        if (isPremium) {
            Text(
                stringResource(R.string.settings_premium_active),
                style = MaterialTheme.typography.bodyMedium, color = AccentCoral, fontWeight = FontWeight.SemiBold,
            )
        } else {
            Text(
                stringResource(R.string.settings_premium_pitch),
                style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f),
            )
        }
        // User-reported: this toggle button sat inline next to its hint label
        // (SpaceBetween Row) instead of full width, unlike Journal's "Activer
        // les notifications" (NotificationPermission.kt), which stacks its hint
        // above a fillMaxWidth button. Matched here.
        Text(
            if (isPremium) stringResource(R.string.settings_premium_downgrade_hint) else stringResource(R.string.settings_premium_upgrade_hint),
            style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f),
        )
        ScanEatPrimaryButton(onClick = { onSetPremium(!isPremium) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (isPremium) stringResource(R.string.settings_premium_disable_button) else stringResource(R.string.settings_premium_enable_button))
        }
    }
}
