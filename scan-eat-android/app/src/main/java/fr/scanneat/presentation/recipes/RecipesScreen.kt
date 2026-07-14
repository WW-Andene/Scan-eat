package fr.scanneat.presentation.recipes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import fr.scanneat.domain.engine.nutrition.OfficialRecipe
import fr.scanneat.presentation.recipes.components.AddRecipeDialog
import fr.scanneat.presentation.recipes.components.LogOfficialRecipeDialog
import fr.scanneat.presentation.recipes.components.LogRecipeDialog
import fr.scanneat.presentation.recipes.components.OfficialRecipeCard
import fr.scanneat.presentation.recipes.components.RecipeCard
import fr.scanneat.presentation.ui.theme.*

@Composable
fun RecipesScreen(
    viewModel: RecipesViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val recipes = viewModel.recipes.collectAsStateWithLifecycle()
    val language = viewModel.language.collectAsStateWithLifecycle()
    val warnings = viewModel.recipeWarnings.collectAsStateWithLifecycle()
    val officialWarnings = viewModel.officialRecipeWarnings.collectAsStateWithLifecycle()
    val pairings = viewModel.recipePairings.collectAsStateWithLifecycle()
    val goalFilter = viewModel.goalFilter.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var logTarget by remember { mutableStateOf<Recipe?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<Recipe?>(null) }
    var logOfficialTarget by remember { mutableStateOf<OfficialRecipe?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipes_title), color = OnBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
                actions = { IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, stringResource(R.string.recipes_cd_new), tint = AccentCoral) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.L),
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
                    onLog    = { logOfficialTarget = recipe },
                    onClone  = { viewModel.cloneOfficial(recipe) },
                )
            }
            item { Spacer(Modifier.height(Spacing.M)) }
            item {
                Text(stringResource(R.string.recipes_title), style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold)
            }
            item {
                val filterOptions = listOf(
                    RecipesViewModel.GoalFilter.ALL to "Tous",
                    RecipesViewModel.GoalFilter.HIGH_PROTEIN to "Riche protéines",
                    RecipesViewModel.GoalFilter.LOW_CARB to "Low-carb",
                    RecipesViewModel.GoalFilter.LOW_FAT to "Faible en gras",
                )
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
                RecipeCard(recipe, warning = warnings.value[recipe.id], pairings = pairings.value[recipe.id] ?: emptyList(), onLog = { logTarget = recipe }, onDelete = { deleteTarget = recipe.id }, onRename = { renameTarget = recipe })
            }
            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }

    if (showAdd) {
        val searchResults = viewModel.ingredientSearchResults.collectAsStateWithLifecycle()
        AddRecipeDialog(
            onDismiss = { showAdd = false },
            onSave = { name, comps, servings -> viewModel.save(name, comps, servings); showAdd = false },
            searchResults = searchResults.value,
            onQueryChange = viewModel::setIngredientQuery,
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

    deleteTarget?.let { id ->
        val name = recipes.value.find { it.id == id }?.name
        DeleteConfirmDialog(itemName = name, onConfirm = { viewModel.delete(id); deleteTarget = null }, onDismiss = { deleteTarget = null })
    }

}

