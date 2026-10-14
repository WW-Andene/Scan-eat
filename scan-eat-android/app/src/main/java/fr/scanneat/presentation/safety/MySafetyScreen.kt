package fr.scanneat.presentation.safety

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import fr.scanneat.R
import fr.scanneat.domain.engine.health.FastingRisk
import fr.scanneat.domain.model.AllergenSeverity
import fr.scanneat.domain.model.Goal
import fr.scanneat.domain.model.severityOf
import fr.scanneat.domain.model.weightLossUnsafe
import fr.scanneat.presentation.medication.components.interactionWarningLabel
import fr.scanneat.presentation.profile.components.allergenLabels
import fr.scanneat.presentation.profile.components.conditionLabels
import fr.scanneat.presentation.ui.theme.*

/**
 * Roadmap D9 "Ma sécurité": allergens and their severity (N6), health
 * conditions, active treatments and their interactions, recalled products
 * (D1), and profile-based contraindications (C13 fasting, C14 weight loss),
 * with links to act on each and to the data sources.
 */
@Composable
fun MySafetyScreen(
    viewModel: MySafetyViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenRecalls: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenReactions: () -> Unit = {},
    onOpenMenuScan: () -> Unit = {},
) {
    val s = viewModel.state.collectAsStateWithLifecycle().value
    val allergenNames = allergenLabels()
    val conditionNames = conditionLabels()

    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.my_safety_title), color = OnBackground) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) } },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold).padding(horizontal = Spacing.L),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            item { Spacer(Modifier.height(Spacing.L)) }

            item {
                SafetySection(stringResource(R.string.my_safety_allergens)) {
                    if (s.profile.allergens.isEmpty()) Line(stringResource(R.string.my_safety_none_declared))
                    s.profile.allergens.sorted().forEach { key ->
                        val sev = stringResource(when (s.profile.severityOf(key)) {
                            AllergenSeverity.SEVERE -> R.string.allergen_severity_severe
                            AllergenSeverity.ALLERGY -> R.string.allergen_severity_allergy
                            AllergenSeverity.INTOLERANCE -> R.string.allergen_severity_intolerance
                        })
                        Line("${allergenNames[key] ?: key} · $sev")
                    }
                    TextButton(onClick = onOpenProfile) { Text(stringResource(R.string.my_safety_edit_profile)) }
                    TextButton(onClick = onOpenReactions) { Text(stringResource(R.string.my_safety_reactions)) }
                    TextButton(onClick = onOpenMenuScan) { Text(stringResource(R.string.menu_scan_title)) }
                }
            }

            item {
                SafetySection(stringResource(R.string.my_safety_conditions)) {
                    if (s.profile.healthConditions.isEmpty()) Line(stringResource(R.string.my_safety_none_declared))
                    s.profile.healthConditions.sorted().forEach { Line(conditionNames[it] ?: it) }
                    s.fasting.risks.takeIf { it.isNotEmpty() }?.let { risks ->
                        Line(stringResource(if (s.fasting.blocked) R.string.fasting_safety_title_blocked else R.string.fasting_safety_title_warning), emphasis = true)
                        risks.forEach { r ->
                            Line(stringResource(when (r) {
                                FastingRisk.PREGNANCY -> R.string.fasting_risk_pregnancy
                                FastingRisk.MINOR -> R.string.fasting_risk_minor
                                FastingRisk.DIABETES -> R.string.fasting_risk_diabetes
                                FastingRisk.UNDERWEIGHT -> R.string.fasting_risk_underweight
                            }))
                        }
                    }
                    if (s.profile.goal == Goal.LOSE && s.profile.weightLossUnsafe) Line(stringResource(R.string.profile_goal_lose_unsafe), emphasis = true)
                }
            }

            item {
                SafetySection(stringResource(R.string.my_safety_treatments)) {
                    if (s.activeMedications.isEmpty()) Line(stringResource(R.string.my_safety_no_treatment))
                    else Line(s.activeMedications.joinToString(", "))
                    if (s.activeMedications.size >= 2 && s.interactions.isEmpty()) Line(stringResource(R.string.my_safety_no_interaction))
                    s.interactions.forEach { Line(interactionWarningLabel(it), emphasis = true) }
                }
            }

            item {
                SafetySection(stringResource(R.string.recalls_title)) {
                    Line(if (s.recalledCount == 0) stringResource(R.string.recalls_empty) else pluralStringResource(R.plurals.my_safety_recalled_count, s.recalledCount, s.recalledCount), emphasis = s.recalledCount > 0)
                    TextButton(onClick = onOpenRecalls) { Text(stringResource(R.string.my_safety_open_recalls)) }
                }
            }

            item { TextButton(onClick = onOpenSources) { Text(stringResource(R.string.settings_sources)) } }
            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }
}

@Composable
private fun SafetySection(title: String, content: @Composable ColumnScope.() -> Unit) {
    ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = OnBackground)
        content()
    }
}

@Composable
private fun Line(text: String, emphasis: Boolean = false) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = if (emphasis) semanticAmber() else OnBackground)
}
