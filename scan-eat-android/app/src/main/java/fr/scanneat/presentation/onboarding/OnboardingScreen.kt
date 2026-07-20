package fr.scanneat.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.local.prefs.ApiMode
import fr.scanneat.domain.model.ActivityLevel
import fr.scanneat.domain.model.Goal
import fr.scanneat.domain.model.Sex
import fr.scanneat.presentation.profile.components.ActivitySelector
import fr.scanneat.presentation.profile.components.GoalSelector
import fr.scanneat.presentation.profile.components.OutlinedInput
import fr.scanneat.presentation.profile.components.SexSelector
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.launch

/** Bundle only natively round-trips a handful of types - an enum needs an explicit
 *  Saver (stored as its .name) to survive rememberSaveable's process-death restore.
 *  internal (not private) so BiolismOnboardingScreen's identical enum-field fix can
 *  reuse this instead of duplicating it. */
internal inline fun <reified T : Enum<T>> enumSaver() = Saver<T, String>(
    save = { it.name },
    restore = { enumValueOf<T>(it) },
)

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    onDone: () -> Unit,
    onGoToProfile: () -> Unit = {},
) {
    val exit = viewModel.exit.collectAsStateWithLifecycle()
    LaunchedEffect(exit.value) {
        when (exit.value) {
            OnboardingViewModel.Exit.SCAN    -> onDone()
            OnboardingViewModel.Exit.PROFILE -> onGoToProfile()
            null -> {}
        }
    }

    // Previously plain remember{} - MainActivity unlocks orientation for tablets/
    // foldables (smallestScreenWidthDp >= 600), so a rotation there (or any
    // locale/font-scale change, on any device) recreated the Activity and wiped
    // every field typed so far, resetting to page 0 with no way to recover.
    var page by rememberSaveable { mutableStateOf(0) }
    var selectedMode by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(ApiMode.DIRECT) }
    var apiKey by rememberSaveable { mutableStateOf("") }
    var apiKeyVisible by rememberSaveable { mutableStateOf(false) }
    var serverUrl by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(Sex.NOT_SPECIFIED) }
    var ageText by rememberSaveable { mutableStateOf("") }
    var heightText by rememberSaveable { mutableStateOf("") }
    var weightText by rememberSaveable { mutableStateOf("") }
    var activity by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(ActivityLevel.MODERATELY_ACTIVE) }
    var goal by rememberSaveable(stateSaver = enumSaver()) { mutableStateOf(Goal.MAINTAIN) }

    Scaffold(containerColor = Background) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .ambientGloom(base = Background, primary = AccentCoral, secondary = Gold)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.XL),
        ) {
            Spacer(Modifier.height(40.dp))

            // Improvement: step-progress dots — previously no visual indicator of how many
            // pages exist or which one you're on; users had no way to gauge remaining effort.
            if (page > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    (1..3).forEach { step ->
                        val active = step == page
                        Box(
                            Modifier
                                .size(if (active) 20.dp else 8.dp, 8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (active) AccentCoral else OnBackground.copy(0.2f)),
                        )
                    }
                }
            }

            when (page) {
                // ---- Page 0: Welcome ----
                0 -> {
                    Icon(Icons.Default.QrCodeScanner, null, tint = AccentCoral, modifier = Modifier.size(64.dp))
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, color = OnBackground, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.onboarding_welcome_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnBackground.copy(0.7f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.weight(1f))
                    ScanEatPrimaryButton(
                        onClick = { page = 1 },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.onboarding_start_button), style = MaterialTheme.typography.titleMedium) }
                }

                // ---- Page 1: Value proposition — what sets this apart ----
                1 -> {
                    Text(stringResource(R.string.onboarding_value_title), style = MaterialTheme.typography.headlineSmall, color = OnBackground, fontWeight = FontWeight.Bold)

                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                        ValueCard(
                            icon    = Icons.Default.Fingerprint,
                            title   = stringResource(R.string.onboarding_value_transparency_title),
                            body    = stringResource(R.string.onboarding_value_transparency_body),
                        )
                        ValueCard(
                            icon    = Icons.Default.MonitorHeart,
                            title   = stringResource(R.string.onboarding_value_biolism_title),
                            body    = stringResource(R.string.onboarding_value_biolism_body),
                        )
                    }

                    // New: feature-domain preview — previously page 1 only showed 2 abstract
                    // value cards with no concrete preview of what the app actually tracks;
                    // users arrived at profile setup with no idea what domains were covered.
                    FeatureDomainChips()

                    Spacer(Modifier.weight(1f))
                    ScanEatPrimaryButton(
                        onClick = { page = 2 },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.onboarding_continue_button), style = MaterialTheme.typography.titleMedium) }
                }

                // ---- Page 2: API mode ----
                2 -> {
                    Text(stringResource(R.string.onboarding_config_title), style = MaterialTheme.typography.headlineSmall, color = OnBackground, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.onboarding_config_body),
                        style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.7f), textAlign = TextAlign.Center,
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ModeCard(
                            selected  = selectedMode == ApiMode.DIRECT,
                            title     = stringResource(R.string.onboarding_mode_direct_title),
                            subtitle  = stringResource(R.string.onboarding_mode_direct_subtitle),
                            onClick   = { selectedMode = ApiMode.DIRECT },
                        )
                        ModeCard(
                            selected  = selectedMode == ApiMode.SERVER,
                            title     = stringResource(R.string.onboarding_mode_server_title),
                            subtitle  = stringResource(R.string.onboarding_mode_server_subtitle),
                            onClick   = { selectedMode = ApiMode.SERVER },
                        )
                    }

                    if (selectedMode == ApiMode.DIRECT) {
                        OutlinedTextField(
                            value = apiKey, onValueChange = { apiKey = it },
                            label = { Text(stringResource(R.string.onboarding_api_key_label)) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                    Icon(if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, stringResource(R.string.settings_toggle_key_visibility), tint = OnBackground.copy(0.6f))
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = scanEatTextFieldColors(),
                            shape = RoundedCornerShape(CardRadius.CONTROL),
                        )
                        Text(stringResource(R.string.onboarding_api_key_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.4f))
                    } else {
                        OutlinedTextField(
                            value = serverUrl, onValueChange = { serverUrl = it },
                            label = { Text(stringResource(R.string.settings_server_url)) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            colors = scanEatTextFieldColors(),
                            shape = RoundedCornerShape(CardRadius.CONTROL),
                        )
                    }

                    Spacer(Modifier.weight(1f))
                    ScanEatPrimaryButton(
                        onClick = {
                            viewModel.setMode(selectedMode)
                            if (apiKey.isNotBlank()) viewModel.setApiKey(apiKey)
                            if (serverUrl.isNotBlank()) viewModel.setServerUrl(serverUrl)
                            page = 3
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = (selectedMode == ApiMode.DIRECT && apiKey.isNotBlank()) ||
                                  (selectedMode == ApiMode.SERVER && serverUrl.isNotBlank()),
                    ) { Text(stringResource(R.string.onboarding_continue_button), style = MaterialTheme.typography.titleMedium) }
                    TextButton(
                        // Previously never persisted selectedMode at all on skip — it
                        // only "worked" because ApiMode.DIRECT also happens to be
                        // UserPreferences' own default, so a toggle to SERVER (with no
                        // URL filled in) then skipping silently discarded that choice.
                        onClick = { viewModel.setMode(selectedMode); viewModel.skipApiSetup(); page = 3 },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.onboarding_api_skip), color = OnBackground.copy(0.5f)) }
                }

                // ---- Page 3: Profile capture — previously just a prompt pointing at a
                // separate, skippable screen. hasMinimalProfile() (PersonalScoreEngine)
                // requires sex+age+height+weight before dailyTargets()/PersonalScoreEngine
                // compute anything at all, so a "Skip" tap here meant zero personalized
                // score/targets indefinitely - the fields are now captured inline instead,
                // still skippable, reusing the exact selectors ProfileScreen itself uses. ----
                3 -> {
                    Text(stringResource(R.string.onboarding_profile_title), style = MaterialTheme.typography.headlineSmall, color = OnBackground, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.onboarding_profile_body),
                        style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.7f), textAlign = TextAlign.Center,
                    )
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(Spacing.M),
                    ) {
                        SexSelector(sex) { sex = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            OutlinedInput(stringResource(R.string.profile_field_age), ageText, KeyboardType.Number, Modifier.weight(1f)) { ageText = it.filter(Char::isDigit) }
                            // ProfileScreen already normalizes comma->period before filtering -
                            // this abbreviated onboarding capture didn't, so on this app's
                            // default French locale (whose numeric keypad decimal key is a
                            // comma) typing "75,5" had the comma silently stripped instead of
                            // converted, concatenating both digit groups into "755".
                            OutlinedInput(stringResource(R.string.profile_field_height), heightText, KeyboardType.Number, Modifier.weight(1f)) { heightText = it.replace(',', '.').filter { c -> c.isDigit() || c == '.' } }
                            OutlinedInput(stringResource(R.string.profile_field_weight), weightText, KeyboardType.Number, Modifier.weight(1f)) { weightText = it.replace(',', '.').filter { c -> c.isDigit() || c == '.' } }
                        }
                        ActivitySelector(activity) { activity = it }
                        GoalSelector(goal) { goal = it }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        val scope = rememberCoroutineScope()
                        val canSave = sex != Sex.NOT_SPECIFIED && ageText.toIntOrNull() != null && heightText.toDoubleOrNull() != null && weightText.toDoubleOrNull() != null
                        ScanEatPrimaryButton(
                            onClick = {
                                scope.launch {
                                    viewModel.saveMinimalProfile(sex, ageText.toIntOrNull()?.coerceIn(1, 120), heightText.toDoubleOrNull()?.coerceIn(50.0, 250.0), weightText.toDoubleOrNull()?.coerceIn(20.0, 400.0), activity, goal)
                                    viewModel.finish()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = canSave,
                        ) { Text(stringResource(R.string.onboarding_continue_button), style = MaterialTheme.typography.titleMedium) }
                        TextButton(
                            // "More options" still routes to the full Profile screen (diet,
                            // allergens, health conditions, goal weight aren't captured here) -
                            // whatever was already filled in above is saved first so it isn't
                            // silently discarded by following this link instead of "Continue".
                            onClick = {
                                scope.launch {
                                    if (canSave) viewModel.saveMinimalProfile(sex, ageText.toIntOrNull()?.coerceIn(1, 120), heightText.toDoubleOrNull()?.coerceIn(50.0, 250.0), weightText.toDoubleOrNull()?.coerceIn(20.0, 400.0), activity, goal)
                                    viewModel.finish(goToProfile = true)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.onboarding_profile_cta), color = OnBackground.copy(0.7f)) }
                        TextButton(
                            onClick = { viewModel.finish() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.onboarding_profile_skip), color = OnBackground.copy(0.5f)) }
                    }
                }
            }
        }
    }

}

