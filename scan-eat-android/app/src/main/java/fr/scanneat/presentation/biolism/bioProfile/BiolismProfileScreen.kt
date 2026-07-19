package fr.scanneat.presentation.biolism.bioProfile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.domain.engine.biolism.*
import fr.scanneat.presentation.ui.theme.*
import fr.scanneat.presentation.ui.theme.dispWeight as sharedDispWeight
import java.util.Locale

@Composable
fun BiolismProfileScreen(viewModel: BiolismProfileViewModel = hiltViewModel()) {
    val profile = viewModel.profile.collectAsStateWithLifecycle()
    val saved   = viewModel.saved.collectAsStateWithLifecycle()
    val completeness = viewModel.profileCompleteness.collectAsStateWithLifecycle()
    // Previously never collected here - every activity-level/ethnicity label and
    // note on this screen used the bare English field regardless of the app's
    // language setting, unlike BiolismOnboardingScreen (the only caller that
    // already used the lang-aware .label(lang)/.note(lang) extensions).
    val language = viewModel.language.collectAsStateWithLifecycle()

    val p = profile.value

    // Local mutable state
    var sex        by remember(p) { mutableStateOf(p.sex) }
    var age        by remember(p) { mutableStateOf(p.ageYears.takeIf { it > 0 }?.toString() ?: "") }
    var height     by remember(p) { mutableStateOf(p.heightCm.takeIf { it > 0 }?.toString() ?: "") }
    var weight     by remember(p) { mutableStateOf(p.weightKg.takeIf { it > 0 }?.toString() ?: "") }
    var activityId by remember(p) { mutableStateOf(p.activityId) }
    var ethnicityId by remember(p) { mutableStateOf(p.ethnicityId) }
    var waist      by remember(p) { mutableStateOf(p.waistCm.takeIf { it > 0 }?.toString() ?: "") }
    var hip        by remember(p) { mutableStateOf(p.hipCm.takeIf { it > 0 }?.toString() ?: "") }
    var neck       by remember(p) { mutableStateOf(p.neckCm.takeIf { it > 0 }?.toString() ?: "") }
    var cycleDay   by remember(p) { mutableStateOf(p.cycleDay.toString()) }
    var useImperial by remember { mutableStateOf(false) }

    LaunchedEffect(saved.value) { if (saved.value) viewModel.clearSaved() }

    fun dispWeight(kg: Double): String = sharedDispWeight(kg, useImperial)
    fun dispCirc(cm: Double): String =
        if (useImperial) "%.1f in".format(Locale.US, cm / CM_TO_IN) else "%.1f cm".format(Locale.US, cm)
    fun dispHeight(cm: Double): String {
        if (!useImperial) return "%.1f cm".format(Locale.US, cm)
        // Round to the displayed 1-decimal precision *before* splitting into feet/
        // inches - splitting the raw unrounded value let a remainder like 11.9999in
        // independently round up to display "12.0" without carrying into the next
        // foot (e.g. "5′ 12.0″" instead of "6′ 0.0″").
        val totalIn = kotlin.math.round(cm / CM_TO_IN * 10) / 10.0
        val ft = (totalIn / 12).toInt()
        val inch = totalIn % 12
        return "$ft′ ${"%.1f".format(Locale.US, inch)}″"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.L)
            .padding(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(Spacing.S))

        if (saved.value) {
            Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = Teal.copy(0.1f), border = androidx.compose.foundation.BorderStroke(1.dp, Teal.copy(0.3f)), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.bioprofile_saved), modifier = Modifier.padding(Spacing.M), color = Teal, fontWeight = FontWeight.Bold)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(false to stringResource(R.string.bioprofile_unit_metric), true to stringResource(R.string.bioprofile_unit_imperial)).forEach { (imperial, label) ->
                    FilterChip(
                        selected = useImperial == imperial,
                        onClick = { useImperial = imperial },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GoldHaze, selectedLabelColor = Gold),
                    )
                }
            }
        }

        // ── Overview (read-only recap) ──────────────────────────────────────────
        val hasData = p.ageYears > 0 || p.heightCm > 0 || p.weightKg > 0 || p.sex != BiolismSex.NOT_SPECIFIED
        if (hasData) {
            ScanEatCard(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.bioprofile_overview_title), style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
                    Surface(shape = RoundedCornerShape(4.dp), color = Teal.copy(0.15f), border = androidx.compose.foundation.BorderStroke(1.dp, Teal.copy(0.3f))) {
                        Text(stringResource(R.string.bioprofile_overview_saved_badge), modifier = Modifier.padding(horizontal = Spacing.S, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall, color = Teal, fontWeight = FontWeight.Bold)
                    }
                }
                val activityLabel = ACTIVITY_LEVELS.firstOrNull { it.id == p.activityId }?.label(language.value) ?: "—"
                val ethnicityLabel = ETHNICITY_OPTIONS.firstOrNull { it.id == p.ethnicityId }?.label(language.value) ?: "—"
                OverviewRow(stringResource(R.string.profile_field_age), if (p.ageYears > 0) "${p.ageYears}" else null)
                OverviewRow(stringResource(R.string.bioprofile_field_weight), if (p.weightKg > 0) dispWeight(p.weightKg) else null)
                OverviewRow(stringResource(R.string.profile_field_height), if (p.heightCm > 0) dispHeight(p.heightCm) else null)
                OverviewRow(stringResource(R.string.bioprofile_section_activity), activityLabel)
                OverviewRow(stringResource(R.string.bioprofile_field_waist), if (p.waistCm > 0) dispCirc(p.waistCm) else null)
                OverviewRow(stringResource(R.string.bioprofile_field_hip), if (p.hipCm > 0) dispCirc(p.hipCm) else null)
                OverviewRow(stringResource(R.string.bioprofile_field_neck), if (p.neckCm > 0) dispCirc(p.neckCm) else null)
                OverviewRow(stringResource(R.string.bioprofile_section_ethnicity), ethnicityLabel)
                if (p.sex == BiolismSex.FEMALE) {
                    OverviewRow(stringResource(R.string.bioprofile_field_cycle_day), "${p.cycleDay} / 28")
                }
                HorizontalDivider(color = OnBackground.copy(0.06f))
                val pct = (completeness.value * 100).toInt()
                Text(stringResource(R.string.bioprofile_completeness_label, pct), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
                LinearProgressIndicator(
                    progress = { completeness.value },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (completeness.value >= 1f) semanticGreen() else Gold,
                    trackColor = OnBackground.copy(0.1f),
                )
            }
        }

        // ── Identity ──────────────────────────────────────────────────────────
        ProfileSection(stringResource(R.string.profile_section_identity)) {
            // Sex
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                BiolismSex.values().forEach { s ->
                    val label = when(s) {
                        BiolismSex.MALE -> stringResource(R.string.bioprofile_sex_male)
                        BiolismSex.FEMALE -> stringResource(R.string.bioprofile_sex_female)
                        else -> stringResource(R.string.sex_not_specified)
                    }
                    FilterChip(selected = sex == s, onClick = { sex = s }, label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = GoldHaze, selectedLabelColor = Gold, labelColor = OnBackground.copy(0.6f)))
                }
            }
            BioInput(stringResource(R.string.profile_field_age), age, KeyboardType.Number) { age = it }
            BioInputUnit(
                stringResource(R.string.profile_field_height), stringResource(R.string.profile_field_height_imperial),
                height, useImperial, { it / CM_TO_IN }, { it * CM_TO_IN },
            ) { height = it }
            BioInputUnit(
                stringResource(R.string.bioprofile_field_weight), stringResource(R.string.bioprofile_field_weight_imperial),
                weight, useImperial, { it * KG_TO_LB }, { it / KG_TO_LB },
            ) { weight = it }
        }

        // ── Activity ──────────────────────────────────────────────────────────
        ProfileSection(stringResource(R.string.bioprofile_section_activity)) {
            ACTIVITY_LEVELS.forEach { lvl ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = activityId == lvl.id, onClick = { activityId = lvl.id }, role = Role.RadioButton),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(lvl.label(language.value), style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = if (activityId == lvl.id) FontWeight.SemiBold else FontWeight.Normal)
                        Text(lvl.note(language.value), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
                    }
                    RadioButton(selected = activityId == lvl.id, onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = Gold))
                }
                if (lvl != ACTIVITY_LEVELS.last()) HorizontalDivider(color = OnBackground.copy(0.06f))
            }
        }

        // ── Ethnicity ─────────────────────────────────────────────────────────
        ProfileSection(stringResource(R.string.bioprofile_section_ethnicity)) {
            Text(stringResource(R.string.bioprofile_ethnicity_hint),
                style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
            ETHNICITY_OPTIONS.forEach { opt ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = ethnicityId == opt.id, onClick = { ethnicityId = opt.id }, role = Role.RadioButton),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(opt.label(language.value), style = MaterialTheme.typography.bodySmall, color = OnBackground, fontWeight = FontWeight.Medium)
                        // Bumped from 0.35f - a UI/UX audit flagged this descriptive note
                        // as real informational content rendered too faint.
                        Text(opt.note(language.value), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
                    }
                    RadioButton(selected = ethnicityId == opt.id, onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = Gold))
                }
                HorizontalDivider(color = OnBackground.copy(0.05f))
            }
        }

        // ── Circumferences ────────────────────────────────────────────────────
        ProfileSection(stringResource(R.string.bioprofile_section_circumferences)) {
            Text(stringResource(R.string.bioprofile_circumferences_hint),
                style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
            BioInputUnit(
                stringResource(R.string.bioprofile_field_waist), stringResource(R.string.bioprofile_field_waist_imperial),
                waist, useImperial, { it / CM_TO_IN }, { it * CM_TO_IN },
            ) { waist = it }
            BioInputUnit(
                stringResource(R.string.bioprofile_field_hip), stringResource(R.string.bioprofile_field_hip_imperial),
                hip, useImperial, { it / CM_TO_IN }, { it * CM_TO_IN },
            ) { hip = it }
            BioInputUnit(
                stringResource(R.string.bioprofile_field_neck), stringResource(R.string.bioprofile_field_neck_imperial),
                neck, useImperial, { it / CM_TO_IN }, { it * CM_TO_IN },
            ) { neck = it }
        }

        // ── Cycle (female) ────────────────────────────────────────────────────
        if (sex == BiolismSex.FEMALE) {
            ProfileSection(stringResource(R.string.bioprofile_section_cycle)) {
                Text(stringResource(R.string.bioprofile_cycle_hint),
                    style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
                BioInput(stringResource(R.string.bioprofile_field_cycle_day), cycleDay, KeyboardType.Number) { v -> if (v.toIntOrNull()?.let { it in 1..28 } != false) cycleDay = v }
                Slider(value = (cycleDay.toIntOrNull() ?: 14).toFloat(), onValueChange = { cycleDay = it.toInt().toString() },
                    valueRange = 1f..28f, steps = 26, colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold))
                val cd = cycleDay.toIntOrNull() ?: 14
                val phaseLabel = when {
                    cd <= 5  -> stringResource(R.string.bioprofile_phase_menstrual)
                    cd <= 13 -> stringResource(R.string.bioprofile_phase_follicular)
                    cd == 14 -> stringResource(R.string.bioprofile_phase_ovulation)
                    cd <= 21 -> stringResource(R.string.bioprofile_phase_luteal_early)
                    else     -> stringResource(R.string.bioprofile_phase_luteal_late)
                }
                Text(stringResource(R.string.bioprofile_cycle_day_summary, cd, phaseLabel), style = MaterialTheme.typography.bodySmall, color = Violet)
            }
        }

        // ── Save ──────────────────────────────────────────────────────────────
        ScanEatPrimaryButton(
            onClick = {
                // Onboarding normalizes comma decimals (French-locale default)
                // before parsing; this edit screen didn't, so a French user
                // typing "82,5" here (metric mode) silently saved weightKg=0.
                // No bound at all previously - an errant value here (e.g. a typo'd
                // extra digit) silently propagates into BiolismEngine's TDEE/BMI/
                // body-fat% math. Clamped to the same sane human ranges
                // ProfileScreen's equivalent save() already uses, rather than
                // rejecting outright, since a slightly-off real value should still
                // save, just not corrupt downstream math.
                viewModel.save(BiolismProfile(
                    sex         = sex,
                    ageYears    = age.toIntOrNull()?.coerceIn(1, 120) ?: 0,
                    heightCm    = height.replace(',', '.').toDoubleOrNull()?.coerceIn(50.0, 250.0) ?: 0.0,
                    weightKg    = weight.replace(',', '.').toDoubleOrNull()?.coerceIn(20.0, 400.0) ?: 0.0,
                    activityId  = activityId,
                    ethnicityId = ethnicityId,
                    waistCm     = waist.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 250.0) ?: 0.0,
                    hipCm       = hip.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 250.0) ?: 0.0,
                    neckCm      = neck.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0,
                    cycleDay    = cycleDay.toIntOrNull()?.coerceIn(1, 28) ?: 14,
                ))
            },
            modifier = Modifier.fillMaxWidth(),
            containerColor = Gold,
        ) {
            Text(stringResource(R.string.bioprofile_save_button), color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

