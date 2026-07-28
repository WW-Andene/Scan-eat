package fr.scanneat.presentation.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
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
import fr.scanneat.presentation.biolism.bioProfile.BioInputUnit
import fr.scanneat.presentation.profile.components.ActivitySelector
import fr.scanneat.presentation.profile.components.AllergenSelector
import fr.scanneat.presentation.profile.components.ConditionsSelector
import fr.scanneat.presentation.profile.components.DietSelector
import fr.scanneat.presentation.profile.components.GoalSelector
import fr.scanneat.presentation.profile.components.MetricChip
import fr.scanneat.presentation.profile.components.OutlinedInput
import fr.scanneat.presentation.profile.components.ProfileMetricsPreviewCard
import fr.scanneat.presentation.profile.components.ProfileSection
import fr.scanneat.presentation.profile.components.SexSelector
import fr.scanneat.presentation.onboarding.enumSaver
import fr.scanneat.presentation.ui.theme.*

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
    val profileLoaded = viewModel.profileLoaded.collectAsStateWithLifecycle()

    // save()'s DataStore writes previously ran completely unguarded - see
    // ProfileViewModel.actionFailed's own comment. A failed write now surfaces
    // here as a one-shot snackbar instead of silently doing nothing.
    val snackbarHostState = remember { SnackbarHostState() }
    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val actionFailedMessage = stringResource(R.string.common_log_failed)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(actionFailedMessage)
            viewModel.clearActionFailed()
        }
    }

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
        snackbarHost = { ScanEatSnackbarHost(snackbarHostState) },
    ) { padding ->
        // Previously rendered the form immediately with profile.value's Profile()
        // seed default (blank name, NOT_SPECIFIED sex, etc.) for the brief window
        // before prefs.profile's real DataStore-backed value loads asynchronously -
        // a visible flash of empty fields on every cold open of this screen.
        if (!profileLoaded.value) {
            Box(
                Modifier.fillMaxSize().padding(padding).ambientGloom(base = Background, primary = AccentCoral, secondary = Gold),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = AccentCoral) }
            return@FloatingScreenScaffold
        }
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
                    ProfileMetricsPreviewCard(
                        currentProfile = profile.value,
                        bmi = bmi.value,
                        bmiCat = bmiCat.value,
                        tdee = tdee.value,
                        tdeeGoal = tdeeGoal.value,
                        useImperial = useImperial.value,
                    )
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