@Composable
private fun FeatureDomainChips() {
    val domains = listOf(
        Icons.Default.QrCodeScanner to R.string.onboarding_domain_scan,
        Icons.Default.RestaurantMenu to R.string.onboarding_domain_diet,
        Icons.Default.WaterDrop to R.string.onboarding_domain_hydration,
        Icons.Default.Timer to R.string.onboarding_domain_fasting,
        Icons.Default.Scale to R.string.onboarding_domain_weight,
        Icons.Default.DirectionsRun to R.string.onboarding_domain_activity,
        Icons.Default.Medication to R.string.onboarding_domain_medication,
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(
            stringResource(R.string.onboarding_domains_title),
            style = MaterialTheme.typography.labelSmall,
            color = OnBackground.copy(0.5f),
        )
        domains.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                row.forEach { (icon, label) ->
                    Surface(shape = RoundedCornerShape(50), color = SurfaceVariant) {
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(icon, null, tint = AccentCoral, modifier = Modifier.size(14.dp))
                            Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.8f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ValueCard(icon: ImageVector, title: String, body: String) {
    ScanEatCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
            Icon(icon, null, tint = AccentCoral, modifier = Modifier.size(28.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
            }
        }
    }
}

@Composable
private fun ModeCard(selected: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(CardRadius.CONTROL),
        color   = if (selected) AccentCoral.copy(0.15f) else SurfaceVariant,
        border  = if (selected) ButtonDefaults.outlinedButtonBorder(enabled = true).copy(width = 1.5.dp) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
            // onClick = null: the whole Surface above is already clickable (onClick = onClick) —
            // a second independent actionable control nested inside it is a real screen-reader/
            // interaction conflict (two actionable elements claiming the same tap), not just redundant.
            RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = AccentCoral))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
            }
        }
    }
}
