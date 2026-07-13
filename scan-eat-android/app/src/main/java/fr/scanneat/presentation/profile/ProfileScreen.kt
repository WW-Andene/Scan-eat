package fr.scanneat.presentation.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.domain.engine.biolism.ETHNICITY_OPTIONS
import fr.scanneat.domain.model.*
import fr.scanneat.presentation.profile.components.ActivitySelector
import fr.scanneat.presentation.profile.components.AllergenSelector
import fr.scanneat.presentation.profile.components.ConditionsSelector
import fr.scanneat.presentation.profile.components.DietSelector
import fr.scanneat.presentation.profile.components.GoalSelector
import fr.scanneat.presentation.profile.components.MetricChip
import fr.scanneat.presentation.profile.components.OutlinedInput
import fr.scanneat.presentation.profile.components.ProfileSection
import fr.scanneat.presentation.profile.components.SexSelector
import fr.scanneat.presentation.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val profile = viewModel.profile.collectAsStateWithLifecycle()
    val bmi          = viewModel.bmiValue.collectAsStateWithLifecycle()
    val tdee         = viewModel.tdee.collectAsStateWithLifecycle()
    val tdeeGoal     = viewModel.tdeeAtGoalWeight.collectAsStateWithLifecycle()
    val saved   = viewModel.saved.collectAsStateWithLifecycle()
    val biolismProfile = viewModel.biolismProfile.collectAsStateWithLifecycle()

    // Local mutable state mirrors the saved profile
    var name       by remember(profile.value.id) { mutableStateOf(profile.value.name) }
    var sex        by remember(profile.value.id) { mutableStateOf(profile.value.sex) }
    var age        by remember(profile.value.id) { mutableStateOf(profile.value.ageYears?.toString() ?: "") }
    var heightCm   by remember(profile.value.id) { mutableStateOf(profile.value.heightCm?.toString() ?: "") }
    var weightKg   by remember(profile.value.id) { mutableStateOf(profile.value.weightKg?.toString() ?: "") }
    var goalWeightKg by remember(profile.value.id) { mutableStateOf(profile.value.goalWeightKg?.toString() ?: "") }
    var activity   by remember(profile.value.id) { mutableStateOf(profile.value.activityLevel) }
    var goal       by remember(profile.value.id) { mutableStateOf(profile.value.goal) }
    var diet       by remember(profile.value.id) { mutableStateOf(profile.value.diet) }
    var allergens  by remember(profile.value.id) { mutableStateOf(profile.value.allergens) }
    var conditions by remember(profile.value.id) { mutableStateOf(profile.value.healthConditions) }
    var isMenstruating by remember(profile.value.id) { mutableStateOf(profile.value.isMenstruating) }
    // Circumferences + ethnicity — previously only editable from Métabolisme >
    // Mon Profil (BiolismProfileScreen), even though they live in the same
    // BiolismRepository already synced with this screen's shared fields.
    var waistCm    by remember(biolismProfile.value) { mutableStateOf(biolismProfile.value.waistCm.takeIf { it > 0 }?.toString() ?: "") }
    var hipCm      by remember(biolismProfile.value) { mutableStateOf(biolismProfile.value.hipCm.takeIf { it > 0 }?.toString() ?: "") }
    var neckCm     by remember(biolismProfile.value) { mutableStateOf(biolismProfile.value.neckCm.takeIf { it > 0 }?.toString() ?: "") }
    var ethnicityId by remember(biolismProfile.value) { mutableStateOf(biolismProfile.value.ethnicityId) }

    LaunchedEffect(saved.value) {
        if (saved.value) { viewModel.clearSaved(); onBack() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title), color = OnBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
                actions = {
                    TextButton(onClick = {
                        // No bound at all previously - an errant value here (e.g. a typo'd
                        // extra digit) silently propagates into ActivityViewModel's kcal-burn
                        // calc and ProfileViewModel's BMI/TDEE. Clamp to sane human ranges
                        // instead of rejecting outright, since a slightly-off real value
                        // (e.g. a very tall/heavy user) should still save, just not corrupt math.
                        viewModel.save(Profile(
                            name          = name.trim(),
                            sex           = sex,
                            ageYears      = age.toIntOrNull()?.coerceIn(1, 120),
                            heightCm      = heightCm.replace(',', '.').toDoubleOrNull()?.coerceIn(50.0, 250.0),
                            weightKg      = weightKg.replace(',', '.').toDoubleOrNull()?.coerceIn(20.0, 400.0),
                            goalWeightKg  = goalWeightKg.replace(',', '.').toDoubleOrNull()?.coerceIn(20.0, 400.0),
                            activityLevel = activity,
                            goal          = goal,
                            diet          = diet,
                            allergens     = allergens,
                            healthConditions = conditions,
                            isMenstruating = isMenstruating,
                        ))
                        viewModel.saveBodyMeasurements(
                            waistCm     = waistCm.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 250.0) ?: 0.0,
                            hipCm       = hipCm.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 250.0) ?: 0.0,
                            neckCm      = neckCm.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0,
                            ethnicityId = ethnicityId,
                        )
                    }) {
                        Text(stringResource(R.string.common_save), color = AccentCoral, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ---- BMI / TDEE preview ----
            if (bmi.value != null || tdee.value != null || tdeeGoal.value != null) {
                Box(Modifier.fillMaxWidth().glassSheen(shape = RoundedCornerShape(12.dp))) {
                    Surface(shape = RoundedCornerShape(12.dp), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                bmi.value?.let { MetricChip(stringResource(R.string.profile_bmi_label), "${it}") }
                                tdee.value?.let { MetricChip("TDEE", stringResource(R.string.profile_tdee_kcal, it.roundToInt())) }
                            }
                            tdeeGoal.value?.let {
                                HorizontalDivider(color = OnSurface.copy(0.08f))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                    MetricChip(stringResource(R.string.profile_tdee_goal_label), stringResource(R.string.profile_tdee_kcal, it.roundToInt()))
                                }
                            }
                        }
                    }
                }
            }

            // ---- Identity ----
            ProfileSection(stringResource(R.string.profile_section_identity)) {
                OutlinedInput(stringResource(R.string.profile_field_name), name) { name = it }
                SexSelector(sex) { sex = it }
                OutlinedInput(stringResource(R.string.profile_field_age), age, KeyboardType.Number) { age = it }
            }

            // ---- Body ----
            ProfileSection(stringResource(R.string.profile_section_body)) {
                OutlinedInput(stringResource(R.string.profile_field_height), heightCm, KeyboardType.Decimal) { heightCm = it }
                OutlinedInput(stringResource(R.string.profile_field_weight), weightKg, KeyboardType.Decimal) { weightKg = it }
                OutlinedInput(stringResource(R.string.profile_field_goal_weight), goalWeightKg, KeyboardType.Decimal) { goalWeightKg = it }
                if (sex == Sex.FEMALE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isMenstruating,
                            onCheckedChange = { isMenstruating = it },
                            colors = CheckboxDefaults.colors(checkedColor = AccentCoral),
                        )
                        Text(stringResource(R.string.profile_menstruating_checkbox), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.8f))
                    }
                }
            }

            // ---- Body measurements (shared with Métabolisme > Mon Profil) ----
            // Only editable from BiolismProfileScreen before — surfaced here too so
            // both profile screens expose the same complete set of fields, and so a
            // user who never opens Métabolisme can still benefit from Navy BF%/WHtR
            // calculations that need these.
            ProfileSection(stringResource(R.string.profile_section_measurements)) {
                OutlinedInput(stringResource(R.string.profile_field_waist), waistCm, KeyboardType.Decimal) { waistCm = it }
                OutlinedInput(stringResource(R.string.profile_field_hip), hipCm, KeyboardType.Decimal) { hipCm = it }
                OutlinedInput(stringResource(R.string.profile_field_neck), neckCm, KeyboardType.Decimal) { neckCm = it }
                Text(stringResource(R.string.profile_field_ethnicity), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
                val isFrench = Locale.current.language == "fr"
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    ETHNICITY_OPTIONS.forEach { opt ->
                        FilterChip(
                            selected = ethnicityId == opt.id,
                            onClick  = { ethnicityId = opt.id },
                            label    = { Text(if (isFrench) opt.labelFr else opt.label, maxLines = 1) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                            ),
                        )
                    }
                }
            }

            // ---- Activity ----
            ProfileSection(stringResource(R.string.profile_section_activity)) {
                ActivitySelector(activity) { activity = it }
                GoalSelector(goal) { goal = it }
            }

            // ---- Allergens ----
            ProfileSection(stringResource(R.string.profile_section_allergens)) {
                AllergenSelector(allergens) { allergens = it }
            }

            // ---- Health conditions ----
            ProfileSection(stringResource(R.string.profile_section_conditions)) {
                ConditionsSelector(conditions) { conditions = it }
            }

            // ---- Diet ----
            ProfileSection(stringResource(R.string.profile_section_diet)) {
                DietSelector(diet) { diet = it }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

}

