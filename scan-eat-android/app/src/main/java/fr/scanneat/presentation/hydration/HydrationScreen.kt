package fr.scanneat.presentation.hydration

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.scanneat.R
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.health.HYD_GLASS_ML
import fr.scanneat.data.repository.health.HydrationRepository
import fr.scanneat.presentation.reminders.HydrationReminderCard
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import javax.inject.Inject

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
    onOpenCalendar: () -> Unit = {},
) {
    val intake          = viewModel.intake.collectAsStateWithLifecycle()
    val goal            = viewModel.goal.collectAsStateWithLifecycle()
    val streak          = viewModel.streak.collectAsStateWithLifecycle()
    val suggestedGoal   = viewModel.suggestedGoalMl.collectAsStateWithLifecycle()
    val weeklyIntake    = viewModel.weeklyIntake.collectAsStateWithLifecycle()
    val weeklyGoalMetDays = viewModel.weeklyGoalMetDays.collectAsStateWithLifecycle()
    val customGoal      = viewModel.customGoalMl.collectAsStateWithLifecycle()
    var showGoalEditor by remember { mutableStateOf(false) }
    val glasses     = intake.value / HYD_GLASS_ML
    val goalGlasses = goal.value / HYD_GLASS_ML
    val pct         = (intake.value.toFloat() / goal.value.toFloat()).coerceIn(0f, 1.2f)

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

    val content = @Composable { padding: PaddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .ambientGloom(base = Background, primary = HydrationBlue, secondary = AccentCoral)
                .padding(horizontal = Spacing.XL),
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
            item { HydrationSuggestedGoalBanner(suggested) }
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
            item { HydrationWeeklyChart(weeklyIntake = weeklyIntake.value, goalMl = goal.value, weeklyGoalMetDays = weeklyGoalMetDays.value) }
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
            title = { Text(stringResource(R.string.hydration_title), color = OnBackground) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
            snackbarHost = { SnackbarHost(snackbarHostState) },
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
                text.toIntOrNull()?.takeIf { it > 0 }?.let { viewModel.setCustomGoal(it) }
                showGoalEditor = false
            },
        )
    }
}

@Composable
private fun HydrationStreakRow(streakDays: Int, onOpenCalendar: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        // Improvement: 7-day adherence streak badge
        if (streakDays > 0) {
            Surface(shape = RoundedCornerShape(50), color = semanticBlue().copy(0.15f)) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.M, vertical = Spacing.XS),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
                ) {
                    Icon(Icons.Default.LocalFireDepartment, null, tint = semanticBlue(), modifier = Modifier.size(16.dp))
                    // stringResource, not a hardcoded "j" (French "jour") suffix - an
                    // English-language user saw this exact French fragment regardless
                    // of the app's own in-app language setting.
                    Text(
                        stringResource(R.string.common_streak_days_compact, streakDays),
                        style = MaterialTheme.typography.labelMedium,
                        color = semanticBlue(),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        } else {
            Spacer(Modifier.width(1.dp))
        }
        IconButton(onClick = onOpenCalendar) {
            Icon(Icons.Default.CalendarMonth, stringResource(R.string.weight_cd_calendar), tint = OnBackground.copy(0.5f))
        }
    }
}

@Composable
private fun HydrationSuggestedGoalBanner(suggestedGoalMl: Int) {
    Surface(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        color = semanticBlue().copy(0.1f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.M),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            Icon(Icons.Default.TipsAndUpdates, null, tint = semanticBlue(), modifier = Modifier.size(18.dp))
            Text(
                stringResource(R.string.hydration_suggested_goal_hint, suggestedGoalMl),
                style = MaterialTheme.typography.bodySmall,
                color = semanticBlue(),
            )
        }
    }
}

