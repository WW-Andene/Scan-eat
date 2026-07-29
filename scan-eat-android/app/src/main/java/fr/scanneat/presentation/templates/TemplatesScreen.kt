package fr.scanneat.presentation.templates

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.repository.planning.*
import fr.scanneat.domain.engine.nutrition.ProductHints
import fr.scanneat.presentation.shell.PlanningDestination
import fr.scanneat.presentation.shell.PlanningSwitcherMenu
import fr.scanneat.presentation.templates.components.AddTemplateDialog
import fr.scanneat.presentation.templates.components.EditTemplateItemsDialog
import fr.scanneat.presentation.templates.components.LogTemplateDialog
import fr.scanneat.presentation.templates.components.TemplateCard
import fr.scanneat.presentation.templates.components.TemplatesMealFilterRow
import fr.scanneat.presentation.templates.components.TemplatesStatsRow
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.flow.*


@Composable
fun TemplatesScreen(
    viewModel: TemplatesViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onNavigateToPlanning: (PlanningDestination) -> Unit = {},
) {
    val templates = viewModel.templates.collectAsStateWithLifecycle()
    val mealFilter = viewModel.mealFilter.collectAsStateWithLifecycle()
    val libraryTotalKcal = viewModel.libraryTotalKcal.collectAsStateWithLifecycle()
    val ingredientSearchResults = viewModel.ingredientSearchResults.collectAsStateWithLifecycle()
    val templateWarnings = viewModel.templateWarnings.collectAsStateWithLifecycle()
    val templateHints = viewModel.templateHints.collectAsStateWithLifecycle()
    var logTarget by remember { mutableStateOf<MealTemplate?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<MealTemplate?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var itemsTarget by remember { mutableStateOf<MealTemplate?>(null) }
    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // logTemplate previously failed completely silently - see TemplatesViewModel
    // .actionFailed's own comment.
    val logFailedMessage = stringResource(R.string.common_log_failed)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(logFailedMessage)
            viewModel.clearActionFailed()
        }
    }

    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.templates_title), color = OnBackground) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
        actions = {
            PlanningSwitcherMenu(current = PlanningDestination.TEMPLATES, onNavigate = onNavigateToPlanning)
            IconButton(onClick = { showAdd = true }) { Icon(Icons.Rounded.Add, stringResource(R.string.templates_cd_new), tint = AccentCoral) }
        },
        snackbarHost = { ScanEatSnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize()
                .ambientGloom(base = Background, primary = AccentCoral, secondary = Teal)
                .padding(horizontal = Spacing.L),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            item {
                // Templates had zero list-level filtering despite MealTemplate.meal being
                // the exact same ready-made filter dimension Recipes already uses for its
                // own GoalFilter chips.
                TemplatesMealFilterRow(selected = mealFilter.value, onSelect = { viewModel.setMealFilter(it) })
            }
            if (templates.value.isNotEmpty()) {
                item { TemplatesStatsRow(count = templates.value.size, totalKcal = libraryTotalKcal.value) }
            } else {
                item {
                    EmptyListState(
                        Icons.AutoMirrored.Filled.ListAlt,
                        if (mealFilter.value != null) stringResource(R.string.templates_empty_filtered)
                        else stringResource(R.string.templates_empty_body),
                    )
                }
            }
            items(templates.value, key = { it.id }) { template ->
                TemplateCard(
                    template = template,
                    hints = templateHints.value[template.id] ?: ProductHints.EMPTY,
                    warning = templateWarnings.value[template.id],
                    onToggleFavorite = { viewModel.toggleFavorite(template) },
                    onManageItems = { itemsTarget = template },
                    onLog = { logTarget = template },
                    onSaveAsRecipe = { viewModel.saveAsRecipe(template) },
                    onRename = { renameTarget = template },
                    onDelete = { deleteTarget = template.id },
                )
            }
            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }

    logTarget?.let { t ->
        LogTemplateDialog(
            template = t,
            onDismiss = { logTarget = null },
            onConfirm = { slot, portion -> viewModel.logTemplate(t, mealOverride = slot, portionScale = portion.toDouble()); logTarget = null },
        )
    }

    deleteTarget?.let { id ->
        val name = templates.value.find { it.id == id }?.name
        DeleteConfirmDialog(itemName = name, onConfirm = { viewModel.delete(id); deleteTarget = null }, onDismiss = { deleteTarget = null })
    }

    renameTarget?.let { template ->
        RenameDialog(
            currentName = template.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName -> viewModel.rename(template, newName); renameTarget = null },
        )
    }

    itemsTarget?.let { t ->
        // Re-fetch the live template by id every recomposition — after each
        // add/remove the underlying list changes via the repo, and `t` itself
        // is a stale snapshot from whenever the dialog was opened.
        val live = templates.value.find { it.id == t.id } ?: t
        EditTemplateItemsDialog(
            template = live,
            onAdd    = { item -> viewModel.addItem(live, item) },
            onRemove = { index -> viewModel.removeItem(live, index) },
            onDismiss = { viewModel.setIngredientQuery(""); itemsTarget = null },
            searchResults = ingredientSearchResults.value,
            onQueryChange = viewModel::setIngredientQuery,
        )
    }

    if (showAdd) {
        AddTemplateDialog(
            onDismiss = { showAdd = false },
            onCreate = { name, meal -> viewModel.create(name, meal); showAdd = false },
        )
    }

}
