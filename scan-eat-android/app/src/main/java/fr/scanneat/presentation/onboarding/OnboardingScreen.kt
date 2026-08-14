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

/** Nullable counterpart to [enumSaver] - for an optional enum field like
 *  Profile.fatLevel/muscleLevel, where "not set" is a real, distinct state
 *  from any enum value, not just a UI default to fall back to. */
internal inline fun <reified T : Enum<T>> enumSaverNullable() = Saver<T?, String>(
    save = { it?.name ?: "" },
    restore = { if (it.isEmpty()) null else enumValueOf<T>(it) },
)

/** Bundle doesn't natively round-trip a raw Set<String> - same gap ProfileScreen's own
 *  identical stringSetSaver fixes, via an ArrayList<String> (Bundle-safe) instead. */
private val stringSetSaver = Saver<Set<String>, ArrayList<String>>(
    save = { ArrayList(it) },
    restore = { it.toSet() },
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
    var conditions by rememberSaveable(stateSaver = stringSetSaver) { mutableStateOf(emptySet<String>()) }

    // Card-wrapped wizard body, header (Skip) and footer (Back/dots) all living
    // inside one ScanEatCard with Spacing.XL content padding, matching the one
    // other multi-step wizard in the app (BiolismOnboardingScreen) exactly —
    // that screen already establishes this as the app's standard "onboarding
    // wizard" shape (one card, Spacing.XL edges, a zeroed-padding TextButton
    // flush against the card's own leading/trailing edge). This screen instead
    // had no card at all (bare TextButtons floating on the ambientGloom
    // background) and its own different Spacing.L (16dp) padding — a visible
    // seam between the app's two onboarding wizards, and the reason the header
    // (Skip) and footer (Back) rows read as two unrelated components rather
    // than symmetric top/bottom bars of the same container. Also unifies the
    // Skip/Back label alpha (was 0.5f vs 0.6f — no reason for these two
    // same-weight secondary nav actions to differ) to Biolism's own 0.5f.
    Scaffold(containerColor = Background, snackbarHost = { ScanEatSnackbarHost(snackbarHostState) }) { padding ->
        // BoxWithConstraints, not Box - Page 3 (Profile capture) is the one page with
        // enough fields (sex/age/height/weight/activity/goal/conditions) to overflow a
        // short screen, and its own scrollable Column relies on Modifier.weight(1f) to
        // both bound itself to the remaining space and let the Skip/Save footer stay put
        // below it. weight(1f) only has real space to expand into when the ScanEatCard
        // around it has a bounded (not wrap-content) height - maxHeight here is that
        // real bound, the same fix BiolismOnboardingScreen's own identical wizard
        // already applies for its own multi-field steps.
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(padding)
                .ambientGloom(base = Background, primary = AccentCoral, secondary = Gold),
            contentAlignment = Alignment.Center,
        ) {
            ScanEatCard(
                modifier = Modifier.padding(Spacing.XL).heightIn(max = maxHeight - Spacing.XL * 2),
                shape = RoundedCornerShape(CardRadius.PROMINENT),
                contentPadding = PaddingValues(Spacing.XL),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.M),
                ) {
                    // Header — Skip, flush against the card's trailing edge (zeroed end
                    // padding), the same edge-alignment treatment Biolism's own footer
                    // Back/Skip buttons use against their leading edge below.
                    // Pages 0-1 (Welcome/Value proposition) had no exit at all short of
                    // abandoning the app entirely - every later page already reaches its own
                    // onSkip (ApiModePage page 2, ProfileCapturePage page 3), same
                    // viewModel.finish() this jumps straight to. A returning user reinstalling,
                    // or anyone who just wants to explore the app first, previously had no way
                    // to bail out of these first two pages.
                    if (page <= 1) {
                        Box(Modifier.fillMaxWidth()) {
                            TextButton(
                                onClick = { viewModel.finish() },
                                modifier = Modifier.align(Alignment.CenterEnd),
                                contentPadding = PaddingValues(start = Spacing.M, end = 0.dp, top = Spacing.S, bottom = Spacing.S),
                            ) {
                                Text(stringResource(R.string.onboarding_skip_all), color = OnBackground.copy(0.5f))
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
                            conditions = conditions, onConditionsChange = { conditions = it },
                            onSaveAndContinue = { s, age, h, w, act, g, cond -> if (viewModel.saveMinimalProfile(s, age, h, w, act, g, cond)) viewModel.finish() },
                            onSaveAndGoToProfile = { s, age, h, w, act, g, cond -> if (viewModel.saveMinimalProfile(s, age, h, w, act, g, cond)) viewModel.finish(goToProfile = true) },
                            onGoToProfileWithoutSaving = { viewModel.finish(goToProfile = true) },
                            onSkip = { viewModel.finish() },
                        )
                    }

                    // Footer — step-progress dots + Back, symmetric with the header Skip
                    // row above: same Box(fillMaxWidth)/Alignment shape, same TextButton
                    // label alpha (0.5f), same zeroed-edge content padding (start=0 here,
                    // end=0 above) so both rows read as one consistent top/bottom bar
                    // pairing rather than two differently-styled components.
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
                                            .size(if (active) 24.dp else 8.dp, 8.dp)
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
                            TextButton(
                                onClick = { page -= 1 },
                                modifier = Modifier.align(Alignment.CenterStart),
                                contentPadding = PaddingValues(start = 0.dp, end = Spacing.M, top = Spacing.S, bottom = Spacing.S),
                            ) {
                                Text(stringResource(R.string.common_back), color = OnBackground.copy(0.5f))
                            }
                        }
                    }
                }
            }
        }
    }
}
