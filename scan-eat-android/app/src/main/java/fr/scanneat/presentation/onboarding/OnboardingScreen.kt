package fr.scanneat.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.local.prefs.ApiMode
import fr.scanneat.domain.model.ActivityLevel
import fr.scanneat.domain.model.Goal
import fr.scanneat.domain.model.Sex
import fr.scanneat.presentation.onboarding.components.ApiModePage
import fr.scanneat.presentation.onboarding.components.ProfileCapturePage
import fr.scanneat.presentation.onboarding.components.ValuePropositionPage
import fr.scanneat.presentation.onboarding.components.WelcomePage
import fr.scanneat.presentation.ui.theme.*

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

    // Every OnboardingViewModel write previously ran completely unguarded - see
    // its own actionFailed comment. A failed write now surfaces here as a
    // one-shot snackbar instead of crashing the app on a new user's first screen.
    val snackbarHostState = remember { SnackbarHostState() }
    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val actionFailedMessage = stringResource(R.string.common_log_failed)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(actionFailedMessage)
            viewModel.clearActionFailed()
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

    Scaffold(containerColor = Background, snackbarHost = { ScanEatSnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .ambientGloom(base = Background, primary = AccentCoral, secondary = Gold)
                .padding(horizontal = Spacing.L),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.XL),
        ) {
            Spacer(Modifier.height(40.dp))

            // Pages 0-1 (Welcome/Value proposition) had no exit at all short of
            // abandoning the app entirely - every later page already reaches its own
            // onSkip (ApiModePage page 2, ProfileCapturePage page 3), same
            // viewModel.finish() this jumps straight to. A returning user reinstalling,
            // or anyone who just wants to explore the app first, previously had no way
            // to bail out of these first two pages.
            if (page <= 1) {
                Box(Modifier.fillMaxWidth()) {
                    TextButton(onClick = { viewModel.finish() }, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Text(stringResource(R.string.onboarding_skip_all), color = OnBackground.copy(0.5f))
                    }
                }
            }

            // Improvement: step-progress dots — previously no visual indicator of how many
            // pages exist or which one you're on; users had no way to gauge remaining effort.
            if (page > 0) {
                Box(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically,
                    ) {
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
                    // A user who picked Server mode then wanted to change it after
                    // already reaching Profile capture (page 3), or who just wanted
                    // to re-read the value-proposition page, had no way back short of
                    // abandoning onboarding entirely (there's no "restart" affordance
                    // either) - every other multi-step wizard in the app
                    // (BiolismOnboardingScreen) already has a Back button at every
                    // step past the first; this one didn't.
                    TextButton(onClick = { page -= 1 }, modifier = Modifier.align(Alignment.CenterStart)) {
                        Text(stringResource(R.string.common_back), color = OnBackground.copy(0.6f))
                    }
                }
            }

            when (page) {
                // ---- Page 0: Welcome ----
                0 -> WelcomePage(onNext = { page = 1 })

                // ---- Page 1: Value proposition — what sets this apart ----
                1 -> ValuePropositionPage(onNext = { page = 2 })

                // ---- Page 2: API mode ----
                2 -> ApiModePage(
                    selectedMode = selectedMode, onModeChange = { selectedMode = it },
                    apiKey = apiKey, onApiKeyChange = { apiKey = it },
                    apiKeyVisible = apiKeyVisible, onToggleApiKeyVisible = { apiKeyVisible = !apiKeyVisible },
                    serverUrl = serverUrl, onServerUrlChange = { serverUrl = it },
                    onContinue = {
                        viewModel.setMode(selectedMode)
                        if (apiKey.isNotBlank()) viewModel.setApiKey(apiKey)
                        if (serverUrl.isNotBlank()) viewModel.setServerUrl(serverUrl)
                        page = 3
                    },
                    onSkip = {
                        // Previously never persisted selectedMode at all on skip — it
                        // only "worked" because ApiMode.DIRECT also happens to be
                        // UserPreferences' own default, so a toggle to SERVER (with no
                        // URL filled in) then skipping silently discarded that choice.
                        viewModel.setMode(selectedMode); viewModel.skipApiSetup(); page = 3
                    },
                )

                // ---- Page 3: Profile capture — previously just a prompt pointing at a
                // separate, skippable screen. hasMinimalProfile() (PersonalScoreEngine)
                // requires sex+age+height+weight before dailyTargets()/PersonalScoreEngine
                // compute anything at all, so a "Skip" tap here meant zero personalized
                // score/targets indefinitely - the fields are now captured inline instead,
                // still skippable, reusing the exact selectors ProfileScreen itself uses. ----
                3 -> ProfileCapturePage(
                    sex = sex, onSexChange = { sex = it },
                    ageText = ageText, onAgeTextChange = { ageText = it },
                    heightText = heightText, onHeightTextChange = { heightText = it },
                    weightText = weightText, onWeightTextChange = { weightText = it },
                    activity = activity, onActivityChange = { activity = it },
                    goal = goal, onGoalChange = { goal = it },
                    onSaveAndContinue = { s, age, h, w, act, g -> if (viewModel.saveMinimalProfile(s, age, h, w, act, g)) viewModel.finish() },
                    onSaveAndGoToProfile = { s, age, h, w, act, g -> if (viewModel.saveMinimalProfile(s, age, h, w, act, g)) viewModel.finish(goToProfile = true) },
                    onGoToProfileWithoutSaving = { viewModel.finish(goToProfile = true) },
                    onSkip = { viewModel.finish() },
                )
            }
        }
    }

}
