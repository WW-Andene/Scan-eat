package fr.scanneat.presentation.grocery

import compose.icons.tablericons.ShoppingCart
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.repository.planning.DEFAULT_LIST
import fr.scanneat.domain.engine.planning.*
import fr.scanneat.presentation.grocery.components.GroceryFrequentSuggestionsRow
import fr.scanneat.presentation.grocery.components.GroceryItemRow
import fr.scanneat.presentation.grocery.components.GroceryProgressRow
import fr.scanneat.presentation.grocery.components.GroceryQuickAddRow
import fr.scanneat.presentation.grocery.components.GroceryTopBarActions
import fr.scanneat.presentation.grocery.components.categoryLabel
import fr.scanneat.presentation.shell.PlanningDestination
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun GroceryScreen(
    viewModel: GroceryViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onNavigateToPlanning: (PlanningDestination) -> Unit = {},
    // User-requested: scan a product straight from Courses to add it to the
    // list - see AppRoutes.SCAN_FOR_GROCERY's own comment. Defaults to a
    // no-op so this remains source-compatible with any other call site.
    onScanToAdd: () -> Unit = {},
    onOpenLoyaltyCards: () -> Unit = {},
) {
    var quickAddText by rememberSaveable { mutableStateOf("") }
    // Grocery had no search at all, unlike every other list-heavy screen (Recipes,
    // Templates, ScanHistory, CustomFood) — aggregating many recipes/templates into
    // one big list meant finding a specific item was scroll-and-scan only. Purely a
    // display filter (checkedProgress/counts below still read the unfiltered lists).
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val items     = viewModel.groceryItems.collectAsStateWithLifecycle()
    val checkable = viewModel.checkableItems.collectAsStateWithLifecycle()
    val manualItemKeys = viewModel.manualItemKeys.collectAsStateWithLifecycle()
    val itemWarnings = viewModel.itemWarnings.collectAsStateWithLifecycle()
    val scopeToPlanned = viewModel.scopeToPlanned.collectAsStateWithLifecycle()
    val checkedProgress = viewModel.checkedProgress.collectAsStateWithLifecycle()
    val budgetEstimate = viewModel.budgetEstimate.collectAsStateWithLifecycle()
    val currencySymbol = viewModel.currencySymbol.collectAsStateWithLifecycle()
    val frequentSuggestions = viewModel.frequentSuggestions.collectAsStateWithLifecycle()
    val sortAlpha = viewModel.sortAlpha.collectAsStateWithLifecycle()
    val groupByAisle = viewModel.groupByAisle.collectAsStateWithLifecycle()
    val activeListName = viewModel.activeListName.collectAsStateWithLifecycle()
    val availableLists = viewModel.availableLists.collectAsStateWithLifecycle()
    var showNewListDialog by remember { mutableStateOf(false) }
    var newListText by rememberSaveable { mutableStateOf("") }
    // User-requested: checking an item off offers to log its price right away,
    // so a real purchase feeds PriceRepository (Journal/Expenses/budgetEstimate)
    // instead of Courses staying a dead end for price data. Optional - "Passer"
    // just checks the item off with no price logged, same as before this feature.
    var priceLogItem by remember { mutableStateOf<GroceryItem?>(null) }
    var priceLogText by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val haptics   = LocalHapticFeedback.current
    val context   = LocalContext.current
    val onToggleChecked: (GroceryItem, Boolean) -> Unit = { item, checked ->
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.toggleChecked(item, checked)
        if (checked) { priceLogText = ""; priceLogItem = item }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.grocery_copied)
    val clearedMessage = stringResource(R.string.grocery_cleared_confirmation)
    val deletedMessage = stringResource(R.string.grocery_item_deleted_message)
    val undoLabel = stringResource(R.string.diary_undo)
    var copyMenuExpanded by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val logFailedMessage = stringResource(R.string.common_log_failed)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(logFailedMessage)
            viewModel.clearActionFailed()
        }
    }

    FloatingScreenScaffold(
        snackbarHost = { ScanEatSnackbarHost(snackbarHostState) },
        title = { Text(stringResource(R.string.grocery_title), color = OnBackground) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) } },
        actions = {
            GroceryTopBarActions(
                onNavigateToPlanning = onNavigateToPlanning,
                hasCheckedItems = checkable.value.any { it.checked },
                onShowClearConfirm = { showClearConfirm = true },
                hasItems = items.value.isNotEmpty(),
                sortAlpha = sortAlpha.value,
                onToggleSortAlpha = { viewModel.toggleSortAlpha() },
                groupByAisle = groupByAisle.value,
                onToggleGroupByAisle = { viewModel.toggleGroupByAisle() },
                onShare = {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, formatGroceryList(items.value))
                    }
                    context.startActivity(Intent.createChooser(sendIntent, null))
                },
                copyMenuExpanded = copyMenuExpanded,
                onCopyMenuExpandedChange = { copyMenuExpanded = it },
                onCopyPlain = {
                    clipboard.setText(AnnotatedString(formatGroceryList(items.value)))
                    scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                },
                onCopyChecklist = {
                    // formatGroceryList's markdown param existed since the original JS
                    // port but had no UI entry point at all - a real feature dropped
                    // in translation, not a deliberate scope cut.
                    clipboard.setText(AnnotatedString(formatGroceryList(items.value, markdown = true)))
                    scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                },
                onScanToAdd = onScanToAdd,
                onOpenLoyaltyCards = onOpenLoyaltyCards,
            )
        },
    ) { padding ->
        // User-reported: ambientGloom() applied after padding(padding) painted the
        // gradient only across this Column's already-shrunken bounds (starting
        // below the floating header), producing a hard rectangular seam right at
        // the header's bottom edge instead of RecipesScreen's smooth radial fade
        // reaching behind the header itself. Reordered so the gloom paints across
        // the full fillMaxSize screen first, matching every other screen's pattern.
        Column(Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold).padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.L, vertical = Spacing.XS),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.grocery_scope_planned),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnBackground.copy(0.85f),
                )
                Switch(
                    checked = scopeToPlanned.value,
                    onCheckedChange = { viewModel.setScopeToPlanned(it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = AccentCoral),
                )
            }
            // User-requested: named multiple lists ("Semaine", "BBQ"...) - Courses
            // previously held exactly one shopping list with no way to separate
            // e.g. a weekly grocery run from a one-off BBQ list. Only shown once
            // there's more than the always-present default list, or once the user
            // has started creating others, to keep the common single-list case
            // uncluttered.
            if (availableLists.value.size > 1) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.L, vertical = Spacing.XS),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                ) {
                    items(availableLists.value, key = { it }) { list ->
                        FilterChip(
                            selected = list == activeListName.value,
                            onClick = { viewModel.switchList(list) },
                            label = { Text(list) },
                            trailingIcon = if (list != DEFAULT_LIST) {
                                {
                                    IconButton(onClick = { viewModel.deleteList(list) }, modifier = Modifier.size(16.dp)) {
                                        Icon(Icons.Rounded.Close, stringResource(R.string.common_delete), tint = OnBackground.copy(0.6f))
                                    }
                                }
                            } else null,
                        )
                    }
                    item {
                        AssistChip(onClick = { newListText = ""; showNewListDialog = true }, label = { Text("+") })
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.L, vertical = Spacing.XS)) {
                    AssistChip(onClick = { newListText = ""; showNewListDialog = true }, label = { Text(stringResource(R.string.grocery_new_list)) })
                }
            }
            if (items.value.isEmpty()) {
                // Was wrapped in a fillMaxSize() Box, vertically centering it in the
                // full screen height - every sibling feature (Diary/Recipes/Templates/
                // CustomFood) places this same component near the top of the content
                // instead, under the header row.
                // Previously told the user what to do ("create/plan a recipe") with no
                // shortcut to do it, unlike Recipes' own empty state which pairs the same
                // kind of message with a CTA button - added the matching navigation here.
                EmptyListState(
                    icon = TablerIcons.ShoppingCart,
                    message = stringResource(
                        if (scopeToPlanned.value) R.string.grocery_empty_planned_body else R.string.grocery_empty_body
                    ),
                    ctaLabel = stringResource(
                        if (scopeToPlanned.value) R.string.grocery_empty_planned_cta else R.string.grocery_empty_cta
                    ),
                    onCta = {
                        onNavigateToPlanning(if (scopeToPlanned.value) PlanningDestination.MEAL_PLAN else PlanningDestination.RECIPES)
                    },
                )
            } else {
            val filteredCheckable = remember(checkable.value, searchQuery) {
                if (searchQuery.isBlank()) checkable.value
                else checkable.value.filter { it.item.name.contains(searchQuery, ignoreCase = true) }
            }
            // Was recomputed on every recomposition of the LazyColumn content scope
            // below (including ones triggered by unrelated state, e.g. itemWarnings/
            // manualItemKeys) - filteredCheckable itself is already remember()'d,
            // this derived grouping wasn't. remember() must live here, not inside
            // the LazyColumn content lambda (LazyListScope isn't @Composable).
            val grouped = remember(filteredCheckable) { filteredCheckable.groupBy { groceryCategoryFor(it.item.name) } }
            // Same checked-items-to-bottom fix as the aisle-grouped branch below - must
            // live here, not inside the LazyColumn content lambda (LazyListScope isn't
            // @Composable, same bug class grouped/filteredCheckable above already guard against).
            val sortedCheckable = remember(filteredCheckable) { filteredCheckable.sortedBy { it.checked } }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.L),
                verticalArrangement = Arrangement.spacedBy(Spacing.M),
            ) {
                item { Spacer(Modifier.height(Spacing.XS)) }
                item {
                    // Inline quick-add — previously the only way to add a manual
                    // item was via a "Save to grocery" button in an unrelated screen.
                    GroceryQuickAddRow(
                        quickAddText = quickAddText,
                        onQuickAddTextChange = { quickAddText = it },
                        onAdd = { viewModel.quickAdd(quickAddText); quickAddText = "" },
                    )
                }
                item {
                    Spacer(Modifier.height(Spacing.XS))
                    GroceryFrequentSuggestionsRow(
                        suggestions = frequentSuggestions.value,
                        onAdd = { viewModel.quickAdd(it) },
                    )
                }
                item {
                    ScanEatSearchField(
                        query = searchQuery, onQueryChange = { searchQuery = it },
                        placeholder = stringResource(R.string.history_search_placeholder),
                        modifier = Modifier.padding(horizontal = Spacing.L, vertical = Spacing.S),
                    )
                }
                item {
                    GroceryProgressRow(
                        itemCount = items.value.size,
                        checkedProgress = checkedProgress.value,
                        budgetEstimate = budgetEstimate.value,
                        currencySymbol = currencySymbol.value,
                    )
                }
                if (searchQuery.isNotBlank() && filteredCheckable.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.grocery_search_no_results),
                            style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f),
                            modifier = Modifier.padding(vertical = Spacing.L),
                        )
                    }
                } else if (groupByAisle.value) {
                    // Previously a flat alphabetical/unsorted list with no produce/dairy/
                    // pantry sectioning at all. Fixed display order regardless of which
                    // categories this particular list actually contains.
                    listOf(
                        GroceryCategory.PRODUCE, GroceryCategory.MEAT_FISH, GroceryCategory.DAIRY,
                        GroceryCategory.BAKERY, GroceryCategory.PANTRY, GroceryCategory.FROZEN,
                        GroceryCategory.BEVERAGES, GroceryCategory.OTHER,
                    ).forEach { category ->
                        // Checked items previously stayed exactly where they started -
                        // in the middle of a long shopping trip, the remaining unchecked
                        // items get increasingly scattered among faded/struck-through
                        // checked ones, making "what's left in produce?" harder to scan
                        // the further along the trip you are. sortedBy is stable, so
                        // relative order within "still needed" / "already checked" is
                        // preserved, only the two groups are separated.
                        val itemsInCategory = (grouped[category] ?: return@forEach).sortedBy { it.checked }
                        item(key = "header_$category") {
                            Text(
                                categoryLabel(category), style = MaterialTheme.typography.labelMedium,
                                color = OnBackground.copy(0.5f), fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = Spacing.S),
                            )
                        }
                        items(itemsInCategory, key = { it.item.key }) { checkableItem ->
                            GroceryItemRow(
                                checkableItem, warning = itemWarnings.value[checkableItem.item.key],
                                isManual = checkableItem.item.key in manualItemKeys.value,
                                onToggleChecked = { checked -> onToggleChecked(checkableItem.item, checked) },
                                onDeleteManual = {
                                viewModel.deleteManualContribution(checkableItem.item.key)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(deletedMessage, actionLabel = undoLabel)
                                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDeleteManual()
                                }
                            },
                                onEditQuantity = { grams -> viewModel.editManualQuantity(checkableItem.item.key, grams) },
                            )
                        }
                    }
                } else {
                    items(sortedCheckable, key = { it.item.key }) { checkableItem ->
                        GroceryItemRow(
                            checkableItem, warning = itemWarnings.value[checkableItem.item.key],
                            isManual = checkableItem.item.key in manualItemKeys.value,
                            onToggleChecked = { checked -> onToggleChecked(checkableItem.item, checked) },
                            onDeleteManual = {
                                viewModel.deleteManualContribution(checkableItem.item.key)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(deletedMessage, actionLabel = undoLabel)
                                    if (result == SnackbarResult.ActionPerformed) viewModel.undoDeleteManual()
                                }
                            },
                            onEditQuantity = { grams -> viewModel.editManualQuantity(checkableItem.item.key, grams) },
                        )
                    }
                }
                item { Spacer(Modifier.height(Spacing.XXL)) }
            }
            }
        }
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.grocery_clear_confirm_title),
            body  = stringResource(R.string.grocery_clear_confirm_body),
            confirmLabel = stringResource(R.string.grocery_clear_checked),
            onConfirm = {
                viewModel.clearAllChecked()
                showClearConfirm = false
                scope.launch { snackbarHostState.showSnackbar(clearedMessage) }
            },
            onDismiss = { showClearConfirm = false },
        )
    }

    if (showNewListDialog) {
        AlertDialog(
            onDismissRequest = { showNewListDialog = false },
            title = { Text(stringResource(R.string.grocery_new_list)) },
            text = {
                OutlinedTextField(
                    value = newListText,
                    onValueChange = { newListText = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.grocery_new_list_placeholder)) },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newListText.isNotBlank(),
                    onClick = { viewModel.createList(newListText); showNewListDialog = false },
                ) { Text(stringResource(R.string.common_add)) }
            },
            dismissButton = { TextButton(onClick = { showNewListDialog = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    // User-requested: checking an item off can log its price - see
    // onToggleChecked above. Purely optional, "Passer" dismisses with the
    // item already checked off from onToggleChecked (this dialog never
    // un-checks it).
    priceLogItem?.let { item ->
        AlertDialog(
            onDismissRequest = { priceLogItem = null },
            title = { Text(stringResource(R.string.grocery_log_price_title, item.name)) },
            text = {
                OutlinedTextField(
                    value = priceLogText,
                    onValueChange = { priceLogText = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.grocery_log_price_placeholder, currencySymbol.value)) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = priceLogText.replace(',', '.').toDoubleOrNull()?.let { it > 0.0 } == true,
                    onClick = {
                        priceLogText.replace(',', '.').toDoubleOrNull()?.let { viewModel.logPrice(item, it) }
                        priceLogItem = null
                    },
                ) { Text(stringResource(R.string.common_add)) }
            },
            dismissButton = { TextButton(onClick = { priceLogItem = null }) { Text(stringResource(R.string.grocery_skip_price)) } },
        )
    }
}
