package fr.scanneat.presentation.result

import compose.icons.tablericons.Share
import compose.icons.tablericons.AlertCircle
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import android.content.Intent
import android.widget.Toast
import java.util.Locale
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.domain.engine.nutrition.generateProductHints
import fr.scanneat.presentation.report.ReportMisclassificationDialog
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.EmptyListState
import fr.scanneat.presentation.ui.theme.FloatingScreenScaffold
import fr.scanneat.presentation.ui.theme.Gold
import fr.scanneat.presentation.ui.theme.OnBackground
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import fr.scanneat.presentation.ui.theme.ScanEatLoadingIndicator
import fr.scanneat.presentation.ui.theme.ScanEatSnackbarHost
import kotlinx.coroutines.launch

// Orchestrator only — content composition lives in ResultContent.kt, each
// section/banner in cards/*.kt. Was previously a single 467-line file with
// everything inline.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: ResultViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onLog: () -> Unit,
    onOpenResult: (Long) -> Unit = {},
    onOpenProfile: () -> Unit = {},
) {
    val state       = viewModel.state.collectAsStateWithLifecycle()
    val language    = viewModel.language.collectAsStateWithLifecycle()
    val profile     = viewModel.profile.collectAsStateWithLifecycle()
    val activeMedicationNames = viewModel.activeMedicationNames.collectAsStateWithLifecycle()
    val priceEntries = viewModel.priceEntries.collectAsStateWithLifecycle()
    val currencySymbol = viewModel.currencySymbol.collectAsStateWithLifecycle()
    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val pantryStock = viewModel.pantryStock.collectAsStateWithLifecycle()
    val avgLoggedPortionG = viewModel.avgLoggedPortionG.collectAsStateWithLifecycle()
    val scanTutorialSeen = viewModel.scanTutorialSeen.collectAsStateWithLifecycle()
    // rememberSaveable, not remember - a process death while either dialog was open
    // (backgrounding the app is enough on a low-memory device) previously reset both
    // flags to false on restoration, silently closing the LogSheet/SaveDestinationsPopup
    // with zero indication anything happened, rather than restoring them open.
    var showSheet   by rememberSaveable { mutableStateOf(false) }
    var showSaveMenu by rememberSaveable { mutableStateOf(false) }
    var showReportDialog by rememberSaveable { mutableStateOf(false) }
    val context      = LocalContext.current
    val shareTemplate = stringResource(R.string.result_share_text)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Navigate to diary after successful log. ResultViewModel.log() already computed
    // a LogState.Error on failure (Room write failure, disk full, etc.) but nothing
    // here ever read it - a failed save previously just left the sheet sitting open
    // with no feedback, so the user couldn't tell whether their tap had registered.
    // R&D audit finding: Fasting and the Diary had zero cross-reference - logging
    // food mid-fast never surfaced any signal. A Toast (not the snackbar host) is
    // used here specifically because onLog() immediately navigates away to the
    // Diary tab, which would cut off a snackbar mid-display - same precedent
    // ScanScreen already uses for a message that survives its own navigation.
    val loggedDuringFastMessage = stringResource(R.string.result_logged_during_fast)
    val reportSubmittedMessage = stringResource(R.string.report_misclassification_submitted)
    LaunchedEffect(state.value.logState) {
        when (val logState = state.value.logState) {
            is LogState.Done -> {
                showSheet = false
                if (logState.loggedDuringFast) Toast.makeText(context, loggedDuringFastMessage, Toast.LENGTH_LONG).show()
                onLog()
            }
            is LogState.Error -> {
                scope.launch { snackbarHostState.showSnackbar(logState.message) }
                viewModel.clearLogState()
            }
            else -> {}
        }
    }

    // User-requested/audit-found: savePrice()/deletePrice() previously failed
    // completely silently (see ResultViewModel's own doc comment on guardedLaunch) -
    // same one-shot failure snackbar every other guarded-write screen in the app uses.
    val actionFailedMessage = stringResource(R.string.common_log_failed)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(actionFailedMessage)
            viewModel.clearActionFailed()
        }
    }

    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.result_title), color = OnBackground) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground)
            }
        },
        actions = {
            state.value.scanResult?.let { scan ->
                IconButton(onClick = {
                    // app-audit §J1: Locale.US, same as every other formatted numeric string
                    // in the app (see UnitConversion.kt's own doc comment) - without it, a
                    // device set to a locale with non-Latin digits (Arabic-Indic, Persian)
                    // would render the score using those digits in the shared text alone.
                    val text = String.format(Locale.US, shareTemplate, scan.product.name, scan.audit.score, scan.audit.grade.label)
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(sendIntent, null))
                }) {
                    Icon(TablerIcons.Share, stringResource(R.string.result_cd_share), tint = OnBackground)
                }
                HintIconButton(hints = generateProductHints(scan.product, profile.value, language.value, activeMedicationNames.value))
                // User-requested: "signaler une erreur de classification" -
                // e.g. a shampoo or other non-food item that classifyNonFood
                // missed and got scored as food. See
                // ReportMisclassificationDialog's own header.
                IconButton(onClick = { showReportDialog = true }) {
                    Icon(Icons.Rounded.Flag, stringResource(R.string.report_misclassification_action_cd), tint = OnBackground.copy(0.6f))
                }
                IconButton(onClick = { showSaveMenu = true }) {
                    // This opens SaveDestinationsPopup (a multi-select "save to..." dialog),
                    // not a direct favorite toggle - unlike the star buttons in
                    // TemplatesScreen/ScanHistoryScreen/RecipeCard, which do flip favorite
                    // status directly. Content description already said "save options"
                    // (not "favorite") for screen readers, but the star glyph itself is
                    // used app-wide as the direct-toggle affordance - a sighted user
                    // familiar with that pattern elsewhere taps expecting an instant
                    // favorite and gets a dialog instead. Bookmark (still filled when
                    // favorited) keeps the same at-a-glance favorite state without
                    // borrowing the direct-toggle star glyph.
                    Icon(
                        if (scan.favorite) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        stringResource(R.string.result_cd_save_options),
                        tint = if (scan.favorite) Gold else OnBackground,
                    )
                }
                // User-reported: tapping "Logger" showed nothing when tapped while the
                // scan was still loading (this actions row renders in the TopBar
                // unconditionally, before the s.scanResult == null check below gates
                // the loading spinner) - showSheet flipped true with nothing to react
                // to it, since the LogSheet below is itself gated on
                // state.value.scanResult being non-null. Moved inside this same
                // scanResult?.let block so the button can't be tapped at all until
                // there's a scan to log.
                TextButton(onClick = { showSheet = true }) {
                    Text(stringResource(R.string.result_log_it), color = AccentCoral, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        snackbarHost = { ScanEatSnackbarHost(snackbarHostState) },
    ) { padding ->
        val s = state.value
        if (s.notFound) {
            // scanLoad resolved to ScanLoad.Empty - a stale deep link or a deleted
            // history entry, not "still loading". Previously indistinguishable from
            // the pre-load state (both had scanResult == null), so this spun the
            // loading indicator forever with no way out but the back arrow.
            EmptyListState(TablerIcons.AlertCircle, stringResource(R.string.result_not_found_body))
        } else if (s.scanResult == null) {
            // Matches ScoreRing's own size/stroke/track exactly, so the loading
            // state visually sets up the score reveal instead of being a generic
            // spinner unrelated to what's about to appear.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ScanEatLoadingIndicator(size = 196.dp, strokeWidth = 12.dp, trackColor = SurfaceVariant)
            }
        } else {
            ResultContent(
                scan              = s.scanResult,
                personalScore     = s.personalScore,
                comparisonResult  = s.comparisonResult,
                pairings          = s.pairings,
                betterAlternative = s.betterAlternative,
                language          = language.value,
                scoreDelta        = s.scoreDelta,
                scoreHistory      = s.scoreHistory,
                recall            = s.recall,
                pantryStock       = pantryStock.value,
                avgLoggedPortionG = avgLoggedPortionG.value,
                priceEntries      = priceEntries.value,
                currencySymbol    = currencySymbol.value,
                // Nutritionist-delivery audit: the ranked "what's costing the
                // most points" list was previously reachable only via the
                // top-bar hint icon, several taps removed from the headline
                // grade a user actually reads first - the one genuinely
                // actionable ("what to do", not just "what's wrong") piece of
                // content on the whole screen was the least visible. Same
                // generateProductHints call already used for HintIconButton
                // above; pure/cheap, safe to compute again here rather than
                // threading it across the TopBar/content composable boundary.
                improvementTips   = generateProductHints(s.scanResult.product, profile.value, language.value, activeMedicationNames.value).improvementTips,
                onSavePrice       = { price, weight -> viewModel.savePrice(price, weight) },
                onDeletePrice     = { id -> viewModel.deletePrice(id) },
                onOpenResult      = onOpenResult,
                onOpenProfile     = onOpenProfile,
                contentPadding    = padding,
            )
        }
    }

    // Log bottom sheet
    // Each block reads state.value.scanResult once into a local val instead of
    // null-checking then re-reading + force-unwrapping a second snapshot-state read -
    // harmless today only because Compose composition is single-threaded/synchronous,
    // but a local val can't go null out from under it on any future refactor that
    // moves the access across a suspension point or into a remembered callback.
    state.value.scanResult?.let { scan ->
        if (showSheet) {
            LogSheet(
                product    = scan.product,
                isLoading  = state.value.logState is LogState.Loading,
                onConfirm  = { g, slot -> viewModel.log(g, slot) },
                onDismiss  = { showSheet = false },
                // User-requested: "Logger" always implied the product was
                // eaten today - these let it just log a price and/or stock
                // the pantry instead, without touching the diary at all, when
                // Repas isn't one of the checked destinations. Repas alone
                // (the default, and every other LogSheet call site's only
                // behavior) still goes through the exact same viewModel.log()
                // path above, LogState.Loading/Done/Error included.
                showDestinationPicker = true,
                onConfirmWithDestinations = { g, slot, destinations, priceEuros, weightG ->
                    if (fr.scanneat.presentation.result.LogDestination.REPAS in destinations) {
                        viewModel.log(g, slot)
                    }
                    if (fr.scanneat.presentation.result.LogDestination.DEPENSES in destinations && priceEuros != null) {
                        viewModel.savePrice(priceEuros, weightG)
                    }
                    if (fr.scanneat.presentation.result.LogDestination.GARDE_MANGER in destinations) {
                        viewModel.saveToDestinations(setOf(SaveDestination.GARDE_MANGER))
                    }
                    // REPAS's own LogState.Done effect (above) closes the sheet when
                    // present - only close it here ourselves when REPAS wasn't
                    // checked, since nothing else drives showSheet back to false.
                    if (fr.scanneat.presentation.result.LogDestination.REPAS !in destinations) showSheet = false
                },
            )
        }

        if (showSaveMenu) {
            SaveDestinationsPopup(
                alreadyFavorite = scan.favorite,
                onConfirm = { destinations ->
                    viewModel.saveToDestinations(destinations)
                    showSaveMenu = false
                },
                onDismiss = { showSaveMenu = false },
            )
        }

        if (showReportDialog) {
            ReportMisclassificationDialog(
                productName = scan.product.name,
                currentClassificationLabel = stringResource(R.string.report_misclassification_current_food_label),
                onSubmit = { corrected, note ->
                    viewModel.reportMisclassification(corrected, note)
                    showReportDialog = false
                    scope.launch { snackbarHostState.showSnackbar(reportSubmittedMessage) }
                },
                onDismiss = { showReportDialog = false },
            )
        }

        // User-requested: interactive walkthrough shown once, over a real
        // scan result, instead of a static onboarding page - see
        // ScanResultTutorialDialog.kt's own header. scanTutorialSeen defaults
        // to true until UserPreferences resolves, so this never flashes on
        // for an existing user before the real (already-seen) value loads.
        if (!scanTutorialSeen.value) {
            ScanResultTutorialDialog(onDismiss = { viewModel.markScanTutorialSeen() })
        }
    }
}
