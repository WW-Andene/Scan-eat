package fr.scanneat.presentation.pantry

import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.ShoppingCart
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Trash
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.repository.pantry.PantryItem
import fr.scanneat.domain.model.ProductCategory
import fr.scanneat.presentation.expenses.components.displayLabel
import fr.scanneat.presentation.pantry.components.AddPantryItemDialog
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/**
 * User-requested: a real persisted pantry inventory - see
 * PantryViewModel/PantryEntity's own doc comments. Grouped by category
 * (declaration order, same as ExpensesSummaryCard.displayLabel()'s `when`)
 * once the list holds items across more than one category - a flat list
 * sorted only by expiry/name got hard to scan once the pantry held more
 * than a handful of unrelated staples.
 */
@Composable
fun PantryScreen(viewModel: PantryViewModel = hiltViewModel(), onBack: () -> Unit) {
    val items = viewModel.items.collectAsStateWithLifecycle()
    val expiringItems = viewModel.expiringItems.collectAsStateWithLifecycle()
    val query = viewModel.query.collectAsStateWithLifecycle()
    val recalledBarcodes = viewModel.recalledBarcodes.collectAsStateWithLifecycle()
    val healthConflictBarcodes = viewModel.healthConflictBarcodes.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<PantryItem?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val actionFailed = viewModel.actionFailed.collectAsStateWithLifecycle()
    val logFailedMessage = stringResource(R.string.common_log_failed)
    LaunchedEffect(actionFailed.value) {
        if (actionFailed.value) {
            snackbarHostState.showSnackbar(logFailedMessage)
            viewModel.clearActionFailed()
        }
    }

    val deletedMessage = stringResource(R.string.pantry_deleted_message)
    val undoLabel = stringResource(R.string.pantry_undo)
    // code-audit §D3: was recomputed on every recomposition (e.g. opening/
    // closing the edit-item dialog) even though items.value hadn't changed -
    // GroceryScreen.kt already fixed this exact bug class elsewhere.
    val byCategory = remember(items.value) { items.value.groupBy { it.category } }

    FloatingScreenScaffold(
        title = { Text(stringResource(R.string.pantry_title), color = OnBackground) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) } },
        actions = { IconButton(onClick = { showAdd = true }) { Icon(TablerIcons.Plus, stringResource(R.string.common_add), tint = AccentCoral) } },
        snackbarHost = { ScanEatSnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold).padding(horizontal = Spacing.L),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            item { Spacer(Modifier.height(Spacing.L)) }

            if (expiringItems.value.isNotEmpty()) {
                item { PantryExpiryBanner(expiringItems.value.size) }
            }

            if (healthConflictBarcodes.value.isNotEmpty()) {
                item { PantryHealthConflictBanner(healthConflictBarcodes.value.size) }
            }

            if (items.value.isNotEmpty() || query.value.isNotBlank()) {
                item {
                    ScanEatSearchField(
                        query = query.value,
                        onQueryChange = { viewModel.setQuery(it) },
                        placeholder = stringResource(R.string.pantry_search_placeholder),
                    )
                }
            }

            if (items.value.isEmpty()) {
                item {
                    val emptyMessage = if (query.value.isNotBlank()) stringResource(R.string.pantry_search_empty) else stringResource(R.string.pantry_empty)
                    EmptyListState(TablerIcons.ShoppingCart, emptyMessage)
                }
            } else {
                // Only worth a header/grouping once items actually span more than
                // one category - a pantry holding only "Autre" items (every manual
                // add before this pass defaulted there) would otherwise show one
                // header for the whole list, adding noise with no new information.
                if (byCategory.size <= 1) {
                    items(items.value, key = { it.id }) { pantryItem -> PantryRowWithActions(pantryItem, viewModel, editTargetSetter = { editTarget = it }, snackbarHostState, scope, deletedMessage, undoLabel, recalledBarcodes.value, healthConflictBarcodes.value) }
                } else {
                    ProductCategory.entries.forEach { category ->
                        val categoryItems = byCategory[category].orEmpty()
                        if (categoryItems.isNotEmpty()) {
                            item(key = "header_${category.key}") {
                                Text(
                                    stringResource(R.string.pantry_category_header, category.displayLabel(), categoryItems.size),
                                    style = MaterialTheme.typography.titleSmall, color = OnBackground, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(top = Spacing.S),
                                )
                            }
                            items(categoryItems, key = { it.id }) { pantryItem -> PantryRowWithActions(pantryItem, viewModel, editTargetSetter = { editTarget = it }, snackbarHostState, scope, deletedMessage, undoLabel, recalledBarcodes.value, healthConflictBarcodes.value) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }

    if (showAdd) {
        AddPantryItemDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, quantity, unit, expiryDate, category ->
                viewModel.add(name, barcode = null, category = category, quantity = quantity, unit = unit, expiryDate = expiryDate)
                showAdd = false
            },
        )
    }

    editTarget?.let { target ->
        AddPantryItemDialog(
            initialName = target.name,
            initialQuantity = target.quantity,
            initialUnit = target.unit,
            initialExpiryDate = target.expiryDate,
            initialCategory = target.category,
            lockName = true,
            onDismiss = { editTarget = null },
            onAdd = { _, quantity, unit, expiryDate, category ->
                viewModel.updateDetails(target.id, quantity, unit, expiryDate, category)
                editTarget = null
            },
        )
    }
}

@Composable
private fun PantryExpiryBanner(count: Int) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(CardRadius.CONTROL),
        color = semanticAmber().copy(0.1f),
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(2.dp, semanticAmber().copy(alpha = 0.35f)),
    ) {
        Row(modifier = Modifier.padding(Spacing.M), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Icon(TablerIcons.AlertTriangle, null, tint = semanticAmber(), modifier = Modifier.size(IconSize.Compact))
            Text(
                pluralStringResourceCompat(count),
                style = MaterialTheme.typography.bodySmall, color = semanticAmber(), fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun pluralStringResourceCompat(count: Int): String =
    androidx.compose.ui.res.pluralStringResource(R.plurals.pantry_expiring_count, count, count)

@Composable
private fun PantryHealthConflictBanner(count: Int) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(CardRadius.CONTROL),
        color = semanticRed().copy(0.1f),
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(2.dp, semanticRed().copy(alpha = 0.35f)),
    ) {
        Row(modifier = Modifier.padding(Spacing.M), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Icon(TablerIcons.AlertTriangle, null, tint = semanticRed(), modifier = Modifier.size(IconSize.Compact))
            Text(
                androidx.compose.ui.res.pluralStringResource(R.plurals.pantry_health_conflict_count, count, count),
                style = MaterialTheme.typography.bodySmall, color = semanticRed(), fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PantryRowWithActions(
    pantryItem: PantryItem,
    viewModel: PantryViewModel,
    editTargetSetter: (PantryItem) -> Unit,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    deletedMessage: String,
    undoLabel: String,
    recalledBarcodes: Set<String>,
    healthConflictBarcodes: Set<String>,
) {
    val step = if (pantryItem.unit == fr.scanneat.data.repository.pantry.PantryUnit.UNITS) 1.0 else 10.0
    PantryItemRow(
        item = pantryItem,
        recalled = pantryItem.barcode != null && pantryItem.barcode in recalledBarcodes,
        healthConflict = pantryItem.barcode != null && pantryItem.barcode in healthConflictBarcodes,
        onIncrement = { viewModel.updateQuantity(pantryItem.id, pantryItem.quantity + step) },
        onDecrement = { viewModel.updateQuantity(pantryItem.id, (pantryItem.quantity - step).coerceAtLeast(0.0)) },
        onEdit = { editTargetSetter(pantryItem) },
        onDelete = {
            viewModel.delete(pantryItem)
            scope.launch {
                val result = snackbarHostState.showSnackbar(deletedMessage, actionLabel = undoLabel)
                if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
            }
        },
    )
}

@Composable
private fun PantryItemRow(item: PantryItem, recalled: Boolean = false, healthConflict: Boolean = false, onIncrement: () -> Unit, onDecrement: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val urgency = item.expiryUrgency()
    val urgencyColor = when (urgency) {
        PantryExpiryUrgency.EXPIRED -> semanticRed()
        PantryExpiryUrgency.SOON -> semanticAmber()
        else -> OnBackground.copy(0.5f)
    }
    val flagged = recalled || healthConflict
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(CardRadius.CONTROL),
        color = if (flagged) semanticRed().copy(alpha = 0.08f) else PrismFillColor,
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit,
        border = if (flagged) androidx.compose.foundation.BorderStroke(2.dp, semanticRed().copy(alpha = 0.4f)) else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.M),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge, color = OnBackground)
                Text(
                    "${formatQty(item.quantity)} ${item.unit.key}" +
                        (item.expiryDate?.let { " · " + it.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) } ?: ""),
                    style = MaterialTheme.typography.labelSmall, color = urgencyColor,
                )
                // User-requested: flag a stocked item that's since turned out
                // to be recalled - see RecallRepository.observeRecalledBarcodes'
                // own doc comment.
                if (recalled) {
                    Text(
                        stringResource(R.string.pantry_recalled_warning),
                        style = MaterialTheme.typography.labelSmall, color = semanticRed(), fontWeight = FontWeight.SemiBold,
                    )
                }
                // User-requested: retroactive check against the *current*
                // profile's health conditions/allergens, not just whatever
                // was true when this item was scanned - see
                // PantryViewModel.healthConflictBarcodes' own doc comment.
                if (healthConflict) {
                    Text(
                        stringResource(R.string.pantry_health_conflict_warning),
                        style = MaterialTheme.typography.labelSmall, color = semanticRed(), fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDecrement) { Text("−", style = MaterialTheme.typography.titleMedium, color = OnBackground.copy(0.7f)) }
                IconButton(onClick = onIncrement) { Text("+", style = MaterialTheme.typography.titleMedium, color = AccentCoral) }
                IconButton(onClick = onDelete) { Icon(TablerIcons.Trash, stringResource(R.string.common_delete), tint = OnBackground.copy(0.5f)) }
            }
        }
    }
}

private fun formatQty(q: Double): String = if (q == q.toLong().toDouble()) q.toLong().toString() else "%.1f".format(q)
