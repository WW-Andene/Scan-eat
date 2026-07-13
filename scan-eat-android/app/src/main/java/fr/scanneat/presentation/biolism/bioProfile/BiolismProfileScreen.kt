package fr.scanneat.presentation.biolism.bioProfile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
import java.util.Locale

@Composable
fun BiolismProfileScreen(viewModel: BiolismProfileViewModel = hiltViewModel()) {
    val profile = viewModel.profile.collectAsStateWithLifecycle()
    val saved   = viewModel.saved.collectAsStateWithLifecycle()

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

    fun dispWeight(kg: Double): String = if (useImperial) "%.1f lb".format(kg * 2.20462) else "%.1f kg".format(kg)
    fun dispCirc(cm: Double): String = if (useImperial) "%.1f in".format(cm / 2.54) else "%.1f cm".format(cm)
    fun dispHeight(cm: Double): String {
        if (!useImperial) return "%.1f cm".format(cm)
        val totalIn = cm / 2.54
        val ft = (totalIn / 12).toInt()
        val inch = totalIn % 12
        return "$ft′ ${"%.1f".format(inch)}″"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.L)
            .padding(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        if (saved.value) {
            Surface(shape = RoundedCornerShape(12.dp), color = Teal.copy(0.1f), border = androidx.compose.foundation.BorderStroke(1.dp, Teal.copy(0.3f)), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.bioprofile_saved), modifier = Modifier.padding(Spacing.M), color = Teal, fontWeight = FontWeight.Bold)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(false to "cm/kg", true to "ft/lb").forEach { (imperial, label) ->
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
                val activityLabel = ACTIVITY_LEVELS.firstOrNull { it.id == p.activityId }?.label ?: "—"
                val ethnicityLabel = ETHNICITY_OPTIONS.firstOrNull { it.id == p.ethnicityId }?.label ?: "—"
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
                height, useImperial, { it / 2.54 }, { it * 2.54 },
            ) { height = it }
            BioInputUnit(
                stringResource(R.string.bioprofile_field_weight), stringResource(R.string.bioprofile_field_weight_imperial),
                weight, useImperial, { it * 2.20462 }, { it / 2.20462 },
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
                        Text(lvl.label, style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = if (activityId == lvl.id) FontWeight.SemiBold else FontWeight.Normal)
                        Text(lvl.note, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
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
                        Text(opt.label, style = MaterialTheme.typography.bodySmall, color = OnBackground, fontWeight = FontWeight.Medium)
                        Text(opt.note, style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.35f))
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
                waist, useImperial, { it / 2.54 }, { it * 2.54 },
            ) { waist = it }
            BioInputUnit(
                stringResource(R.string.bioprofile_field_hip), stringResource(R.string.bioprofile_field_hip_imperial),
                hip, useImperial, { it / 2.54 }, { it * 2.54 },
            ) { hip = it }
            BioInputUnit(
                stringResource(R.string.bioprofile_field_neck), stringResource(R.string.bioprofile_field_neck_imperial),
                neck, useImperial, { it / 2.54 }, { it * 2.54 },
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
        Button(
            onClick = {
                // Onboarding normalizes comma decimals (French-locale default)
                // before parsing; this edit screen didn't, so a French user
                // typing "82,5" here (metric mode) silently saved weightKg=0.
                viewModel.save(BiolismProfile(
                    sex         = sex,
                    ageYears    = age.toIntOrNull() ?: 0,
                    heightCm    = height.replace(',', '.').toDoubleOrNull() ?: 0.0,
                    weightKg    = weight.replace(',', '.').toDoubleOrNull() ?: 0.0,
                    activityId  = activityId,
                    ethnicityId = ethnicityId,
                    waistCm     = waist.replace(',', '.').toDoubleOrNull() ?: 0.0,
                    hipCm       = hip.replace(',', '.').toDoubleOrNull() ?: 0.0,
                    neckCm      = neck.replace(',', '.').toDoubleOrNull() ?: 0.0,
                    cycleDay    = cycleDay.toIntOrNull()?.coerceIn(1, 28) ?: 14,
                ))
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Gold),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(stringResource(R.string.bioprofile_save_button), color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

