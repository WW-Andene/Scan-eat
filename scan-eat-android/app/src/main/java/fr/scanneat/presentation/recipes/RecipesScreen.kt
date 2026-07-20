package fr.scanneat.presentation.recipes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.repository.planning.*
import fr.scanneat.data.repository.scan.FetchedRecipeResult
import fr.scanneat.domain.engine.nutrition.OfficialRecipe
import fr.scanneat.domain.engine.nutrition.ProductHints
import fr.scanneat.presentation.recipes.components.AddRecipeDialog
import fr.scanneat.presentation.recipes.components.ImportRecipeUrlDialog
import fr.scanneat.presentation.recipes.components.LogOfficialRecipeDialog
import fr.scanneat.presentation.recipes.components.LogRecipeDialog
import fr.scanneat.presentation.recipes.components.OfficialRecipeCard
import fr.scanneat.presentation.recipes.components.RecipeCard
import fr.scanneat.presentation.recipes.components.SaveAsTemplateDialog
import fr.scanneat.presentation.recipes.components.ScaleRecipeDialog
import fr.scanneat.presentation.shell.PlanningDestination
import fr.scanneat.presentation.shell.PlanningSwitcherMenu
import fr.scanneat.presentation.ui.theme.*

/**
 * FetchedRecipeResult's ingredients/steps are free text (no gram weight to build a real
 * RecipeComponent from — see that class's own doc comment), so an imported recipe's full
 * detail lands here as pre-filled notes text instead of being silently dropped, while the
 * user adds macro-tracked ingredients via the normal search flow in AddRecipeDialog.
 */
@Composable
private fun formatImportedNotes(result: FetchedRecipeResult): String = buildString {
    if (result.ingredients.isNotEmpty()) {
        appendLine(stringResource(R.string.recipes_import_notes_ingredients))
        result.ingredients.forEach { appendLine("- $it") }
        appendLine()
    }
    if (result.steps.isNotEmpty()) {
        appendLine(stringResource(R.string.recipes_import_notes_steps))
        result.steps.forEachIndexed { i, step -> appendLine("${i + 1}. $step") }
        appendLine()
    }
    result.cookTimeMinutes?.let { appendLine(stringResource(R.string.recipes_import_notes_cook_time, it)) }
    val kcal = result.kcal
    if (kcal != null) {
        appendLine(stringResource(
            R.string.recipes_import_notes_nutrition,
            kcal.toInt(), (result.proteinG ?: 0.0).toInt(), (result.carbsG ?: 0.0).toInt(), (result.fatG ?: 0.0).toInt(),
        ))
    }
    if (result.sourceUrl.isNotBlank()) appendLine(stringResource(R.string.recipes_import_notes_source, result.sourceUrl))
}.trim()

