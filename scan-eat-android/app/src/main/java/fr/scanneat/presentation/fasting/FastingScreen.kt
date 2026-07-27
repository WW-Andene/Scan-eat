package fr.scanneat.presentation.fasting

import kotlinx.coroutines.flow.StateFlow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.presentation.fasting.components.ActiveFastCard
import fr.scanneat.presentation.fasting.components.FastingHistoryRow
import fr.scanneat.presentation.fasting.components.FastingHistoryStatsCard
import fr.scanneat.presentation.fasting.components.StartFastForm
import fr.scanneat.presentation.ui.theme.*

/**
 * [embedded] = true skips this screen's own Scaffold/TopAppBar — used when
 * hosted as a Journal sub-tab, where the tab row itself is the header and a
 * second nested app bar (with a dead-end back arrow) would be redundant
 * chrome. Standalone push-navigation callers leave it false.
 */
@Composable
fun FastingScreen(
    viewModel: FastingViewModel = hiltViewModel(),
    onBack: () -> Unit,
    embedded: Boolean = false,
    // Only meaningful when [embedded] — the host (DiaryScreen) supplies this so
    // this screen's own LazyColumn reserves the same floating-bottom-nav
    // clearance the host itself is already reserving.
    embeddedBottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onOpenCalendar: () -> Unit = {},
) {
    val state          = viewModel.fastingState.collectAsStateWithLifecycle()
    val history        = viewModel.history.collectAsStateWithLifecycle()
    val streak         = viewModel.streak.collectAsStateWithLifecycle()
    val language       = viewModel.language.collectAsStateWithLifecycle()
    val personalRecord = viewModel.personalRecord.collectAsStateWithLifecycle()
    viewModel.tick.collectAsStateWithLifecycle() // force recomposition every second

    // start()/stop()/cancel() previously failed completely silently - see
    // FastingViewModel.actionFailed's own comment.
    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val logFailedMessage = stringResource(R.string.common_log_failed)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(logFailedMessage)
            viewModel.clearActionFailed()
        }
    }

    // cancel() (unlike stop()) discards the in-progress fast with no history record
    // at all - a misclick on a button sitting right next to Finish previously threw
    // away hours of progress with zero confirmation and no way to undo it.
    var showCancelConfirm by remember { mutableStateOf(false) }

    var targetHours by remember { mutableIntStateOf(16) }
    var customMode by remember { mutableStateOf(false) }
    var customStart by remember { mutableStateOf("18:00") }
    var customEnd by remember { mutableStateOf("06:00") }
    val customHours = remember(customStart, customEnd) {
        runCatching {
            val start = java.time.LocalTime.parse(customStart)
            val end = java.time.LocalTime.parse(customEnd)
            var minutes = java.time.Duration.between(start, end).toMinutes()
            if (minutes <= 0) minutes += 24 * 60 // overnight window, e.g. 18:00 -> 06:00
            minutes / 60.0
        }.getOrNull()
    }

    val content = @Composable { padding: PaddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .ambientGloom(base = Background, primary = Teal, secondary = AccentCoral)
                .padding(horizontal = Spacing.L),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            item { Spacer(Modifier.height(Spacing.S)) }

            // Previously no calendar entry point at all for Jeûne (unlike
            // Weight/Activity/Hydration, which each had their own local one) -
            // routes straight to the unified Calendar instead of adding yet
            // another single-domain grid.
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onOpenCalendar) {
                        Icon(Icons.Default.CalendarMonth, stringResource(R.string.fasting_cd_calendar), tint = OnBackground.copy(0.6f))
                    }
                }
            }

            // Streak
            if (streak.value > 0) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Icon(Icons.Default.LocalFireDepartment, null, tint = CalorieOrange)
                        Text(pluralStringResource(R.plurals.fasting_streak, streak.value, streak.value), style = MaterialTheme.typography.bodyMedium, color = OnBackground, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Timer
            item {
                ScanEatCard(shape = RoundedCornerShape(CardRadius.PROMINENT), contentPadding = PaddingValues(24.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.L),
                    ) {
                        val fs = state.value
                        if (fs != null) {
                            ActiveFastCard(
                                fastingState = fs,
                                language = language.value,
                                personalRecord = personalRecord.value,
                                onCancel = { showCancelConfirm = true },
                                onStop = { viewModel.stop() },
                            )
                        } else {
                            StartFastForm(
                                targetHours = targetHours, onTargetHoursChange = { targetHours = it },
                                customMode = customMode, onCustomModeChange = { customMode = it },
                                customStart = customStart, onCustomStartChange = { customStart = it },
                                customEnd = customEnd, onCustomEndChange = { customEnd = it },
                                customHours = customHours,
                                onStart = { hours -> viewModel.start(hours) },
                            )
                        }
                    }
                }
            }

            // History stats summary card
            if (history.value.isNotEmpty()) {
                item { FastingHistoryStatsCard(history.value) }
            }

            // History list
            if (history.value.isNotEmpty()) {
                items(history.value.take(20), key = { it.endMs }) { c -> FastingHistoryRow(c) }
            }

            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }

    if (embedded) {
        Box(Modifier.fillMaxSize()) {
            content(PaddingValues(bottom = embeddedBottomPadding))
            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = embeddedBottomPadding))
        }
    } else {
        FloatingScreenScaffold(
            title = { Text(stringResource(R.string.fasting_title), color = OnBackground) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { padding -> content(padding) }
    }

    if (showCancelConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.fasting_cancel_confirm_title),
            body = stringResource(R.string.fasting_cancel_confirm_body),
            // Not common_cancel - ConfirmDialog's own dismiss button already shows
            // that same generic "Cancel" label (to back out of *this* dialog), which
            // would've meant two buttons on the same dialog both reading "Cancel"
            // with opposite effects.
            confirmLabel = stringResource(R.string.fasting_cancel_confirm_action),
            onConfirm = { viewModel.cancel(); showCancelConfirm = false },
            onDismiss = { showCancelConfirm = false },
        )
    }
}
