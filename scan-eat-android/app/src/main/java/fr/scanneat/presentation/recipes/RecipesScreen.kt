package fr.scanneat.presentation.recipes

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import fr.scanneat.presentation.recipes.components.ImportRecipeUrlDialog
import fr.scanneat.presentation.recipes.components.LogOfficialRecipeDialog
import fr.scanneat.presentation.recipes.components.LogRecipeDialog
import fr.scanneat.presentation.recipes.components.OfficialRecipeCard
import fr.scanneat.presentation.recipes.components.RecipeCard
import fr.scanneat.presentation.recipes.components.SaveAsTemplateDialog
import fr.scanneat.presentation.recipes.components.ScaleRecipeDialog
import fr.scanneat.presentation.recipes.components.SuggestRecipesDialog
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
    var showAdd by remember { mutableStateOf(false) }
    var showImportUrl by remember { mutableStateOf(false) }
    var showSuggest by remember { mutableStateOf(false) }
    var importPrefill by remember { mutableStateOf<FetchedRecipeResult?>(null) }
    val importState = viewModel.importState.collectAsStateWithLifecycle()
    var logTarget by remember { mutableStateOf<Recipe?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<Recipe?>(null) }
    var notesTarget by remember { mutableStateOf<Recipe?>(null) }
    var scaleTarget by remember { mutableStateOf<Recipe?>(null) }
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

    // Same pattern as WeightScreen - toggleFavorite/save/delete/log/etc. previously
    // called repo's Room writes completely unguarded; a failed write now surfaces
    // here as a one-shot snackbar instead of going back to silent.
    val snackbarHostState = remember { SnackbarHostState() }
    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val logFailedMessage = stringResource(R.string.common_log_failed)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(logFailedMessage)
            viewModel.clearActionFailed()
        }
    }

    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.recipes_title), color = OnBackground) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
        actions = {
            PlanningSwitcherMenu(current = PlanningDestination.RECIPES, onNavigate = onNavigateToPlanning)
            IconButton(onClick = { showSuggest = true }) { Icon(Icons.Default.Lightbulb, stringResource(R.string.recipes_cd_suggest), tint = OnBackground) }
            IconButton(onClick = { showImportUrl = true }) { Icon(Icons.Default.Link, stringResource(R.string.recipes_cd_import_url), tint = OnBackground) }
            IconButton(onClick = {
                photoImportLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        .build()
                )
            }) {
                Icon(Icons.Default.PhotoCamera, stringResource(R.string.recipes_cd_import_photo), tint = OnBackground)
            }
            IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, stringResource(R.string.recipes_cd_new), tint = AccentCoral) }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .ambientGloom(base = Background, primary = AccentCoral, secondary = Gold)
                .padding(horizontal = Spacing.L),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
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
                    if (recipeQuery.value.isBlank() && goalFilter.value == RecipesViewModel.GoalFilter.ALL) {
                        EmptyListState(
                            Icons.Default.RestaurantMenu, stringResource(R.string.recipes_empty_body),
                            ctaLabel = stringResource(R.string.recipes_cd_new), onCta = { showAdd = true },
                        )
                    } else {
                        EmptyListState(Icons.Default.RestaurantMenu, stringResource(R.string.recipes_empty_query, recipeQuery.value))
                    }
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
            showSuggest = false
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
    } else if (showSuggest) {
        SuggestRecipesDialog(
            isLoading    = importState.value is RecipesViewModel.ImportUiState.Loading,
            results      = (importState.value as? RecipesViewModel.ImportUiState.SuggestSuccess)?.results,
            errorMessage = (importState.value as? RecipesViewModel.ImportUiState.Error)?.message,
            onDismiss    = { showSuggest = false; viewModel.clearImportState() },
            onSuggest    = { ingredient -> viewModel.suggestRecipes(ingredient) },
            onPick       = { idea -> viewModel.pickSuggestion(idea) },
        )
    } else {
        // Photo import has no entry dialog of its own (the system photo picker is
        // the whole "input" step) - loading/error feedback for that path surfaces
        // here instead, distinguished from the URL/suggest flows by both being false.
        when (val state = importState.value) {
            is RecipesViewModel.ImportUiState.Loading -> AlertDialog(
                onDismissRequest = {},
                containerColor = SurfaceVariant,
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                        CircularProgressIndicator(color = AccentCoral, modifier = Modifier.size(IconSize.Inline))
                        Text(stringResource(R.string.recipes_import_photo_loading), color = OnBackground)
                    }
                },
                confirmButton = {},
            )
            is RecipesViewModel.ImportUiState.Error -> AlertDialog(
                onDismissRequest = { viewModel.clearImportState() },
                containerColor = SurfaceVariant,
                text = { Text(state.message, color = semanticRed()) },
                confirmButton = { TextButton(onClick = { viewModel.clearImportState() }) { Text(stringResource(R.string.common_ok), color = AccentCoral) } },
            )
            else -> {}
        }
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

