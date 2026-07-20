package fr.scanneat.presentation.grocery

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.domain.engine.planning.*
import fr.scanneat.presentation.shell.PlanningDestination
import fr.scanneat.presentation.shell.PlanningSwitcherMenu
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun GroceryScreen(
    viewModel: GroceryViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onNavigateToPlanning: (PlanningDestination) -> Unit = {},
) {
    var quickAddText by rememberSaveable { mutableStateOf("") }
    val items     = viewModel.groceryItems.collectAsStateWithLifecycle()
    val checkable = viewModel.checkableItems.collectAsStateWithLifecycle()
    val manualItemKeys = viewModel.manualItemKeys.collectAsStateWithLifecycle()
    val itemWarnings = viewModel.itemWarnings.collectAsStateWithLifecycle()
    val scopeToPlanned = viewModel.scopeToPlanned.collectAsStateWithLifecycle()
    val checkedProgress = viewModel.checkedProgress.collectAsStateWithLifecycle()
    val sortAlpha = viewModel.sortAlpha.collectAsStateWithLifecycle()
    val groupByAisle = viewModel.groupByAisle.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val haptics   = LocalHapticFeedback.current
    val context   = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.grocery_copied)
    val clearedMessage = stringResource(R.string.grocery_cleared_confirmation)
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            FloatingTopBar(
                title = { Text(stringResource(R.string.grocery_title), color = OnBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
                actions = {
                    PlanningSwitcherMenu(current = PlanningDestination.GROCERY, onNavigate = onNavigateToPlanning)
                    if (checkable.value.any { it.checked }) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Default.RemoveDone, stringResource(R.string.grocery_clear_checked), tint = OnBackground.copy(0.7f))
                        }
                    }
                    if (items.value.isNotEmpty()) {
                        IconButton(onClick = { viewModel.toggleSortAlpha() }) {
                            Icon(
                                Icons.Default.SortByAlpha,
                                stringResource(R.string.grocery_sort_alpha),
                                tint = if (sortAlpha.value) AccentCoral else OnBackground.copy(0.6f),
                            )
                        }
                        // Previously a flat unsorted/alphabetical-only list with no
                        // produce/dairy/pantry sectioning at all.
                        IconButton(onClick = { viewModel.toggleGroupByAisle() }) {
                            Icon(
                                Icons.Default.Category,
                                stringResource(R.string.grocery_group_by_aisle),
                                tint = if (groupByAisle.value) AccentCoral else OnBackground.copy(0.6f),
                            )
                        }
                        // Previously the only way out of the app was clipboard copy-then-paste -
                        // mirrors ResultScreen's existing ACTION_SEND share pattern.
                        IconButton(onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, formatGroceryList(items.value))
                            }
                            context.startActivity(Intent.createChooser(sendIntent, null))
                        }) {
                            Icon(Icons.Default.Share, stringResource(R.string.grocery_cd_share), tint = OnBackground.copy(0.7f))
                        }
                        Box {
                            IconButton(onClick = { copyMenuExpanded = true }) {
                                Icon(Icons.Default.ContentCopy, stringResource(R.string.common_copy), tint = AccentCoral)
                            }
                            DropdownMenu(expanded = copyMenuExpanded, onDismissRequest = { copyMenuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.grocery_copy_plain)) },
                                    onClick = {
                                        copyMenuExpanded = false
                                        clipboard.setText(AnnotatedString(formatGroceryList(items.value)))
                                        scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                                    },
                                )
                                // formatGroceryList's markdown param existed since the original JS
                                // port but had no UI entry point at all - a real feature dropped
                                // in translation, not a deliberate scope cut.
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.grocery_copy_checklist)) },
                                    onClick = {
                                        copyMenuExpanded = false
                                        clipboard.setText(AnnotatedString(formatGroceryList(items.value, markdown = true)))
                                        scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        containerColor = Background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).ambientGloom(base = Background, primary = CalorieOrange, secondary = AccentCoral)) {
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
            if (items.value.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyListState(
                        icon = Icons.Default.ShoppingCart,
                        message = stringResource(
                            if (scopeToPlanned.value) R.string.grocery_empty_planned_body else R.string.grocery_empty_body
                        ),
                    )
                }
            } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.L),
                verticalArrangement = Arrangement.spacedBy(Spacing.S),
            ) {
                item { Spacer(Modifier.height(Spacing.XS)) }
                item {
                    // Inline quick-add — previously the only way to add a manual
                    // item was via a "Save to grocery" button in an unrelated screen.
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        OutlinedTextField(
                            value = quickAddText,
                            onValueChange = { quickAddText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.grocery_quick_add_placeholder), color = OnBackground.copy(0.4f)) },
                            singleLine = true,
                            shape = RoundedCornerShape(CardRadius.CONTROL),
                            colors = scanEatTextFieldColors(),
                        )
                        IconButton(
                            onClick = { viewModel.quickAdd(quickAddText); quickAddText = "" },
                            enabled = quickAddText.isNotBlank(),
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(Icons.Default.Add, stringResource(R.string.grocery_quick_add_cd), tint = if (quickAddText.isNotBlank()) AccentCoral else OnBackground.copy(0.3f))
                        }
                    }
                }
                item {
                    val (checked, total) = checkedProgress.value
                    Text(pluralStringResource(R.plurals.grocery_item_count, items.value.size, items.value.size),
                        style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
                    if (total > 0 && checked > 0) {
                        Spacer(Modifier.height(4.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { (checked.toFloat() / total).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                            color = semanticGreen(),
                            trackColor = OnBackground.copy(0.08f),
                        )
                        if (checked == total) {
                            Text(
                                stringResource(R.string.grocery_all_done),
                                style = MaterialTheme.typography.labelSmall,
                                color = semanticGreen(),
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            )
                        } else {
                            Text(
                                stringResource(R.string.grocery_checked_progress, checked, total),
                                style = MaterialTheme.typography.labelSmall,
                                color = OnBackground.copy(0.4f),
                            )
                        }
                    }
                }
                if (groupByAisle.value) {
                    // Previously a flat alphabetical/unsorted list with no produce/dairy/
                    // pantry sectioning at all. Fixed display order regardless of which
                    // categories this particular list actually contains.
                    val grouped = checkable.value.groupBy { groceryCategoryFor(it.item.name) }
                    listOf(
                        GroceryCategory.PRODUCE, GroceryCategory.MEAT_FISH, GroceryCategory.DAIRY,
                        GroceryCategory.BAKERY, GroceryCategory.PANTRY, GroceryCategory.FROZEN,
                        GroceryCategory.BEVERAGES, GroceryCategory.OTHER,
                    ).forEach { category ->
                        val itemsInCategory = grouped[category] ?: return@forEach
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
                                onToggleChecked = { checked -> haptics.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.toggleChecked(checkableItem.item, checked) },
                                onDeleteManual = { viewModel.deleteManualContribution(checkableItem.item.key) },
                            )
                        }
                    }
                } else {
                    items(checkable.value, key = { it.item.key }) { checkableItem ->
                        GroceryItemRow(
                            checkableItem, warning = itemWarnings.value[checkableItem.item.key],
                            isManual = checkableItem.item.key in manualItemKeys.value,
                            onToggleChecked = { checked -> haptics.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.toggleChecked(checkableItem.item, checked) },
                            onDeleteManual = { viewModel.deleteManualContribution(checkableItem.item.key) },
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
}

@Composable
private fun categoryLabel(category: GroceryCategory): String = stringResource(
    when (category) {
        GroceryCategory.PRODUCE   -> R.string.grocery_category_produce
        GroceryCategory.DAIRY     -> R.string.grocery_category_dairy
        GroceryCategory.MEAT_FISH -> R.string.grocery_category_meat_fish
        GroceryCategory.BAKERY    -> R.string.grocery_category_bakery
        GroceryCategory.FROZEN    -> R.string.grocery_category_frozen
        GroceryCategory.BEVERAGES -> R.string.grocery_category_beverages
        GroceryCategory.PANTRY    -> R.string.grocery_category_pantry
        GroceryCategory.OTHER     -> R.string.grocery_category_other
    },
)

/** Extracted so the same row renders identically flat (default) or grouped-by-aisle. */
@Composable
private fun GroceryItemRow(
    checkableItem: CheckableGroceryItem,
    warning: String?,
    isManual: Boolean,
    onToggleChecked: (Boolean) -> Unit,
    onDeleteManual: () -> Unit,
) {
    val item = checkableItem.item
    val checked = checkableItem.checked
    val reducedMotion = rememberReducedMotion()
    val contentAlpha by animateFloatAsState(
        targetValue   = if (checked) 0.5f else 1f,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = 200),
        label         = "groceryItemAlpha",
    )
    // Previously deleted a manual item on a single tap with no confirmation - every
    // other destructive action in the app (Weight/Activity/Recipes/Templates/Medication/
    // CustomFood/ScanHistory) routes through DeleteConfirmDialog first.
    var showDeleteConfirm by remember { mutableStateOf(false) }
    ScanEatCard(
        shape = RoundedCornerShape(CardRadius.CONTROL),
        color = SurfaceVariant,
        contentPadding = PaddingValues(horizontal = Spacing.XS, vertical = Spacing.XS),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Checkbox(
                checked = checked,
                onCheckedChange = onToggleChecked,
                colors = CheckboxDefaults.colors(checkedColor = AccentCoral),
            )
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyMedium,
                    color = OnSurface.copy(contentAlpha), fontWeight = FontWeight.Medium,
                    textDecoration = if (checked) TextDecoration.LineThrough else null)
                if (item.sources.isNotEmpty()) {
                    Text(item.sources.joinToString(", "), style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.5f * contentAlpha))
                }
                // Diet/allergen check previously only ran on scanned products and
                // (as of this same round) Recipes - the grocery list itself, the
                // other place a user relies on the app to protect them, had none.
                warning?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = semanticAmber().copy(contentAlpha))
                }
            }
            if (item.grams > 0) {
                Text(stringResource(R.string.grocery_grams, item.grams), style = MaterialTheme.typography.labelLarge,
                    color = AccentCoral.copy(contentAlpha), fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = Spacing.S))
            }
            // Manually-added items (e.g. "Save to grocery" from a scanned
            // product) previously had no way to be removed at all, only
            // checked off — the only way to hide one was to leave it
            // permanently ticked. Only shown when this row actually has a
            // manual contribution, since a recipe-only row has nothing here to delete.
            if (isManual) {
                // Left at IconButton's default 48dp touch target (Material/WCAG
                // minimum) - a UI/UX audit found this forced to 32dp.
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Close, stringResource(R.string.common_delete), tint = OnSurface.copy(0.4f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
    if (showDeleteConfirm) {
        DeleteConfirmDialog(
            itemName = item.name,
            onConfirm = { onDeleteManual(); showDeleteConfirm = false },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}
