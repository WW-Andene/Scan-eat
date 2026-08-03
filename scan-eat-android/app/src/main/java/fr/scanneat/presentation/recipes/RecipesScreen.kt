package fr.scanneat.presentation.recipes

import compose.icons.tablericons.Plus
import compose.icons.tablericons.Bulb
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.remote.api.ImagePayload
import fr.scanneat.data.repository.planning.*
import fr.scanneat.data.repository.planning.FetchedRecipeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import fr.scanneat.domain.engine.nutrition.OfficialRecipe
import fr.scanneat.domain.engine.nutrition.ProductHints
import fr.scanneat.presentation.recipes.components.AddRecipeDialog
import fr.scanneat.presentation.recipes.components.LogOfficialRecipeDialog
import fr.scanneat.presentation.recipes.components.LogRecipeDialog
import fr.scanneat.presentation.recipes.components.OfficialRecipeCard
import fr.scanneat.presentation.recipes.components.RecipeCard
import fr.scanneat.presentation.recipes.components.RecipesFilterChipsRow
import fr.scanneat.presentation.recipes.components.RecipesImportStateDialogs
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

/**
 * Decodes a gallery-picked photo into the same [ImagePayload] shape Scan already
 * uses, scaled down for upload same as ScanViewModel.toPayload() (no OCR accuracy
 * benefit past a moderate resolution, just wasted bandwidth). No EXIF-orientation
 * correction on the API 26/27 fallback path (MediaStore.Images.Media.getBitmap
 * predates ImageDecoder's automatic handling of it) - a real but minor limitation
 * on a shrinking slice of devices, not a functional failure.
 */
