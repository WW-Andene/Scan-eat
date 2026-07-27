package fr.scanneat.presentation.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.*

@Composable
internal fun LanguageSection(language: String, onLanguageChange: (String) -> Unit) {
    SettingsSection(stringResource(R.string.settings_section_language)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            listOf("fr" to stringResource(R.string.settings_lang_fr), "en" to stringResource(R.string.settings_lang_en)).forEach { (code, label) ->
                FilterChip(
                    selected = language == code,
                    onClick  = { onLanguageChange(code) },
                    label    = { Text(label) },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                    ),
                )
            }
        }
    }
}
