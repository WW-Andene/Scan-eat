package fr.scanneat.presentation.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.domain.engine.biolism.ETHNICITY_OPTIONS
import fr.scanneat.domain.engine.scoring.DietKey
import fr.scanneat.domain.engine.scoring.dietNote
import fr.scanneat.domain.model.*
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

// ---- Sub-composables ----

@Composable
private fun MetricChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = AccentCoral, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f))
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun OutlinedInput(
    label: String,
    value: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValue: (String) -> Unit,
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValue,
        label         = { Text(label, fontSize = 13.sp) },
        modifier      = Modifier.fillMaxWidth(),
        singleLine    = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape  = RoundedCornerShape(12.dp),
        colors = scanEatTextFieldColors(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SexSelector(current: Sex, onSelect: (Sex) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Sex.values().forEach { s ->
            val label = when (s) {
                Sex.MALE -> stringResource(R.string.sex_male)
                Sex.FEMALE -> stringResource(R.string.sex_female)
                Sex.NOT_SPECIFIED -> stringResource(R.string.sex_not_specified)
            }
            FilterChip(
                selected = current == s, onClick = { onSelect(s) }, label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                    labelColor = OnBackground.copy(0.7f),
                ),
            )
        }
    }
}

@Composable
private fun ActivitySelector(current: ActivityLevel, onSelect: (ActivityLevel) -> Unit) {
    val labels = mapOf(
        ActivityLevel.SEDENTARY to stringResource(R.string.activity_sedentary), ActivityLevel.LIGHTLY_ACTIVE to stringResource(R.string.activity_lightly_active),
        ActivityLevel.MODERATELY_ACTIVE to stringResource(R.string.activity_moderately_active), ActivityLevel.VERY_ACTIVE to stringResource(R.string.activity_very_active),
        ActivityLevel.EXTRA_ACTIVE to stringResource(R.string.activity_extra_active),
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ActivityLevel.values().forEach { lvl ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .selectable(selected = current == lvl, onClick = { onSelect(lvl) }, role = Role.RadioButton),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(labels[lvl] ?: lvl.name, style = MaterialTheme.typography.bodyMedium, color = OnBackground)
                RadioButton(
                    selected = current == lvl, onClick = null,
                    colors = RadioButtonDefaults.colors(selectedColor = AccentCoral),
                )
            }
            if (lvl != ActivityLevel.EXTRA_ACTIVE) HorizontalDivider(thickness = 0.5.dp, color = SeparatorLight)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoalSelector(current: Goal, onSelect: (Goal) -> Unit) {
    val labels = mapOf(
        Goal.LOSE to stringResource(R.string.goal_lose),
        Goal.MAINTAIN to stringResource(R.string.goal_maintain),
        Goal.GAIN to stringResource(R.string.goal_gain),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Goal.values().forEach { g ->
            FilterChip(
                selected = current == g, onClick = { onSelect(g) },
                label = { Text(labels[g] ?: g.name, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                    labelColor = OnBackground.copy(0.7f),
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DietSelector(current: DietKey, onSelect: (DietKey) -> Unit) {
    val diets = DietKey.values().filter { it != DietKey.NONE }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        FilterChip(
            selected = current == DietKey.NONE, onClick = { onSelect(DietKey.NONE) },
            label = { Text(stringResource(R.string.diet_none), style = MaterialTheme.typography.labelMedium) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                labelColor = OnBackground.copy(0.7f),
            ),
        )
        val isEnglish = Locale.current.language == "en"
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            diets.forEach { d ->
                FilterChip(
                    selected = current == d, onClick = { onSelect(d) },
                    label = { Text(if (isEnglish) d.labelEn else d.labelFr, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral,
                        labelColor = OnBackground.copy(0.7f),
                    ),
                )
            }
        }
        // Diet chips previously carried zero explanation of what each diet actually
        // excludes (or, for vegan, what supplementation it implies) - every other
        // definitional detail in DietChecker.kt's DietDef was already there and
        // simply never reached the UI.
        if (current != DietKey.NONE) {
            dietNote(current, if (isEnglish) "en" else "fr")?.let { note ->
                Text(
                    note, style = MaterialTheme.typography.bodySmall,
                    color = OnBackground.copy(0.6f),
                )
            }
        }
    }
}

@Composable
private fun allergenLabels(): Map<String, String> = mapOf(
    "gluten" to stringResource(R.string.allergen_gluten), "lactose" to stringResource(R.string.allergen_lactose),
    "eggs" to stringResource(R.string.allergen_eggs), "nuts" to stringResource(R.string.allergen_nuts),
    "peanuts" to stringResource(R.string.allergen_peanuts), "soy" to stringResource(R.string.allergen_soy),
    "fish" to stringResource(R.string.allergen_fish), "crustaceans" to stringResource(R.string.allergen_crustaceans),
    "molluscs" to stringResource(R.string.allergen_molluscs), "sesame" to stringResource(R.string.allergen_sesame),
    "celery" to stringResource(R.string.allergen_celery), "mustard" to stringResource(R.string.allergen_mustard),
    "sulfites" to stringResource(R.string.allergen_sulfites), "lupin" to stringResource(R.string.allergen_lupin),
)

@Composable
private fun conditionLabels(): Map<String, String> = mapOf(
    "diabetes" to stringResource(R.string.condition_diabetes),
    "hypertension" to stringResource(R.string.condition_hypertension),
    "pregnancy" to stringResource(R.string.condition_pregnancy),
    "kidney_disease" to stringResource(R.string.condition_kidney_disease),
    "thyroid_disorder" to stringResource(R.string.condition_thyroid_disorder),
    "food_allergies" to stringResource(R.string.condition_food_allergies),
    "intolerances" to stringResource(R.string.condition_intolerances),
    "digestive_disorders" to stringResource(R.string.condition_digestive_disorders),
    "cancer" to stringResource(R.string.condition_cancer),
    "depression" to stringResource(R.string.condition_depression),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConditionsSelector(current: Set<String>, onSelect: (Set<String>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(stringResource(R.string.profile_condition_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            conditionLabels().forEach { (key, label) ->
                FilterChip(
                    selected = key in current,
                    onClick  = { onSelect(if (key in current) current - key else current + key) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Teal.copy(0.2f), selectedLabelColor = Teal,
                        labelColor = OnBackground.copy(0.7f),
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllergenSelector(current: Set<String>, onSelect: (Set<String>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(stringResource(R.string.profile_allergen_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            allergenLabels().forEach { (key, label) ->
                FilterChip(
                    selected = key in current,
                    onClick  = {
                        onSelect(if (key in current) current - key else current + key)
                    },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = semanticAmber().copy(0.2f), selectedLabelColor = semanticAmber(),
                        labelColor = OnBackground.copy(0.7f),
                    ),
                )
            }
        }
    }
}
