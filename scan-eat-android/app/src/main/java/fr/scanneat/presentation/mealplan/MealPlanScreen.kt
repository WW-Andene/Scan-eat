package fr.scanneat.presentation.mealplan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.repository.planning.*
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MEALS = listOf("breakfast", "lunch", "dinner", "snack")

@Composable
fun MealPlanScreen(
    viewModel: MealPlanViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val plan = viewModel.weekPlan.collectAsStateWithLifecycle()
    val weekDates = viewModel.weekDates.collectAsStateWithLifecycle()
    val language = viewModel.language.collectAsStateWithLifecycle()
    val recipes = viewModel.recipes.collectAsStateWithLifecycle()
    val templates = viewModel.templates.collectAsStateWithLifecycle()
    val dayCalories = viewModel.dayCalories.collectAsStateWithLifecycle()
    val gapSuggestions = viewModel.gapSuggestions.collectAsStateWithLifecycle()
    val weeklyTotalKcal = viewModel.weeklyTotalKcal.collectAsStateWithLifecycle()
    // In-app language can differ from device locale - ofPattern() alone would
    // default to Locale.getDefault() and could show day names in the wrong language.
    val dayFmt = remember(language.value) { DateTimeFormatter.ofPattern("EEE d", Locale(language.value)) }
    // (date, meal) of the slot currently being assigned a recipe/template, or null.
    var assignTarget by remember { mutableStateOf<Pair<LocalDate, String>?>(null) }
    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // logSlot previously failed completely silently - see MealPlanViewModel
    // .actionFailed's own comment.
    val logFailedMessage = stringResource(R.string.common_log_failed)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(logFailedMessage)
            viewModel.clearActionFailed()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.mealplan_title), color = OnBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
                // Repeating a whole week's plan previously meant re-assigning all 28
                // slots (7 days x 4 meals) by hand - this duplicates the displayed
                // week onto the next 7 days in one tap.
                actions = {
                    if (weeklyTotalKcal.value > 0) {
                        IconButton(onClick = { viewModel.duplicateWeek() }) {
                            Icon(Icons.Default.ContentCopy, stringResource(R.string.mealplan_duplicate_week), tint = OnBackground)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            item { Spacer(Modifier.height(Spacing.XS)) }
            if (weeklyTotalKcal.value > 0) {
                item {
                    Surface(
                        shape = RoundedCornerShape(CardRadius.CONTROL),
                        color = AccentCoral.copy(0.08f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.M, vertical = Spacing.S),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.mealplan_weekly_kcal_title), style = MaterialTheme.typography.labelMedium, color = OnBackground.copy(0.7f))
                            Text(stringResource(R.string.mealplan_weekly_kcal_value, weeklyTotalKcal.value), style = MaterialTheme.typography.labelMedium, color = AccentCoral, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            items(weekDates.value, key = { it.toEpochDay() }) { date ->
                val dayPlan = plan.value[date] ?: DayPlan(date)
                val isToday = date == LocalDate.now()
                val kcal = dayCalories.value[date] ?: 0
                val suggestion = gapSuggestions.value[date]
                Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(
                                    if (isToday) stringResource(R.string.mealplan_day_today, date.format(dayFmt)) else date.format(dayFmt),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isToday) AccentCoral else OnSurface,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (kcal > 0) Text(stringResource(R.string.mealplan_day_kcal, kcal), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f))
                            }
                            // MealPlanRepository.clearDay() previously had no UI entry
                            // point - clearing a fully-planned day took one tap per
                            // meal slot instead of one action for the whole day.
                            if (MEALS.any { dayPlan[it] != null }) {
                                Row {
                                    // Repeating a day (e.g. "same lunch as today, next
                                    // Tuesday") previously meant re-assigning each slot by
                                    // hand - this copies the whole day onto the same
                                    // weekday next week.
                                    IconButton(onClick = { viewModel.duplicateDay(date) }) {
                                        Icon(Icons.Default.ContentCopy, stringResource(R.string.mealplan_duplicate_day), tint = OnSurface.copy(0.4f), modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = { viewModel.clearDay(date) }) {
                                        Icon(Icons.Default.Close, stringResource(R.string.mealplan_clear_day), tint = OnSurface.copy(0.4f), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        MEALS.forEach { meal ->
                            val slot = dayPlan[meal]
                            MealPlanRow(
                                meal  = mealLabel(meal),
                                slot  = slot,
                                onEdit = { text -> viewModel.setNote(date, meal, text) },
                                onClear = { viewModel.clear(date, meal) },
                                onAssign = { assignTarget = date to meal },
                                onLog = { s -> viewModel.logSlot(date, meal, s) },
                            )
                        }
                        if (suggestion != null) {
                            HorizontalDivider(color = OnSurface.copy(0.07f))
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                            ) {
                                Icon(Icons.Default.Lightbulb, null, tint = AccentCoral, modifier = Modifier.size(14.dp))
                                Text(
                                    stringResource(R.string.mealplan_gap_suggestion, suggestion.name, suggestion.totalKcal.toInt(), suggestion.totalProteinG.toInt()),
                                    style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.6f), modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }

    assignTarget?.let { (date, meal) ->
        AssignSlotDialog(
            mealLabel = mealLabel(meal),
            recipes   = recipes.value,
            templates = templates.value,
            onPickRecipe   = { viewModel.setRecipe(date, meal, it); assignTarget = null },
            onPickTemplate = { viewModel.setTemplate(date, meal, it); assignTarget = null },
            onDismiss = { assignTarget = null },
        )
    }
}

@Composable
private fun mealLabel(key: String): String = when (key) {
    "breakfast" -> stringResource(R.string.meal_breakfast)
    "lunch"     -> stringResource(R.string.meal_lunch)
    "dinner"    -> stringResource(R.string.meal_dinner)
    "snack"     -> stringResource(R.string.meal_snack)
    else        -> key
}

@Composable
private fun MealPlanRow(meal: String, slot: MealPlanSlot?, onEdit: (String) -> Unit, onClear: () -> Unit, onAssign: () -> Unit, onLog: (MealPlanSlot) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(slot) { mutableStateOf((slot as? MealPlanSlot.NoteSlot)?.text ?: "") }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Text(meal, style = MaterialTheme.typography.labelMedium, color = OnSurface.copy(0.6f), modifier = Modifier.width(72.dp))
        if (editing) {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                modifier = Modifier.weight(1f), singleLine = true,
                colors = scanEatTextFieldColors(),
            )
            // IconButtons left at their default 48dp touch target (Material/WCAG
            // minimum) below - a UI/UX audit found this row forcing every control
            // to 32dp. The inner Icon's own smaller size keeps the glyph compact.
            IconButton(onClick = { onEdit(text); editing = false }) {
                Icon(Icons.Default.Check, stringResource(R.string.common_ok), tint = AccentCoral, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { editing = false }) {
                Icon(Icons.Default.Close, stringResource(R.string.common_cancel), tint = OnSurface.copy(0.4f), modifier = Modifier.size(16.dp))
            }
        } else {
            val label = when (slot) {
                is MealPlanSlot.NoteSlot     -> slot.text
                is MealPlanSlot.RecipeSlot   -> stringResource(R.string.mealplan_recipe_prefix, slot.name)
                is MealPlanSlot.TemplateSlot -> stringResource(R.string.mealplan_template_prefix, slot.name)
                null -> stringResource(R.string.common_dash)
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when (slot) {
                    is MealPlanSlot.RecipeSlot   -> Icon(Icons.Default.RestaurantMenu, null, tint = OnSurface.copy(0.4f), modifier = Modifier.size(14.dp))
                    is MealPlanSlot.TemplateSlot -> Icon(Icons.AutoMirrored.Filled.ListAlt, null, tint = OnSurface.copy(0.4f), modifier = Modifier.size(14.dp))
                    else -> {}
                }
                Text(label, style = MaterialTheme.typography.bodySmall, color = if (slot != null) OnSurface else OnSurface.copy(0.3f))
            }
            // Editing as free text only makes sense for a note (or an empty slot) — a
            // Recipe/Template assignment has no text to edit, and the text field always
            // started blank for them, so confirming it used to silently wipe the
            // assignment. Recipe/Template slots use the clear (X) button to remove instead.
            if (slot == null || slot is MealPlanSlot.NoteSlot) {
                IconButton(onClick = { editing = true }) {
                    Icon(Icons.Default.Edit, stringResource(R.string.common_edit), tint = OnSurface.copy(0.4f), modifier = Modifier.size(16.dp))
                }
            }
            // A planned Recipe/Template slot previously only ever persisted the plan
            // itself — nothing connected it to the diary, so the day arrived and the
            // plan stayed purely decorative. Only meaningful once a real recipe/
            // template is assigned; a note has no nutrition to log.
            if (slot is MealPlanSlot.RecipeSlot || slot is MealPlanSlot.TemplateSlot) {
                IconButton(onClick = { onLog(slot) }) {
                    Icon(Icons.Default.Add, stringResource(R.string.common_log), tint = AccentCoral, modifier = Modifier.size(18.dp))
                }
            }
            // Lets a saved Recipe/Template actually be planned onto this slot — until
            // now MealPlanSlot.RecipeSlot/TemplateSlot could only ever be produced by
            // deserializing a backup, never by anything reachable from the UI.
            IconButton(onClick = onAssign) {
                Icon(Icons.Default.RestaurantMenu, stringResource(R.string.mealplan_assign_cd), tint = OnSurface.copy(0.4f), modifier = Modifier.size(16.dp))
            }
            if (slot != null) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, stringResource(R.string.common_clear), tint = OnSurface.copy(0.3f), modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

// ============================================================================
// FEATURE: assign a saved Recipe or Template to a meal-plan slot. Previously
// the only way to populate a day's plan was a free-text note — the recipe/
// template picker (and the model support behind it — MealPlanSlot.RecipeSlot/
// TemplateSlot, MealPlanRepository's "recipe"/"template" serialization kinds,
// MealPlanViewModel's orphan-pruning safeguard) already existed one layer
// down but had no UI entry point to actually create one.
// ============================================================================
@Composable
private fun AssignSlotDialog(
    mealLabel: String,
    recipes: List<Recipe>,
    templates: List<MealTemplate>,
    onPickRecipe: (Recipe) -> Unit,
    onPickTemplate: (MealTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = SurfaceVariant,
        title = { Text(stringResource(R.string.mealplan_assign_title, mealLabel), color = OnBackground) },
        text = {
            if (recipes.isEmpty() && templates.isEmpty()) {
                Text(stringResource(R.string.mealplan_assign_empty), color = OnBackground.copy(0.6f), style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    if (recipes.isNotEmpty()) {
                        item { Text(stringResource(R.string.recipes_title), style = MaterialTheme.typography.labelMedium, color = AccentCoral) }
                        items(recipes, key = { "r_${it.id}" }) { recipe ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onPickRecipe(recipe) }.padding(vertical = Spacing.S),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                            ) {
                                Icon(Icons.Default.RestaurantMenu, null, tint = OnSurface.copy(0.5f), modifier = Modifier.size(16.dp))
                                Text(recipe.name, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                            }
                        }
                    }
                    if (templates.isNotEmpty()) {
                        item { Text(stringResource(R.string.templates_title), style = MaterialTheme.typography.labelMedium, color = AccentCoral) }
                        items(templates, key = { "t_${it.id}" }) { template ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onPickTemplate(template) }.padding(vertical = Spacing.S),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ListAlt, null, tint = OnSurface.copy(0.5f), modifier = Modifier.size(16.dp))
                                Text(template.name, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
    )
}
