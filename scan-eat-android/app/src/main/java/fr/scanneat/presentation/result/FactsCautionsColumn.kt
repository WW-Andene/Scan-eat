package fr.scanneat.presentation.result

import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.Bulb
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.semanticAmber

/**
 * Shared facts/cautions layout for scan-result dialogs that aren't full
 * food scoring — MedicationFound and NonConsumableFound in ScanScreen.kt
 * both use this instead of duplicating the section/scroll/divider
 * plumbing HintPanel already has.
 */
@Composable
fun FactsCautionsColumn(facts: List<String>, cautions: List<String>) {
    val amber = semanticAmber()
    val neutral = OnBackground.copy(0.7f)
    Column(modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
        if (cautions.isNotEmpty()) {
            HintSection(stringResource(R.string.hint_section_risks), cautions, amber, TablerIcons.AlertTriangle)
        }
        if (cautions.isNotEmpty() && facts.isNotEmpty()) {
            HorizontalDivider(color = OnBackground.copy(0.08f), modifier = Modifier.padding(vertical = Spacing.XS))
        }
        if (facts.isNotEmpty()) {
            HintSection(stringResource(R.string.hint_section_facts), facts, neutral, TablerIcons.Bulb)
        }
    }
}
