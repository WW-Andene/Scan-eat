package fr.scanneat.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
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
import fr.scanneat.domain.model.*
import fr.scanneat.presentation.biolism.bioProfile.BioInputUnit
import fr.scanneat.presentation.profile.components.ActivitySelector
import fr.scanneat.presentation.profile.components.AllergenSelector
import fr.scanneat.presentation.profile.components.ConditionsSelector
import fr.scanneat.presentation.profile.components.DietSelector
import fr.scanneat.presentation.profile.components.GoalSelector
import fr.scanneat.presentation.profile.components.MetricChip
import fr.scanneat.presentation.profile.components.OutlinedInput
import fr.scanneat.presentation.profile.components.ProfileSection
import fr.scanneat.presentation.profile.components.SexSelector
import fr.scanneat.presentation.onboarding.enumSaver
import fr.scanneat.presentation.ui.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt

/** Bundle doesn't natively round-trip a raw Set<String> - same gap enumSaver() (see
 *  fr.scanneat.presentation.onboarding.OnboardingScreen) fixes for enums, via an
 *  ArrayList<String> (which IS Bundle-safe) instead. */
private val stringSetSaver = Saver<Set<String>, ArrayList<String>>(
    save = { ArrayList(it) },
    restore = { it.toSet() },
)

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
    val bmiCat = viewModel.bmiCat.collectAsStateWithLifecycle()
    val useImperial = viewModel.useImperial.collectAsStateWithLifecycle()

    // Local mutable state mirrors the saved profile - keyed on the whole Profile
    // object (a data class, so this only re-derives when a real field actually
    // differs), not profile.value.id: id is a constant "default" that never
    // changes in this single-profile app, so keying on it alone captured
    // whatever profile.value happened to be at the very first composition
    // (typically the StateFlow's blank Profile() seed, since prefs.profile
    // loads from DataStore asynchronously) and then never re-derived again -
    // every field below stayed frozen at blank/default even after the real
    // saved data streamed in a moment later, and saving from that state wrote
    // those blanks back over the real data. Matches the already-correct
    // biolismProfile.value-keyed pattern used 4 lines below in this same file.
    // rememberSaveable (not remember) below - this is a long form (15 fields) a user
    // can easily spend a minute or more filling in; a process death mid-edit (the app
    // backgrounded to check a scale/tape measure is a completely plausible flow here)
    // previously discarded every unsaved field silently on return, re-deriving from
    // the still-stale saved profile instead of restoring what was actually typed.
    var name       by rememberSaveable(profile.value) { mutableStateOf(profile.value.name) }
    var sex        by rememberSaveable(profile.value, stateSaver = enumSaver()) { mutableStateOf(profile.value.sex) }
    var age        by rememberSaveable(profile.value) { mutableStateOf(profile.value.ageYears?.toString() ?: "") }
    var heightCm   by rememberSaveable(profile.value) { mutableStateOf(profile.value.heightCm?.toString() ?: "") }
    var weightKg   by rememberSaveable(profile.value) { mutableStateOf(profile.value.weightKg?.toString() ?: "") }
    var goalWeightKg by rememberSaveable(profile.value) { mutableStateOf(profile.value.goalWeightKg?.toString() ?: "") }
    var activity   by rememberSaveable(profile.value, stateSaver = enumSaver()) { mutableStateOf(profile.value.activityLevel) }
    var goal       by rememberSaveable(profile.value, stateSaver = enumSaver()) { mutableStateOf(profile.value.goal) }
    var diet       by rememberSaveable(profile.value, stateSaver = enumSaver()) { mutableStateOf(profile.value.diet) }
    var allergens  by rememberSaveable(profile.value, stateSaver = stringSetSaver) { mutableStateOf(profile.value.allergens) }
    var conditions by rememberSaveable(profile.value, stateSaver = stringSetSaver) { mutableStateOf(profile.value.healthConditions) }
    var isMenstruating by rememberSaveable(profile.value) { mutableStateOf(profile.value.isMenstruating) }
    // Circumferences + ethnicity — previously only editable from Métabolisme >
    // Mon Profil (BiolismProfileScreen), even though they live in the same
    // BiolismRepository already synced with this screen's shared fields.
    var waistCm    by rememberSaveable(biolismProfile.value) { mutableStateOf(biolismProfile.value.waistCm.takeIf { it > 0 }?.toString() ?: "") }
    var hipCm      by rememberSaveable(biolismProfile.value) { mutableStateOf(biolismProfile.value.hipCm.takeIf { it > 0 }?.toString() ?: "") }
    var neckCm     by rememberSaveable(biolismProfile.value) { mutableStateOf(biolismProfile.value.neckCm.takeIf { it > 0 }?.toString() ?: "") }
    var ethnicityId by rememberSaveable(biolismProfile.value) { mutableStateOf(biolismProfile.value.ethnicityId) }

    LaunchedEffect(saved.value) {
        if (saved.value) { viewModel.clearSaved(); onBack() }
    }

    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.profile_title), color = OnBackground) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
        actions = {
            TextButton(onClick = {
                // No bound at all previously - an errant value here (e.g. a typo'd
                // extra digit) silently propagates into ActivityViewModel's kcal-burn
                // calc and ProfileViewModel's BMI/TDEE. Clamp to sane human ranges
                // instead of rejecting outright, since a slightly-off real value
                // (e.g. a very tall/heavy user) should still save, just not corrupt math.
                viewModel.save(
                    profile = Profile(
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
                    ),
                    waistCm     = waistCm.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 250.0) ?: 0.0,
                    hipCm       = hipCm.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 250.0) ?: 0.0,
                    neckCm      = neckCm.replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 0.0,
                    ethnicityId = ethnicityId,
                )
            }) {
                Text(stringResource(R.string.common_save), color = AccentCoral, fontWeight = FontWeight.SemiBold)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .ambientGloom(base = Background, primary = AccentCoral, secondary = Gold)
                .padding(horizontal = 20.dp),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            item { Spacer(Modifier.height(Spacing.XS)) }

            // ---- BMI / TDEE preview ----
            if (bmi.value != null || tdee.value != null || tdeeGoal.value != null) {
                item {
                val currentProfile = profile.value
                // Macro ratio: recommended P/G/L split by diet, expressed as display percentages.
                val (pPct, cPct, fPct) = when (currentProfile.diet) {
                    DietKey.KETO       -> Triple(25f, 5f, 70f)
                    DietKey.CARNIVORE  -> Triple(40f, 0f, 60f)
                    DietKey.PALEO      -> Triple(30f, 25f, 45f)
                    DietKey.MEDITERRANEAN -> Triple(20f, 50f, 30f)
                    else -> when (currentProfile.goal) {
                        Goal.LOSE  -> Triple(35f, 35f, 30f)
                        Goal.GAIN  -> Triple(35f, 45f, 20f)
                        else       -> Triple(25f, 45f, 30f)
                    }
                }
                // Goal ETA: weeks to reach goal weight at a realistic 0.5 kg/week deficit/surplus.
                val etaWeeks: Int? = run {
                    val cw = currentProfile.weightKg ?: return@run null
                    val gw = currentProfile.goalWeightKg ?: return@run null
                    val diff = abs(gw - cw)
                    if (diff < 0.5) null else (diff / 0.5).roundToInt()
                }

                ScanEatCard(
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    verticalArrangement = Arrangement.spacedBy(Spacing.S),
                ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                bmi.value?.let { bmiVal ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        MetricChip(stringResource(R.string.profile_bmi_label), "$bmiVal")
                                        bmiCat.value?.let { cat ->
                                            val (catLabel, catColor) = when (cat) {
                                                fr.scanneat.domain.engine.scoring.BmiCategory.UNDERWEIGHT -> stringResource(R.string.profile_bmi_cat_underweight) to semanticBlue()
                                                fr.scanneat.domain.engine.scoring.BmiCategory.NORMAL      -> stringResource(R.string.profile_bmi_cat_normal)      to semanticGreen()
                                                fr.scanneat.domain.engine.scoring.BmiCategory.OVERWEIGHT  -> stringResource(R.string.profile_bmi_cat_overweight)  to semanticAmber()
                                                // OBESE_2 and OBESE_3 previously both rendered in the exact same
                                                // full-opacity semanticRed(), giving no visual distinction between
                                                // two categories of meaningfully different severity, while OBESE_1
                                                // was (correctly) lighter via alpha. Alpha already maxes out at
                                                // OBESE_2, so OBESE_3 darkens the same hue instead of picking an
                                                // independent color - this keeps it correct under every colorblind
                                                // mode, since it's a scale of whatever semanticRed() already
                                                // returned for the active mode, not a second hardcoded color.
                                                fr.scanneat.domain.engine.scoring.BmiCategory.OBESE_1     -> stringResource(R.string.profile_bmi_cat_obese1)      to semanticRed().copy(0.8f)
                                                fr.scanneat.domain.engine.scoring.BmiCategory.OBESE_2     -> stringResource(R.string.profile_bmi_cat_obese2)      to semanticRed()
                                                fr.scanneat.domain.engine.scoring.BmiCategory.OBESE_3     -> stringResource(R.string.profile_bmi_cat_obese3)      to
                                                    semanticRed().let { it.copy(red = it.red * 0.7f, green = it.green * 0.7f, blue = it.blue * 0.7f) }
                                            }
                                            Surface(shape = RoundedCornerShape(50), color = catColor.copy(alpha = 0.15f)) {
                                                Text(catLabel, modifier = Modifier.padding(horizontal = Spacing.S, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = catColor)
                                            }
                                        }
                                    }
                                }
                                tdee.value?.let { MetricChip("TDEE", stringResource(R.string.profile_tdee_kcal, it.roundToInt())) }
                            }
                            tdeeGoal.value?.let {
                                HorizontalDivider(color = OnSurface.copy(0.08f))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                    MetricChip(stringResource(R.string.profile_tdee_goal_label), stringResource(R.string.profile_tdee_kcal, it.roundToInt()))
                                }
                            }
                            // Improvement: macro ratio bar showing recommended P/G/L distribution.
                            HorizontalDivider(color = OnSurface.copy(0.08f))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.profile_macro_ratio_label), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp)),
                                ) {
                                    Box(Modifier.weight(pPct).fillMaxHeight().background(semanticGreen()))
                                    Box(Modifier.weight(cPct).fillMaxHeight().background(AccentCoral))
                                    Box(Modifier.weight(fPct).fillMaxHeight().background(Gold))
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                    val proteinAbbr = stringResource(R.string.macro_protein_abbr)
                                    val carbsAbbr   = stringResource(R.string.macro_carbs_abbr)
                                    val fatAbbr     = stringResource(R.string.macro_fat_abbr)
                                    listOf("$proteinAbbr ${pPct.toInt()}%" to semanticGreen(), "$carbsAbbr ${cPct.toInt()}%" to AccentCoral, "$fatAbbr ${fPct.toInt()}%" to Gold).forEach { (label, color) ->
                                        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = color)
                                    }
                                }
                            }
                            // New: goal weight progress + weeks-to-goal ETA.
                            val cw = currentProfile.weightKg
                            val gw = currentProfile.goalWeightKg
                            if (cw != null && gw != null && abs(gw - cw) >= 0.1) {
                                HorizontalDivider(color = OnSurface.copy(0.08f))
                                val startWeight = if (gw < cw) maxOf(cw, gw + 30.0) else minOf(cw, gw - 30.0)
                                val totalRange = abs(gw - startWeight).coerceAtLeast(0.1)
                                val progress = ((cw - startWeight) / (gw - startWeight)).toFloat().coerceIn(0f, 1f)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(stringResource(R.string.profile_goal_progress_label), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                                        etaWeeks?.let { Text(stringResource(R.string.profile_goal_eta_weeks, it), style = MaterialTheme.typography.labelSmall, color = AccentCoral) }
                                    }
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = AccentCoral,
                                        trackColor = OnSurface.copy(0.1f),
                                    )
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${cw}kg", style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                                        Text("→ ${gw}kg", style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                                    }
                                }
                            }
                        }
                }
            }

            // ---- Identity ----
            item {
                ProfileSection(stringResource(R.string.profile_section_identity)) {
                    OutlinedInput(stringResource(R.string.profile_field_name), name) { name = it }
                    SexSelector(sex) { sex = it }
                    OutlinedInput(stringResource(R.string.profile_field_age), age, KeyboardType.Number) { age = it }
                }
            }

            // ---- Body ----
            // Same app-wide metric/imperial preference as the Weight tab (prefs.useImperialWeight)
            // - these fields previously always treated typed input as cm/kg regardless of that
            // setting, so a user in imperial mode could silently save a pound value as kilograms.
            item {
                ProfileSection(stringResource(R.string.profile_section_body)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(false to stringResource(R.string.bioprofile_unit_metric), true to stringResource(R.string.bioprofile_unit_imperial)).forEach { (imperial, label) ->
                            FilterChip(
                                selected = useImperial.value == imperial,
                                onClick = { viewModel.setUseImperial(imperial) },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral),
                            )
                        }
                    }
                }
                BioInputUnit(
                    stringResource(R.string.profile_field_height), stringResource(R.string.profile_field_height_imperial),
                    heightCm, useImperial.value, { it / CM_TO_IN }, { it * CM_TO_IN },
                ) { heightCm = it }
                BioInputUnit(
                    stringResource(R.string.profile_field_weight), stringResource(R.string.profile_field_weight_imperial),
                    weightKg, useImperial.value, { it * KG_TO_LB }, { it / KG_TO_LB },
                ) { weightKg = it }
                BioInputUnit(
                    stringResource(R.string.profile_field_goal_weight), stringResource(R.string.profile_field_goal_weight_imperial),
                    goalWeightKg, useImperial.value, { it * KG_TO_LB }, { it / KG_TO_LB },
                ) { goalWeightKg = it }
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
            }

            // ---- Body measurements (shared with Métabolisme > Mon Profil) ----
            // Only editable from BiolismProfileScreen before — surfaced here too so
            // both profile screens expose the same complete set of fields, and so a
            // user who never opens Métabolisme can still benefit from Navy BF%/WHtR
            // calculations that need these.
            item {
                ProfileSection(stringResource(R.string.profile_section_measurements)) {
                    BioInputUnit(
                        stringResource(R.string.profile_field_waist), stringResource(R.string.profile_field_waist_imperial),
                        waistCm, useImperial.value, { it / CM_TO_IN }, { it * CM_TO_IN },
                    ) { waistCm = it }
                    BioInputUnit(
                        stringResource(R.string.profile_field_hip), stringResource(R.string.profile_field_hip_imperial),
                        hipCm, useImperial.value, { it / CM_TO_IN }, { it * CM_TO_IN },
                    ) { hipCm = it }
                    BioInputUnit(
                        stringResource(R.string.profile_field_neck), stringResource(R.string.profile_field_neck_imperial),
                        neckCm, useImperial.value, { it / CM_TO_IN }, { it * CM_TO_IN },
                    ) { neckCm = it }
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
            }

            // ---- Activity ----
            item {
                ProfileSection(stringResource(R.string.profile_section_activity)) {
                    ActivitySelector(activity) { activity = it }
                    GoalSelector(goal) { goal = it }
                }
            }

            // ---- Allergens ----
            item {
                ProfileSection(stringResource(R.string.profile_section_allergens)) {
                    AllergenSelector(allergens) { allergens = it }
                }
            }

            // ---- Health conditions ----
            item {
                ProfileSection(stringResource(R.string.profile_section_conditions)) {
                    ConditionsSelector(conditions) { conditions = it }
                }
            }

            // ---- Diet ----
            item {
                ProfileSection(stringResource(R.string.profile_section_diet)) {
                    DietSelector(diet) { diet = it }
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }

}

