package fr.scanneat.presentation.mood

import compose.icons.tablericons.Calendar
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.Heart
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
import fr.scanneat.data.repository.mood.MoodEntry
import fr.scanneat.domain.engine.dashboard.IntakeSleepMoodLink
import fr.scanneat.domain.engine.dashboard.SleepMoodCorrelation
import fr.scanneat.presentation.mood.components.AddMoodEntryDialog
import fr.scanneat.presentation.mood.components.MoodWeeklyChart
import fr.scanneat.presentation.ui.theme.*
import java.time.format.DateTimeFormatter

/**
 * [embedded] = true skips this screen's own Scaffold/TopAppBar - same
 * convention SleepScreen/FastingScreen already use when hosted as a
 * Journal sub-tab.
 */
@Composable
fun MoodScreen(
    viewModel: MoodViewModel = hiltViewModel(),
    onBack: () -> Unit,
    embedded: Boolean = false,
    embeddedBottomPadding: Dp = 0.dp,
    embeddedTopPadding: Dp = 0.dp,
    onOpenCalendar: () -> Unit = {},
) {
    val entries = viewModel.entries.collectAsStateWithLifecycle()
    val streak = viewModel.streak.collectAsStateWithLifecycle()
    val longestStreak = viewModel.longestStreak.collectAsStateWithLifecycle()
    val today = viewModel.today.collectAsStateWithLifecycle()
    val weeklyMoodStress = viewModel.weeklyMoodStress.collectAsStateWithLifecycle()
    val avgMood = viewModel.avgMood.collectAsStateWithLifecycle()
    val avgStress = viewModel.avgStress.collectAsStateWithLifecycle()
    val sleepMoodCorrelation = viewModel.sleepMoodCorrelation.collectAsStateWithLifecycle()
    val intakeSleepMoodLink = viewModel.intakeSleepMoodLink.collectAsStateWithLifecycle()
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
                        Icon(TablerIcons.Calendar, stringResource(R.string.mood_cd_calendar), tint = OnBackground.copy(0.6f))
                    }
                }
            }

            // Today / add-entry card
            item {
                ScanEatCard(shape = RoundedCornerShape(CardRadius.PROMINENT), contentPadding = PaddingValues(Spacing.XL)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                        val entry = today.value
                        if (entry != null) {
                            Icon(TablerIcons.Heart, null, tint = Violet, modifier = Modifier.size(IconSize.EmptyState))
                            Text(
                                stringResource(R.string.mood_today_summary, entry.mood, entry.stress),
                                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = OnBackground,
                            )
                        } else {
                            Icon(TablerIcons.Heart, null, tint = Violet.copy(0.5f), modifier = Modifier.size(IconSize.EmptyState))
                            Text(stringResource(R.string.mood_no_entry_today), style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.6f))
                        }
                        Button(onClick = { showAdd = true }, colors = ButtonDefaults.buttonColors(containerColor = Violet)) {
                            Text(stringResource(R.string.mood_log_button))
                        }
                    }
                }
            }

            if (weeklyMoodStress.value.any { it.second > 0 || it.third > 0 }) {
                item { MoodWeeklyChart(weeklyMoodStress.value, language.value) }
            }

            sleepMoodCorrelation.value?.let { correlation ->
                item { SleepMoodCorrelationCard(correlation) }
            }

            intakeSleepMoodLink.value?.let { link ->
                item { IntakeSleepMoodLinkCard(link) }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    avgMood.value?.let {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.mood_avg_mood_label), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
                            Text(stringResource(R.string.mood_score_value, it), style = MaterialTheme.typography.titleMedium, color = OnBackground, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    avgStress.value?.let {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.mood_avg_stress_label), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
                            Text(stringResource(R.string.mood_score_value, it), style = MaterialTheme.typography.titleMedium, color = OnBackground, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (longestStreak.value > 0) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.mood_record_label), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
                            Text(stringResource(R.string.common_streak_days_compact, longestStreak.value), style = MaterialTheme.typography.titleMedium, color = OnBackground, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            if (entries.value.isNotEmpty()) {
                items(entries.value.take(30), key = { it.id }) { entry ->
                    MoodHistoryRow(entry, onDelete = { deleteTarget = entry.id })
                }
            } else {
                item { EmptyListState(TablerIcons.Heart, stringResource(R.string.mood_empty)) }
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
            title = { Text(stringResource(R.string.mood_title), color = OnBackground) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) } },
            snackbarHost = { ScanEatSnackbarHost(snackbarHostState) },
        ) { padding -> content(padding) }
    }

    if (showAdd) {
        AddMoodEntryDialog(
            onDismiss = { showAdd = false },
            onAdd = { mood, stress, notes ->
                viewModel.log(mood, stress, notes)
                showAdd = false
            },
        )
    }

    deleteTarget?.let { targetId ->
        val entryDate = entries.value.find { it.id == targetId }?.date?.toString()
        DeleteConfirmDialog(itemName = entryDate, onConfirm = { viewModel.delete(targetId); deleteTarget = null }, onDismiss = { deleteTarget = null })
    }
}

