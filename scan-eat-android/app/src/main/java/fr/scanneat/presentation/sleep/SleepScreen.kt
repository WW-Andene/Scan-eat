package fr.scanneat.presentation.sleep

import compose.icons.tablericons.Calendar
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Moon
import compose.icons.tablericons.Trash
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.repository.sleep.SleepEntry
import fr.scanneat.presentation.sleep.components.AddSleepEntryDialog
import fr.scanneat.presentation.sleep.components.SleepWeeklyChart
import fr.scanneat.presentation.ui.theme.*
import java.time.format.DateTimeFormatter

/**
 * [embedded] = true skips this screen's own Scaffold/TopAppBar - same
 * convention FastingScreen/HydrationScreen already use when hosted as a
 * Journal sub-tab (see FastingScreen's own doc comment on why).
 */
@Composable
fun SleepScreen(
    viewModel: SleepViewModel = hiltViewModel(),
    onBack: () -> Unit,
    embedded: Boolean = false,
    embeddedBottomPadding: Dp = 0.dp,
    embeddedTopPadding: Dp = 0.dp,
    onOpenCalendar: () -> Unit = {},
) {
    val entries = viewModel.entries.collectAsStateWithLifecycle()
    val goalHours = viewModel.goalHours.collectAsStateWithLifecycle()
    val streak = viewModel.streak.collectAsStateWithLifecycle()
    val longestStreak = viewModel.longestStreak.collectAsStateWithLifecycle()
    val lastNight = viewModel.lastNight.collectAsStateWithLifecycle()
    val weeklyDuration = viewModel.weeklyDuration.collectAsStateWithLifecycle()
    val avgDurationHours = viewModel.avgDurationHours.collectAsStateWithLifecycle()
    val language = viewModel.language.collectAsStateWithLifecycle()

    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val logFailedMessage = stringResource(R.string.common_log_failed)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(logFailedMessage)
            viewModel.clearActionFailed()
        }
    }

    val content = @Composable { padding: PaddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .ambientGloom(base = Background, primary = Violet, secondary = Gold)
                .padding(horizontal = Spacing.L),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            item { Spacer(Modifier.height(Spacing.S)) }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    if (streak.value > 0) StreakBadge(streakDays = streak.value, accentColor = Violet) else Spacer(Modifier)
                    IconButton(onClick = onOpenCalendar) {
                        Icon(TablerIcons.Calendar, stringResource(R.string.sleep_cd_calendar), tint = OnBackground.copy(0.6f))
                    }
                }
            }

            // Last night / add-entry card
            item {
                ScanEatCard(shape = RoundedCornerShape(CardRadius.PROMINENT), contentPadding = PaddingValues(Spacing.XL)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                        val night = lastNight.value
                        if (night != null && night.date == java.time.LocalDate.now().minusDays(if (java.time.LocalTime.now().hour < 12) 0 else 1)) {
                            Icon(TablerIcons.Moon, null, tint = Violet, modifier = Modifier.size(IconSize.EmptyState))
                            Text(
                                stringResource(R.string.sleep_last_night_hours, night.durationHours),
                                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = OnBackground,
                            )
                            Text(stringResource(R.string.sleep_quality_stars, night.quality), style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.7f))
                        } else {
                            Icon(TablerIcons.Moon, null, tint = Violet.copy(0.5f), modifier = Modifier.size(IconSize.EmptyState))
                            Text(stringResource(R.string.sleep_no_entry_today), style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.6f))
                        }
                        Button(onClick = { showAdd = true }, colors = ButtonDefaults.buttonColors(containerColor = Violet)) {
                            Text(stringResource(R.string.sleep_log_button))
                        }
                        Text(
                            stringResource(R.string.sleep_goal_label, goalHours.value),
                            style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f),
                        )
                    }
                }
            }

            if (weeklyDuration.value.any { it.second > 0 }) {
                item { SleepWeeklyChart(weeklyDuration.value, goalHours.value, language.value) }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    avgDurationHours.value?.let {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.sleep_avg_label), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
                            Text(stringResource(R.string.sleep_hours_value, it), style = MaterialTheme.typography.titleMedium, color = OnBackground, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (longestStreak.value > 0) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.sleep_record_label), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
                            Text(stringResource(R.string.common_streak_days_compact, longestStreak.value), style = MaterialTheme.typography.titleMedium, color = OnBackground, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (entries.value.isNotEmpty()) {
                items(entries.value.take(30), key = { it.id }) { entry ->
                    SleepHistoryRow(entry, onDelete = { deleteTarget = entry.id })
                }
            } else {
                item { EmptyListState(TablerIcons.Moon, stringResource(R.string.sleep_empty)) }
            }

            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }

    if (embedded) {
        Box(Modifier.fillMaxSize()) {
            content(PaddingValues(top = embeddedTopPadding, bottom = embeddedBottomPadding))
            ScanEatSnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = embeddedBottomPadding))
        }
    } else {
        FloatingScreenScaffold(
            title = { Text(stringResource(R.string.sleep_title), color = OnBackground) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) } },
            snackbarHost = { ScanEatSnackbarHost(snackbarHostState) },
        ) { padding -> content(padding) }
    }

    if (showAdd) {
        AddSleepEntryDialog(
            currentGoalHours = goalHours.value,
            onGoalChange = { viewModel.setGoalHours(it) },
            onDismiss = { showAdd = false },
            onAdd = { bedtimeMs, wakeMs, quality, notes ->
                viewModel.log(bedtimeMs, wakeMs, quality, notes)
                showAdd = false
            },
        )
    }

    deleteTarget?.let { targetId ->
        val entryDate = entries.value.find { it.id == targetId }?.date?.toString()
        DeleteConfirmDialog(itemName = entryDate, onConfirm = { viewModel.delete(targetId); deleteTarget = null }, onDismiss = { deleteTarget = null })
    }
}

@Composable
private fun SleepHistoryRow(entry: SleepEntry, onDelete: () -> Unit) {
    val isPrism = LocalThemeName.current == "prism"
    Surface(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        color = if (isPrism) PrismFillColor else SurfaceVariant.copy(alpha = StandardCardAlpha),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.M),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), style = MaterialTheme.typography.bodyLarge, color = OnBackground)
                Text(
                    stringResource(R.string.sleep_hours_value, entry.durationHours) + " · " + stringResource(R.string.sleep_quality_stars, entry.quality) +
                        (entry.notes.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f),
                )
            }
            IconButton(onClick = onDelete) { Icon(TablerIcons.Trash, stringResource(R.string.common_delete), tint = OnBackground.copy(0.5f)) }
        }
    }
}
