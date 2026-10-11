package fr.scanneat.presentation.settings.components

import compose.icons.TablerIcons
import compose.icons.tablericons.ChevronDown
import compose.icons.tablericons.ChevronUp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.IconSize
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing

/**
 * User-reported: 6 dense paragraphs back-to-back with no visual separation
 * (only the outer SettingsSection's own spacedBy(Spacing.S) between them)
 * read as a wall of text. Rebuilt as collapsible rows, one per topic —
 * mirrors PillarsSection's own expand/collapse row pattern (result/cards/
 * PillarsSection.kt) rather than inventing a new one.
 */
@Composable
internal fun LegalSection() {
    SettingsSection(stringResource(R.string.settings_section_legal), icon = Icons.Default.Gavel) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            LegalItemRow(stringResource(R.string.settings_legal_title_medical_disclaimer), stringResource(R.string.settings_legal_medical_disclaimer))
            LegalItemRow(stringResource(R.string.settings_legal_title_data_accuracy), stringResource(R.string.settings_legal_data_accuracy))
            LegalItemRow(stringResource(R.string.settings_legal_title_nutrition_recs), stringResource(R.string.settings_legal_nutrition_recs))
            LegalItemRow(stringResource(R.string.settings_legal_title_medication), stringResource(R.string.settings_legal_medication))
            LegalItemRow(stringResource(R.string.settings_legal_title_liability), stringResource(R.string.settings_legal_liability))
            LegalItemRow(stringResource(R.string.settings_legal_title_privacy), stringResource(R.string.settings_legal_privacy))
        }
    }
}

@Composable
private fun LegalItemRow(title: String, body: String) {
    var expanded by remember { mutableStateOf(false) }
    val expandedStateDescription = stringResource(if (expanded) R.string.common_expanded else R.string.common_collapsed)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { expanded = !expanded }
                .semantics(mergeDescendants = true) {
                    stateDescription = expandedStateDescription
                    role = Role.Button
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.Medium)
            Icon(
                if (expanded) TablerIcons.ChevronUp else TablerIcons.ChevronDown, null,
                tint = OnBackground.copy(0.5f), modifier = Modifier.size(IconSize.Inline),
            )
        }
        if (expanded) {
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = OnBackground.copy(0.6f),
                modifier = Modifier.padding(top = Spacing.XS),
            )
        }
    }
}