@Composable
fun RecipesScreen(
    viewModel: RecipesViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onNavigateToPlanning: (PlanningDestination) -> Unit = {},
) {
    val recipes = viewModel.recipes.collectAsStateWithLifecycle()
    val language = viewModel.language.collectAsStateWithLifecycle()
    val warnings = viewModel.recipeWarnings.collectAsStateWithLifecycle()
    val officialWarnings = viewModel.officialRecipeWarnings.collectAsStateWithLifecycle()
    val pairings = viewModel.recipePairings.collectAsStateWithLifecycle()
    val hints = viewModel.recipeHints.collectAsStateWithLifecycle()
    val officialHints = viewModel.officialRecipeHints.collectAsStateWithLifecycle()
    val goalFilter = viewModel.goalFilter.collectAsStateWithLifecycle()
    val recipeQuery = viewModel.recipeQuery.collectAsStateWithLifecycle()
    val totalRecipesCount = viewModel.totalRecipesCount.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var showImportUrl by remember { mutableStateOf(false) }
    var importPrefill by remember { mutableStateOf<FetchedRecipeResult?>(null) }
    val importState = viewModel.importState.collectAsStateWithLifecycle()
    var logTarget by remember { mutableStateOf<Recipe?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<Recipe?>(null) }
    var notesTarget by remember { mutableStateOf<Recipe?>(null) }
    var scaleTarget by remember { mutableStateOf<Recipe?>(null) }
    var saveAsTemplateTarget by remember { mutableStateOf<Recipe?>(null) }
    var logOfficialTarget by remember { mutableStateOf<OfficialRecipe?>(null) }

    Scaffold(
        topBar = {
            FloatingTopBar(
                title = { Text(stringResource(R.string.recipes_title), color = OnBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
                actions = {
                    PlanningSwitcherMenu(current = PlanningDestination.RECIPES, onNavigate = onNavigateToPlanning)
                    IconButton(onClick = { showImportUrl = true }) { Icon(Icons.Default.Link, stringResource(R.string.recipes_cd_import_url), tint = OnBackground) }
                    IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, stringResource(R.string.recipes_cd_new), tint = AccentCoral) }
                },
            )
        },
        containerColor = Background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
                .ambientGloom(base = Background, primary = AccentCoral, secondary = Gold)
                .padding(horizontal = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // ---- Official starter recipes — real CIQUAL/ANSES-sourced nutrition,
            // built to the Santé publique France / PNNS "assiette-type" portion
            // model. Read-only: log them directly, or clone into an editable
            // recipe of your own. See OfficialRecipeDb.kt for full provenance. ----
            item {
                Text(stringResource(R.string.recipes_official_section_title), style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
            }
            item {
                Text(stringResource(R.string.recipes_official_section_hint), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
            }
            items(viewModel.officialRecipes) { recipe ->
                OfficialRecipeCard(
                    recipe   = recipe,
                    isFrench = language.value == "fr",
                    warning  = officialWarnings.value[recipe.nameFr],
                    pairings = viewModel.officialRecipePairings[recipe.nameFr] ?: emptyList(),
                    hints    = officialHints.value[recipe.nameFr] ?: ProductHints.EMPTY,
                    onLog    = { logOfficialTarget = recipe },
                    onClone  = { viewModel.cloneOfficial(recipe) },
                )
            }
            item { Spacer(Modifier.height(Spacing.M)) }
            item {
                Text(stringResource(R.string.recipes_title), style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
            }
            item {
                // Recipes previously had no way to search by name - only the macro-based
                // filter chips below - unlike History/CustomFood's real text search.
                OutlinedTextField(
                    value = recipeQuery.value,
                    onValueChange = { viewModel.setRecipeQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.recipes_search_placeholder), color = OnBackground.copy(0.4f)) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = OnBackground.copy(0.5f)) },
                    trailingIcon = {
                        if (recipeQuery.value.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setRecipeQuery("") }) {
                                Icon(Icons.Default.Close, stringResource(R.string.common_clear_search), tint = OnBackground.copy(0.5f))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    colors = scanEatTextFieldColors(),
                )
            }
            item {
                val filterOptions = listOf(
                    RecipesViewModel.GoalFilter.ALL         to stringResource(R.string.recipes_filter_all),
                    RecipesViewModel.GoalFilter.HIGH_PROTEIN to stringResource(R.string.recipes_filter_high_protein),
                    RecipesViewModel.GoalFilter.LOW_CARB    to stringResource(R.string.recipes_filter_low_carb),
                    RecipesViewModel.GoalFilter.LOW_FAT     to stringResource(R.string.recipes_filter_low_fat),
                )
                val total = totalRecipesCount.value
                val filtered = recipes.value.size
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                        items(filterOptions) { (filter, label) ->
                            FilterChip(
                                selected = goalFilter.value == filter,
                                onClick = { viewModel.setGoalFilter(filter) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AccentCoral.copy(0.2f), selectedLabelColor = AccentCoral),
                            )
                        }
                    }
                    if (goalFilter.value != RecipesViewModel.GoalFilter.ALL && total > 0) {
                        Text(
                            stringResource(R.string.recipes_filter_results, filtered, total),
                            style = MaterialTheme.typography.labelSmall,
                            color = OnBackground.copy(0.5f),
                        )
                    }
                }
            }

            if (recipes.value.isEmpty()) {
                item {
                    EmptyListState(
                        Icons.Default.RestaurantMenu, stringResource(R.string.recipes_empty_body),
                        ctaLabel = stringResource(R.string.recipes_cd_new), onCta = { showAdd = true },
                    )
                }
            }
            items(recipes.value, key = { it.id }) { recipe ->
                RecipeCard(recipe, warning = warnings.value[recipe.id], pairings = pairings.value[recipe.id] ?: emptyList(), hints = hints.value[recipe.id] ?: ProductHints.EMPTY, onLog = { logTarget = recipe }, onDelete = { deleteTarget = recipe.id }, onRename = { renameTarget = recipe }, onEditNotes = { notesTarget = recipe }, onToggleFavorite = { viewModel.toggleFavorite(recipe) }, onScale = { scaleTarget = recipe }, onSaveAsTemplate = { saveAsTemplateTarget = recipe })
            }
            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }

    // A successful URL import hands off straight into the same AddRecipeDialog a manual
    // "+" tap opens, pre-filled - one review-before-save step, not a silent auto-save of
    // unverified external page content.
    LaunchedEffect(importState.value) {
        val state = importState.value
        if (state is RecipesViewModel.ImportUiState.Success) {
            importPrefill = state.result
            showImportUrl = false
            showAdd = true
            viewModel.clearImportState()
        }
    }

    if (showImportUrl) {
        ImportRecipeUrlDialog(
            isLoading    = importState.value is RecipesViewModel.ImportUiState.Loading,
            errorMessage = (importState.value as? RecipesViewModel.ImportUiState.Error)?.message,
            onDismiss    = { showImportUrl = false; viewModel.clearImportState() },
            onFetch      = { url -> viewModel.importRecipeFromUrl(url) },
        )
    }

    if (showAdd) {
        val searchResults = viewModel.ingredientSearchResults.collectAsStateWithLifecycle()
        val prefill = importPrefill
        AddRecipeDialog(
            onDismiss = { showAdd = false; importPrefill = null },
            onConfirm = { name, comps, servings, notes -> viewModel.save(name, comps, servings, notes); showAdd = false; importPrefill = null },
            searchResults = searchResults.value,
            onQueryChange = viewModel::setIngredientQuery,
            initialName     = prefill?.name ?: "",
            initialServings = prefill?.servings?.toIntOrNull() ?: 1,
            initialNotes    = prefill?.let { formatImportedNotes(it) } ?: "",
        )
    }
    logTarget?.let { LogRecipeDialog(recipe = it, onDismiss = { logTarget = null }, onLog = { slot, frac -> viewModel.log(it, slot, frac); logTarget = null }) }
    logOfficialTarget?.let { recipe ->
        LogOfficialRecipeDialog(recipe = recipe, isFrench = language.value == "fr", onDismiss = { logOfficialTarget = null }, onLog = { slot -> viewModel.logOfficial(recipe, slot); logOfficialTarget = null })
    }
    renameTarget?.let { recipe ->
        RenameDialog(
            currentName = recipe.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName -> viewModel.rename(recipe, newName); renameTarget = null },
        )
    }

    notesTarget?.let { recipe ->
        EditNotesDialog(
            title = stringResource(R.string.recipes_field_notes),
            currentNotes = recipe.notes,
            onDismiss = { notesTarget = null },
            onConfirm = { notes -> viewModel.updateNotes(recipe, notes); notesTarget = null },
        )
    }

    scaleTarget?.let { recipe ->
        ScaleRecipeDialog(
            currentServings = recipe.servings,
            onDismiss = { scaleTarget = null },
            onConfirm = { newServings -> viewModel.scale(recipe, newServings); scaleTarget = null },
        )
    }

    saveAsTemplateTarget?.let { recipe ->
        SaveAsTemplateDialog(
            recipe = recipe,
            onDismiss = { saveAsTemplateTarget = null },
            onConfirm = { meal -> viewModel.saveAsTemplate(recipe, meal); saveAsTemplateTarget = null },
        )
    }

    deleteTarget?.let { id ->
        val name = recipes.value.find { it.id == id }?.name
        DeleteConfirmDialog(itemName = name, onConfirm = { viewModel.delete(id); deleteTarget = null }, onDismiss = { deleteTarget = null })
    }

}

