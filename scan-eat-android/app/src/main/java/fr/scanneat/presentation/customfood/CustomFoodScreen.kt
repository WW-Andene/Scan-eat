package fr.scanneat.presentation.customfood

import compose.icons.tablericons.Barcode
import compose.icons.tablericons.ClipboardList
import compose.icons.tablericons.Plus
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.domain.engine.nutrition.generateProductHints
import fr.scanneat.presentation.customfood.components.AddFoodDialog
import fr.scanneat.presentation.customfood.components.FoodEntryRow
import fr.scanneat.presentation.shell.PlanningDestination
import fr.scanneat.presentation.shell.PlanningSwitcherMenu
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun CustomFoodScreen(
    viewModel: CustomFoodViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onNavigateToPlanning: (PlanningDestination) -> Unit = {},
) {
    val foods   = viewModel.foods.collectAsStateWithLifecycle()
    val foodsWithId = viewModel.foodsWithId.collectAsStateWithLifecycle()
    val query   = viewModel.query.collectAsStateWithLifecycle()
    val results = viewModel.searchResults.collectAsStateWithLifecycle()
    val latestScan = viewModel.latestScan.collectAsStateWithLifecycle()
    val avgKcal = viewModel.avgKcal.collectAsStateWithLifecycle()
    val profile = viewModel.profile.collectAsStateWithLifecycle()
    val language = viewModel.language.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // id to name
    // Replaced the name-only renameTarget (RenameDialog) with a full-field edit -
    // see AddFoodDialog's own doc comment on why this reuses that dialog instead
    // of a second, separate one.
    var editTarget by remember { mutableStateOf<Pair<String, fr.scanneat.domain.engine.nutrition.FoodEntry>?>(null) }

    val displayList = if (query.value.isBlank()) foods.value else results.value

    // Same pattern as Recipes/Templates - save/delete/rename/import previously called
    // repo's Room writes completely unguarded, so a write failure now surfaces here as
    // a one-shot snackbar instead of crashing or silently discarding the action.
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val actionFailedMessage = stringResource(R.string.common_log_failed)
    val deletedMessage = stringResource(R.string.customfood_deleted_message)
    val undoLabel = stringResource(R.string.diary_undo)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(actionFailedMessage)
            viewModel.clearActionFailed()
        }
    }

    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.customfood_title), color = OnBackground) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground)
            }
        },
        actions = {
            PlanningSwitcherMenu(current = PlanningDestination.CUSTOM_FOODS, onNavigate = onNavigateToPlanning)
            IconButton(onClick = { showAdd = true }) {
                Icon(TablerIcons.Plus, stringResource(R.string.common_add), tint = AccentCoral)
            }
        },
        snackbarHost = { ScanEatSnackbarHost(snackbarHostState) },
    ) { padding ->
        // User-reported: ambientGloom() after padding(padding) clipped the gradient
        // to a hard-edged panel starting below the header instead of fading behind
        // it - see GroceryScreen's identical fix for the full explanation.
        Column(modifier = Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold).padding(padding)) {
            // Search bar
            ScanEatSearchField(
                query = query.value, onQueryChange = { viewModel.setQuery(it) },
                placeholder = stringResource(R.string.customfood_search_placeholder),
                modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.S),
            )

            // Library stat chips — count + avg kcal, shown once there's at least one custom food.
            if (foods.value.isNotEmpty() && query.value.isBlank()) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.XS),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                ) {
                    Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = OnBackground.copy(0.06f), shadowElevation = 0.dp, modifier = Modifier.shadow(elevation = 3.dp, shape = RoundedCornerShape(CardRadius.CONTROL), ambientColor = ShadowTint, spotColor = ShadowTint).clip(RoundedCornerShape(CardRadius.CONTROL))) {
                        Text(
                            stringResource(R.string.customfood_stats_count, foods.value.size),
                            modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
                            style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f),
                        )
                    }
                    avgKcal.value?.let { avg ->
                        Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = AccentCoral.copy(0.08f), shadowElevation = 0.dp, modifier = Modifier.shadow(elevation = 3.dp, shape = RoundedCornerShape(CardRadius.CONTROL), ambientColor = ShadowTint, spotColor = ShadowTint).clip(RoundedCornerShape(CardRadius.CONTROL))) {
                            Text(
                                stringResource(R.string.customfood_stats_avg_kcal, avg),
                                modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
                                style = MaterialTheme.typography.labelSmall, color = AccentCoral,
                            )
                        }
                    }
                }
            }

            // Import from last scan banner — surfaces when the most recent scan isn't
            // already saved as a custom food, offering a one-tap import.
            val scan = latestScan.value
            val customNames = remember(foods.value) { foods.value.mapTo(hashSetOf()) { it.name } }
            if (scan != null && scan.product.name !in customNames) {
                Surface(
                    color = AccentCoral.copy(0.1f),
                    shape = RoundedCornerShape(CardRadius.CONTROL),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.L, vertical = Spacing.XS)
                        .shadow(elevation = 3.dp, shape = RoundedCornerShape(CardRadius.CONTROL), ambientColor = ShadowTint, spotColor = ShadowTint)
                        .clip(RoundedCornerShape(CardRadius.CONTROL)),
                    shadowElevation = 0.dp,
                ) {
                    Row(
                        Modifier.padding(Spacing.S),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                    ) {
                        Icon(TablerIcons.Barcode, null, tint = AccentCoral, modifier = Modifier.size(IconSize.Compact))
                        Text(
                            stringResource(R.string.customfood_import_from_scan, scan.product.name),
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentCoral,
                            modifier = Modifier.weight(1f),
                        )
                        // Left at IconButton's default 48dp touch target (Material/WCAG
                        // minimum, was 32dp) and given a real contentDescription (was
                        // null - a TalkBack user heard nothing for this import action).
                        IconButton(onClick = { viewModel.importFromScan(scan) }) {
                            Icon(TablerIcons.Plus, contentDescription = stringResource(R.string.customfood_import_from_scan, scan.product.name), tint = AccentCoral, modifier = Modifier.size(IconSize.Compact))
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.L),
                verticalArrangement = Arrangement.spacedBy(Spacing.M),
            ) {
                if (displayList.isEmpty()) {
                    item {
                        // Was missing the ctaLabel/onCta pair every other tracker's empty state
                        // (Weight/Medication/Recipes) already passes - a first-time user saw the
                        // icon + message with no tappable affordance and had to notice the
                        // top-bar "+" instead. Only offered on the true "library is empty" case,
                        // not the "no search results" case, where a search-scoped CTA to add a
                        // food with the exact query text would need a dedicated dialog prefill.
                        EmptyListState(
                            TablerIcons.ClipboardList,
                            if (query.value.isBlank()) stringResource(R.string.customfood_empty_body)
                            else stringResource(R.string.customfood_empty_query, query.value),
                            ctaLabel = if (query.value.isBlank()) stringResource(R.string.customfood_cd_add) else null,
                            onCta = if (query.value.isBlank()) { { showAdd = true } } else null,
                        )
                    }
                }

                // Indexed (rather than keyed purely on name) so two entries that happen to
                // share a display name — e.g. a custom food named the same as a built-in
                // FOOD_DB hit while searching — can never collide on a LazyColumn key and
                // crash Compose.
                itemsIndexed(displayList, key = { index, entry -> "$index:${entry.name}" }) { _, entry ->
                    FoodEntryRow(
                        entry    = entry,
                        isCustom = entry.name in customNames,
                        hints    = generateProductHints(viewModel.toProduct(entry), profile.value, language.value),
                        // Matched on full structural equality (every field), not just name -
                        // two custom foods sharing a name (e.g. after a backup restore, or a
                        // displayed row that's actually a built-in FOOD_DB hit sharing a
                        // custom food's name) previously resolved via a name-only firstOrNull,
                        // so tapping delete/rename on the row the user is actually looking at
                        // could silently mutate/delete a different row instead.
                        onDelete = {
                            foodsWithId.value.firstOrNull { it.second == entry }
                                ?.let { (id, _) -> deleteTarget = id to entry.name }
                        },
                        onEdit = {
                            // Previously delete was the only entry point — a typo in a
                            // custom food's name or macro value could never be fixed
                            // without deleting and re-creating it from scratch.
                            foodsWithId.value.firstOrNull { it.second == entry }
                                ?.let { (id, food) -> editTarget = id to food }
                        },
                    )
                }

                item { Spacer(Modifier.height(Spacing.XXL)) }
            }
        }
    }

    // Add dialog
    if (showAdd) {
        AddFoodDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, kcal, prot, carb, fat, fib, salt, aliases, barcode ->
                viewModel.save(name, kcal, prot, carb, fat, fib, salt, aliases, barcode)
                showAdd = false
            },
        )
    }

    // Delete confirmation — shared dialog, same as Weight/Templates/Recipes/Activity.
    // Was confirm-only with no way back afterward, unlike Weight/Medication's
    // confirm-then-undo-snackbar pair - a mis-tapped confirm on the wrong row (two
    // custom foods can share a display name) was previously unrecoverable.
    deleteTarget?.let { (id, name) ->
        DeleteConfirmDialog(
            itemName  = name,
            onConfirm = {
                viewModel.delete(id)
                deleteTarget = null
                scope.launch {
                    val result = snackbarHostState.showSnackbar(deletedMessage, actionLabel = undoLabel)
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }

    editTarget?.let { (id, food) ->
        AddFoodDialog(
            initial = food,
            onDismiss = { editTarget = null },
            onConfirm = { name, kcal, prot, carb, fat, fib, salt, aliases, _ ->
                viewModel.update(id, name, kcal, prot, carb, fat, fib, salt, aliases)
                editTarget = null
            },
        )
    }
}
