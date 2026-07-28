package fr.scanneat.presentation.settings.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun ProfileSection(onOpenProfile: () -> Unit) {
    SettingsSection(stringResource(R.string.settings_section_profile), icon = Icons.Default.Person) {
        Text(stringResource(R.string.settings_profile_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
        ScanEatOutlinedButton(
            onClick = onOpenProfile,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Person, null, tint = OnBackground, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.S))
            Text(stringResource(R.string.settings_profile_button), color = OnBackground)
        }
    }
}