@Composable
private fun HydrationRingAndControls(
    intakeMl: Int,
    goalMl: Int,
    glasses: Int,
    goalGlasses: Int,
    pct: Float,
    onEditGoal: () -> Unit,
    onRemoveGlass: () -> Unit,
    onAddGlass: () -> Unit,
) {
    // Big ring
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .background(
                    Brush.radialGradient(listOf(semanticBlue().copy(alpha = 0.2f), Color.Transparent)),
                    CircleShape,
                ),
        )
        CircularProgressIndicator(
            progress = { pct.coerceIn(0f, 1f) },
            modifier = Modifier.size(180.dp),
            color = semanticBlue(),
            trackColor = SurfaceVariant,
            strokeWidth = 14.dp,
        )
        val editGoalCd = stringResource(R.string.hydration_edit_goal_title)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(onClick = onEditGoal)
                .semantics(mergeDescendants = true) { contentDescription = editGoalCd },
        ) {
            Text("$intakeMl", style = MaterialTheme.typography.headlineLarge, color = semanticBlue(), fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.hydration_goal_ml, goalMl), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f))
                Icon(Icons.Default.Edit, null, tint = OnBackground.copy(0.4f), modifier = Modifier.size(12.dp))
            }
            Text(stringResource(R.string.hydration_glasses_count, glasses, goalGlasses), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
        }
    }

    // Glass grid — filled up to current intake, gold accent past goal
    val totalGlassCells = maxOf(goalGlasses, glasses)
    if (totalGlassCells > 0) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (0 until totalGlassCells).chunked(8).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { i ->
                        val filled = i < glasses
                        val overGoal = i >= goalGlasses
                        Icon(
                            Icons.Default.Opacity,
                            contentDescription = null,
                            tint = when {
                                !filled -> OnBackground.copy(0.15f)
                                overGoal -> Gold
                                else -> semanticBlue()
                            },
                            modifier = Modifier.size(IconSize.Inline),
                        )
                    }
                }
            }
        }
    }

    if (pct >= 1f) {
        Box(Modifier.glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(CardRadius.CONTROL))) {
            Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = semanticGreen().copy(0.15f)) {
                Row(Modifier.padding(Spacing.M), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Icon(Icons.Default.CheckCircle, null, tint = semanticGreen(), modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.hydration_goal_reached), style = MaterialTheme.typography.bodyMedium, color = semanticGreen())
                }
            }
        }
    }

    // Controls
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
        FloatingActionButton(
            onClick = onRemoveGlass,
            containerColor = if (intakeMl > 0) SurfaceVariant else SurfaceVariant.copy(alpha = 0.4f),
            shape = CircleShape,
            modifier = Modifier.size(56.dp),
        ) { Icon(Icons.Default.Remove, stringResource(R.string.common_remove), tint = if (intakeMl > 0) OnSurface else OnSurface.copy(0.3f)) }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.hydration_glass_ml, HYD_GLASS_ML), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.5f))
            Text(stringResource(R.string.hydration_per_glass_label), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.4f))
        }

        FloatingActionButton(
            onClick = onAddGlass,
            containerColor = semanticBlue(),
            shape = CircleShape,
            modifier = Modifier.size(56.dp),
        ) { Icon(Icons.Default.Add, stringResource(R.string.common_add), tint = Color.Black) }
    }

    Text(
        stringResource(R.string.hydration_goal_footer, goalMl),
        style = MaterialTheme.typography.bodySmall,
        color = OnBackground.copy(0.4f),
    )
}

@Composable
private fun HydrationWeeklyChart(weeklyIntake: List<Pair<LocalDate, Int>>, goalMl: Int, weeklyGoalMetDays: Int) {
    val goalMlCoerced = goalMl.coerceAtLeast(1)
    val peak = weeklyIntake.maxOfOrNull { it.second }?.coerceAtLeast(goalMlCoerced) ?: goalMlCoerced
    Surface(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        color = SurfaceVariant.copy(alpha = 0.42f),
        modifier = Modifier.fillMaxWidth().glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(CardRadius.CONTROL), glowAlpha = 0.06f),
    ) {
        Column(Modifier.padding(horizontal = Spacing.M, vertical = Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.hydration_7day_chart_title), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.5f))
                if (weeklyGoalMetDays > 0) {
                    Text(
                        stringResource(R.string.hydration_weekly_goal_met, weeklyGoalMetDays),
                        style = MaterialTheme.typography.labelSmall,
                        color = semanticGreen(),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.XS), verticalAlignment = Alignment.Bottom) {
                weeklyIntake.forEach { (date, ml) ->
                    val frac = (ml.toFloat() / peak).coerceIn(0f, 1f)
                    val isToday = date == java.time.LocalDate.now()
                    val color = when {
                        ml == 0    -> OnBackground.copy(0.08f)
                        ml >= goalMlCoerced -> semanticGreen().copy(if (isToday) 1f else 0.7f)
                        else       -> semanticBlue().copy(if (isToday) 1f else 0.6f)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(if (frac == 0f) 0.05f else frac.coerceAtLeast(0.05f))
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(color),
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                weeklyIntake.forEach { (date, _) ->
                    Text(
                        date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()).replaceFirstChar { it.uppercaseChar() },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (date == java.time.LocalDate.now()) semanticBlue() else OnBackground.copy(0.35f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HydrationGoalEditorDialog(
    initialGoalText: String,
    hasCustomGoal: Boolean,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var goalText by rememberSaveable { mutableStateOf(initialGoalText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant,
        title = { Text(stringResource(R.string.hydration_edit_goal_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                OutlinedTextField(
                    value = goalText,
                    onValueChange = { goalText = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.hydration_goal_ml_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                if (hasCustomGoal) {
                    TextButton(onClick = onReset) {
                        Text(stringResource(R.string.hydration_reset_goal), color = AccentCoral)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(goalText) }) { Text(stringResource(R.string.common_save), color = AccentCoral) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) }
        },
    )
}