private fun decodeImagePayload(context: android.content.Context, uri: Uri): ImagePayload? = runCatching {
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
            decoder.isMutableRequired = false
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
    val maxPx = 1024
    val scale = maxPx.toFloat() / maxOf(bitmap.width, bitmap.height)
    val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
    if (scaled !== bitmap) bitmap.recycle()
    scaled.recycle()
    ImagePayload(base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
}.getOrNull()

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
    val historyItems = viewModel.historyItems.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var filtersExpanded by remember { mutableStateOf(false) }
    var showImportUrl by remember { mutableStateOf(false) }
    var showSuggest by remember { mutableStateOf(false) }
    var importPrefill by remember { mutableStateOf<FetchedRecipeResult?>(null) }
    val importState = viewModel.importState.collectAsStateWithLifecycle()
    var logTarget by remember { mutableStateOf<Recipe?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<Recipe?>(null) }
    var notesTarget by remember { mutableStateOf<Recipe?>(null) }
    var scaleTarget by remember { mutableStateOf<Recipe?>(null) }
    var editIngredientsTarget by remember { mutableStateOf<Recipe?>(null) }
    var saveAsTemplateTarget by remember { mutableStateOf<Recipe?>(null) }
    var logOfficialTarget by remember { mutableStateOf<OfficialRecipe?>(null) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val photoImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val payload = withContext(Dispatchers.IO) { decodeImagePayload(context, uri) }
                if (payload != null) viewModel.importRecipeFromPhotos(listOf(payload)) else viewModel.photoDecodeFailed()
            }
        }
    }
    // Restaurant menu photo -> estimated dishes (IdentifyMenuRoute.kt), previously
    // unreachable from the app. Mirrors photoImportLauncher exactly - the system photo
    // picker is the whole "input" step, loading/result/error surface via importState,
    // same as the recipe-photo-import path above.
    val menuScanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val payload = withContext(Dispatchers.IO) { decodeImagePayload(context, uri) }
                if (payload != null) viewModel.identifyMenuFromPhotos(listOf(payload)) else viewModel.photoDecodeFailed()
            }
        }
    }

    // Same pattern as WeightScreen - toggleFavorite/save/delete/log/etc. previously
    // called repo's Room writes completely unguarded; a failed write now surfaces
    // here as a one-shot snackbar instead of going back to silent.
    val snackbarHostState = remember { SnackbarHostState() }
    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val logFailedMessage = stringResource(R.string.common_log_failed)
    val recipeDeletedMessage = stringResource(R.string.recipes_deleted_message)
    val recipeScaledMessage = stringResource(R.string.recipes_scaled_message)
    val undoLabel = stringResource(R.string.diary_undo)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(logFailedMessage)
            viewModel.clearActionFailed()
        }
    }

    // cloneOfficial() silently drops macros for any ingredient it can't exact-match
    // against FOOD_DB - a one-shot snackbar surfaces that instead of the clone
    // just quietly looking "complete" with several ingredients actually at 0 macros.
    val cloneUnmatchedCount = viewModel.cloneUnmatchedCount.collectAsStateWithLifecycle()
    val cloneUnmatchedMessage = pluralStringResource(R.plurals.recipes_clone_unmatched, cloneUnmatchedCount.value, cloneUnmatchedCount.value)
    LaunchedEffect(cloneUnmatchedCount.value) {
        if (cloneUnmatchedCount.value > 0) {
            snackbarHostState.showSnackbar(cloneUnmatchedMessage)
            viewModel.clearCloneUnmatchedCount()
        }
    }

    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.recipes_title), color = OnBackground) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) } },
        actions = {
            PlanningSwitcherMenu(current = PlanningDestination.RECIPES, onNavigate = onNavigateToPlanning)
            IconButton(onClick = { showSuggest = true }) { Icon(TablerIcons.Bulb, stringResource(R.string.recipes_cd_suggest), tint = OnBackground) }
            IconButton(onClick = { showImportUrl = true }) { Icon(Icons.Rounded.Link, stringResource(R.string.recipes_cd_import_url), tint = OnBackground) }
            IconButton(onClick = {
                photoImportLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        .build()
                )
            }) {
                Icon(Icons.Rounded.PhotoCamera, stringResource(R.string.recipes_cd_import_photo), tint = OnBackground)
            }
            IconButton(onClick = {
                menuScanLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        .build()
                )
            }) {
                Icon(Icons.Rounded.Restaurant, stringResource(R.string.recipes_cd_menu_scan), tint = OnBackground)
            }
            IconButton(onClick = { showAdd = true }) { Icon(TablerIcons.Plus, stringResource(R.string.recipes_cd_new), tint = AccentCoral) }
        },
        snackbarHost = { ScanEatSnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .ambientGloom(base = Background, primary = AccentCoral, secondary = Gold)
                .padding(horizontal = Spacing.L),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            // "Mes recettes" (search + filters + the user's own recipes) now comes
            // FIRST - was previously below the whole "Recettes officielles" section,
            // so reaching the search field meant scrolling past every official
            // recipe card first (reported: "why is the search bar at the bottom?").
            // The user's own recipes are what they're actively managing/searching;
            // the read-only official reference list is secondary.
            item {
                Text(stringResource(R.string.recipes_title), style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
            }
            item {
                ScanEatSearchField(
                    query = recipeQuery.value, onQueryChange = { viewModel.setRecipeQuery(it) },
                    placeholder = stringResource(R.string.recipes_search_placeholder),
                )
            }
            item {
                RecipesFilterChipsRow(
                    expanded = filtersExpanded, onToggle = { filtersExpanded = !filtersExpanded },
                    goalFilter = goalFilter.value,
                    onFilterChange = { viewModel.setGoalFilter(it) },
                    filtered = recipes.value.size,
                    total = totalRecipesCount.value,
                )
            }

            if (recipes.value.isEmpty()) {
                item {
                    if (recipeQuery.value.isBlank() && goalFilter.value == RecipesViewModel.GoalFilter.ALL) {
                        EmptyListState(
                            Icons.Rounded.RestaurantMenu, stringResource(R.string.recipes_empty_body),
                            ctaLabel = stringResource(R.string.recipes_cd_new), onCta = { showAdd = true },
                        )
                    } else {
                        EmptyListState(Icons.Rounded.RestaurantMenu, stringResource(R.string.recipes_empty_query, recipeQuery.value))
                    }
                }
            }
            items(recipes.value, key = { it.id }) { recipe ->
                RecipeCard(recipe, warning = warnings.value[recipe.id], pairings = pairings.value[recipe.id] ?: emptyList(), hints = hints.value[recipe.id] ?: ProductHints.EMPTY, onLog = { logTarget = recipe }, onDelete = { deleteTarget = recipe.id }, onRename = { renameTarget = recipe }, onEditNotes = { notesTarget = recipe }, onToggleFavorite = { viewModel.toggleFavorite(recipe) }, onScale = { scaleTarget = recipe }, onSaveAsTemplate = { saveAsTemplateTarget = recipe }, onDuplicate = { viewModel.duplicate(recipe) }, onEditIngredients = { editIngredientsTarget = recipe })
            }

            item { Spacer(Modifier.height(Spacing.L)) }

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
            items(viewModel.officialRecipes, key = { it.nameFr }) { recipe ->
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
            showSuggest = false
            showAdd = true
            viewModel.clearImportState()
        }
    }

    RecipesImportStateDialogs(
        showImportUrl = showImportUrl,
        showSuggest = showSuggest,
        importState = importState.value,
        historyItems = historyItems.value,
        onDismissImportUrl = { showImportUrl = false; viewModel.clearImportState() },
        onFetchUrl = { url -> viewModel.importRecipeFromUrl(url) },
        onDismissSuggest = { showSuggest = false; viewModel.clearImportState() },
        onSuggest = { ingredient -> viewModel.suggestRecipes(ingredient) },
        onSuggestFromPantry = { items -> viewModel.suggestFromPantry(items) },
        onPickSuggestion = { idea -> viewModel.pickSuggestion(idea) },
        onClearImportState = { viewModel.clearImportState() },
    )

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
    editIngredientsTarget?.let { recipe ->
        val searchResults = viewModel.ingredientSearchResults.collectAsStateWithLifecycle()
        AddRecipeDialog(
            onDismiss = { editIngredientsTarget = null; viewModel.setIngredientQuery("") },
            onConfirm = { name, comps, servings, notes ->
                viewModel.updateRecipe(recipe, name, comps, servings, notes)
                editIngredientsTarget = null
                viewModel.setIngredientQuery("")
            },
            searchResults = searchResults.value,
            onQueryChange = viewModel::setIngredientQuery,
            initialName = recipe.name,
            initialServings = recipe.servings,
            initialNotes = recipe.notes,
            initialComponents = recipe.components,
            isEdit = true,
        )
    }
    logTarget?.let { LogRecipeDialog(recipe = it, onDismiss = { logTarget = null }, onLog = { slot, frac -> viewModel.log(it, slot, frac); logTarget = null }) }
    logOfficialTarget?.let { recipe ->
        LogOfficialRecipeDialog(recipe = recipe, isFrench = language.value == "fr", onDismiss = { logOfficialTarget = null }, onLog = { slot, portionFraction -> viewModel.logOfficial(recipe, slot, portionFraction); logOfficialTarget = null })
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
            onConfirm = { newServings ->
                viewModel.scale(recipe, newServings)
                scaleTarget = null
                // scale() permanently overwrites every stored gram/kcal/macro with no
                // confirmation step (unlike delete, which already had DeleteConfirmDialog
                // + undo) - a fat-fingered serving count had no way back until now.
                coroutineScope.launch {
                    val result = snackbarHostState.showSnackbar(recipeScaledMessage, actionLabel = undoLabel)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoScale()
                }
            },
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
        DeleteConfirmDialog(
            itemName = name,
            onConfirm = {
                viewModel.delete(id)
                deleteTarget = null
                coroutineScope.launch {
                    val result = snackbarHostState.showSnackbar(recipeDeletedMessage, actionLabel = undoLabel)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }

}


