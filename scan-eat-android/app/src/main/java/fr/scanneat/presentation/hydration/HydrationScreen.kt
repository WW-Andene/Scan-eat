package fr.scanneat.presentation.hydration

import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.repository.health.HYD_GLASS_ML
import fr.scanneat.presentation.hydration.components.HydrationGoalEditorDialog
import fr.scanneat.presentation.hydration.components.HydrationHistorySection
import fr.scanneat.presentation.hydration.components.HydrationRingAndControls
import fr.scanneat.presentation.hydration.components.HydrationStreakRow
import fr.scanneat.presentation.hydration.components.HydrationSuggestedGoalBanner
import fr.scanneat.presentation.hydration.components.HydrationWeeklyChart
import fr.scanneat.presentation.reminders.HydrationReminderCard
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * [embedded] = true skips this screen's own Scaffold/TopAppBar — used when
 * hosted as a Journal sub-tab, where the tab row itself is the header and a
 * second nested app bar (with a dead-end back arrow) would be redundant
 * chrome. Standalone push-navigation callers leave it false.
 */
@Composable
fun HydrationScreen(
    viewModel: HydrationViewModel = hiltViewModel(),
    onBack: () -> Unit,
    embedded: Boolean = false,
    // Only meaningful when [embedded] — the host (DiaryScreen) supplies this so
    // this screen's own LazyColumn reserves the same floating-bottom-nav
    // clearance the host itself is already reserving.
    embeddedBottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    // Same reasoning as embeddedBottomPadding, for the host's floating header -
    // fed into this screen's own LazyColumn contentPadding so scrolled items can
    // still scroll up into the region behind the header instead of that region
    // staying permanently empty.
    embeddedTopPadding: androidx.compose.ui.unit.Dp = 0.dp,
    onOpenCalendar: () -> Unit = {},
) {
    val intake          = viewModel.intake.collectAsStateWithLifecycle()
    val goal            = viewModel.goal.collectAsStateWithLifecycle()
    val streak          = viewModel.streak.collectAsStateWithLifecycle()
    val suggestedGoal   = viewModel.suggestedGoalMl.collectAsStateWithLifecycle()
    val weeklyIntake    = viewModel.weeklyIntake.collectAsStateWithLifecycle()
    val weeklyGoalMetDays = viewModel.weeklyGoalMetDays.collectAsStateWithLifecycle()
    val language        = viewModel.language.collectAsStateWithLifecycle()
    val customGoal      = viewModel.customGoalMl.collectAsStateWithLifecycle()
    val history         = viewModel.history.collectAsStateWithLifecycle()
    val useImperial     = viewModel.useImperial.collectAsStateWithLifecycle()
    var showGoalEditor by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<LocalDate?>(null) }
    val glasses     = intake.value / HYD_GLASS_ML
    val goalGlasses = goal.value / HYD_GLASS_ML
    val pct         = (intake.value.toFloat() / goal.value.toFloat()).coerceIn(0f, 1.2f)
    // In-app language, not Locale.getDefault() - same fix every date-heavy
    // sibling screen (Weight/Expenses/Diary/...) already applies.
    val dateFmt = remember(language.value) { DateTimeFormatter.ofPattern("d MMM yyyy", Locale(language.value)) }

    // addGlass()/removeGlass() previously failed completely silently - see
    // HydrationViewModel.actionFailed's own comment.
    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val logFailedMessage = stringResource(R.string.common_log_failed)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(logFailedMessage)
            viewModel.clearActionFailed()
        }
    }

    // Same CsvExportReady-then-SAF-picker split ExpensesScreen's own CSV export
    // uses - reachable directly from this screen instead of only via Settings >
    // Sauvegarde (which already calls the same csvExportRepository.exportHydrationCsv()).
    val context = LocalContext.current
    val csvExportReady = viewModel.csvExportReady.collectAsStateWithLifecycle()
    val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val csv = csvExportReady.value
        when {
            uri == null -> viewModel.clearCsvExport()
            csv != null -> {
                val stream = context.contentResolver.openOutputStream(uri)
                val wrote = stream != null && runCatching { stream.use { it.write(csv.toByteArray()) } }.isSuccess
                if (wrote) viewModel.clearCsvExport() else viewModel.reportCsvExportIoFailed()
            }
            else -> viewModel.reportCsvExportIoFailed()
        }
    }
    LaunchedEffect(csvExportReady.value) {
        if (csvExportReady.value != null) {
            csvExportLauncher.launch("scaneat-hydratation-${LocalDate.now()}.csv")
        }
    }

    val content = @Composable { padding: PaddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .ambientGloom(base = Background, primary = AccentCoral, secondary = Gold)
                .padding(horizontal = Spacing.L),
            contentPadding = padding,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
        item { Spacer(Modifier.height(Spacing.L)) }

        // Previously an inline single-domain MonthCalendar toggled here; now
        // routes to the unified Calendar (Dashboard), which shows hydration
        // alongside every other tracker.
        item { HydrationStreakRow(streakDays = streak.value, onOpenCalendar = onOpenCalendar) }

        // New: smart goal suggestion banner
        suggestedGoal.value?.let { suggested ->
            item { HydrationSuggestedGoalBanner(suggested, onApply = { viewModel.setCustomGoal(it) }) }
        }

        item {
            HydrationRingAndControls(
                intakeMl = intake.value, goalMl = goal.value, glasses = glasses, goalGlasses = goalGlasses, pct = pct,
                onEditGoal = { showGoalEditor = true },
                onRemoveGlass = { if (intake.value > 0) viewModel.removeGlass() },
                onAddGlass = { viewModel.addGlass() },
            )
        }

        item { HydrationReminderCard() }

        // 7-day intake chart
        if (weeklyIntake.value.isNotEmpty()) {
            item { HydrationWeeklyChart(weeklyIntake = weeklyIntake.value, goalMl = goal.value, weeklyGoalMetDays = weeklyGoalMetDays.value, language = language.value) }
        }

        if (history.value.isNotEmpty()) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.hydration_history_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = OnBackground,
                        fontWeight = FontWeight.SemiBold,
                    )
                    // Reachable directly from this screen instead of only via
                    // Settings > Sauvegarde, same rationale as ExpensesScreen's
                    // own export shortcut.
                    IconButton(onClick = { viewModel.prepareCsvExport() }) {
                        Icon(Icons.Rounded.Download, stringResource(R.string.hydration_export_csv), tint = OnSurface.copy(0.5f))
                    }
                }
            }
            item {
                HydrationHistorySection(
                    history = history.value,
                    dateFmt = dateFmt,
                    useImperial = useImperial.value,
                    onEdit = { date, ml -> viewModel.editDay(date, ml) },
                    onDelete = { date -> deleteTarget = date },
                )
            }
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
            title = { Text(stringResource(R.string.hydration_title), color = OnBackground) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) } },
            snackbarHost = { ScanEatSnackbarHost(snackbarHostState) },
        ) { padding -> content(padding) }
    }

    // Goal was previously purely formula-derived (sex/activity/health conditions)
    // with no way to override it - e.g. a doctor-recommended target that doesn't
    // match the EFSA formula. Weight already has this exact capability via its
    // own explicit goalWeightKg profile field.
    if (showGoalEditor) {
        HydrationGoalEditorDialog(
            initialGoalText = (customGoal.value ?: goal.value).toString(),
            hasCustomGoal = customGoal.value != null,
            onDismiss = { showGoalEditor = false },
            onReset = { viewModel.setCustomGoal(null); showGoalEditor = false },
            onConfirm = { text ->
                text.toIntOrNull()?.takeIf { it > 0 }?.coerceIn(1, 10000)?.let { viewModel.setCustomGoal(it) }
                showGoalEditor = false
            },
        )
    }

    deleteTarget?.let { date ->
        DeleteConfirmDialog(
            itemName = date.format(dateFmt),
            onConfirm = { viewModel.deleteDay(date); deleteTarget = null },
            onDismiss = { deleteTarget = null },
        )
    }
}