/**
 * User-requested: "corrélation sommeil/humeur" - see
 * computeSleepMoodCorrelation's own doc comment on why this is a plain
 * good-vs-poor-nights average comparison, not a real correlation
 * coefficient. Only rendered when the ViewModel already found enough paired
 * days on both sides (see MIN_NIGHTS_PER_SIDE) to be a real signal.
 */
@Composable
private fun SleepMoodCorrelationCard(correlation: SleepMoodCorrelation) {
    ScanEatCard(contentPadding = PaddingValues(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(stringResource(R.string.mood_sleep_correlation_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(
                R.string.mood_sleep_correlation_body,
                correlation.goodSleepAvgMood, correlation.goodSleepNights,
                correlation.poorSleepAvgMood, correlation.poorSleepNights,
            ),
            style = MaterialTheme.typography.bodyMedium, color = OnSurface.copy(0.85f),
        )
    }
}

/**
 * User-requested: "lien entre le score des produits scannés (caféine,
 * sucre...) et les entrées sommeil/humeur du même jour" - see
 * computeIntakeSleepMoodLink's own doc comment. Each half (caffeine/sleep,
 * sugar/mood) is shown independently and only when the ViewModel found
 * enough paired days on both sides for that specific half - a month with
 * only the caffeine half usable still shows that one alone.
 */
@Composable
private fun IntakeSleepMoodLinkCard(link: IntakeSleepMoodLink) {
    ScanEatCard(contentPadding = PaddingValues(Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        Text(stringResource(R.string.mood_intake_link_title), style = MaterialTheme.typography.titleSmall, color = OnSurface, fontWeight = FontWeight.SemiBold)
        if (link.highCaffeineDays > 0) {
            Text(
                stringResource(
                    R.string.mood_intake_link_caffeine_sleep,
                    link.highCaffeineAvgSleepQuality, link.highCaffeineDays,
                    link.normalCaffeineAvgSleepQuality, link.normalCaffeineDays,
                ),
                style = MaterialTheme.typography.bodyMedium, color = OnSurface.copy(0.85f),
            )
        }
        if (link.highSugarDays > 0) {
            Text(
                stringResource(
                    R.string.mood_intake_link_sugar_mood,
                    link.highSugarAvgMood, link.highSugarDays,
                    link.normalSugarAvgMood, link.normalSugarDays,
                ),
                style = MaterialTheme.typography.bodyMedium, color = OnSurface.copy(0.85f),
            )
        }
    }
}

@Composable
private fun MoodHistoryRow(entry: MoodEntry, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        color = PrismFillColor,
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
                    stringResource(R.string.mood_today_summary, entry.mood, entry.stress) +
                        (entry.notes.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f),
                )
            }
            IconButton(onClick = onDelete) { Icon(TablerIcons.Trash, stringResource(R.string.common_delete), tint = OnBackground.copy(0.5f)) }
        }
    }
}
