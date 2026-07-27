package fr.scanneat.presentation.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing

@Composable
internal fun LegalSection() {
    SettingsSection(stringResource(R.string.settings_section_legal)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(stringResource(R.string.settings_legal_medical_disclaimer), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
            Text(stringResource(R.string.settings_legal_data_accuracy), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
            Text(stringResource(R.string.settings_legal_nutrition_recs), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
            Text(stringResource(R.string.settings_legal_medication), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
            Text(stringResource(R.string.settings_legal_liability), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
            Text(stringResource(R.string.settings_legal_privacy), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
        }
    }
}
