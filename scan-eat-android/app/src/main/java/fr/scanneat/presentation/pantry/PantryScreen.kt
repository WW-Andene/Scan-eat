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
import fr.scanneat.presentation.pantry.components.AddPantryItemDialog
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

/**
 * User-requested: a real persisted pantry inventory - see
 * PantryViewModel/PantryEntity's own doc comments. Deliberately simple list
 * screen (no category grouping like FoodSearch, no shopping-list checkbox
 * flow like Courses) since this is a small, glanceable "what do I have and
 * is it about to expire" view, not a browsing tool.
 */
@Composable
fun PantryScreen(viewModel: PantryViewModel = hiltViewModel(), onBack: () -> Unit) {
    val items = viewModel.items.collectAsStateWithLifecycle()
    val expiringItems = viewModel.expiringItems.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
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

            if (items.value.isEmpty()) {
                item {
                    EmptyListState(TablerIcons.ShoppingCart, stringResource(R.string.pantry_empty))
                }
            } else {
                items(items.value, key = { it.id }) { pantryItem ->
                    PantryItemRow(
                        item = pantryItem,
                        onIncrement = { viewModel.updateQuantity(pantryItem.id, pantryItem.quantity + 1) },
                        onDecrement = { viewModel.updateQuantity(pantryItem.id, (pantryItem.quantity - 1).coerceAtLeast(0.0)) },
                        onDelete = {
                            viewModel.delete(pantryItem)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(deletedMessage, actionLabel = undoLabel)
                                if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
                            }
                        },
                    )
                }
            }
            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
    }

    if (showAdd) {
        AddPantryItemDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, quantity, unit, expiryDate ->
                viewModel.add(name, barcode = null, category = fr.scanneat.domain.model.ProductCategory.OTHER, quantity = quantity, unit = unit, expiryDate = expiryDate)
                showAdd = false
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
        border = androidx.compose.foundation.BorderStroke(1.dp, semanticAmber().copy(alpha = 0.35f)),
    ) {
        Row(modifier = Modifier.padding(Spacing.M), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Icon(TablerIcons.AlertTriangle, null, tint = semanticAmber(), modifier = Modifier.size(18.dp))
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
private fun PantryItemRow(item: PantryItem, onIncrement: () -> Unit, onDecrement: () -> Unit, onDelete: () -> Unit) {
    val urgency = item.expiryUrgency()
    val urgencyColor = when (urgency) {
        PantryExpiryUrgency.EXPIRED -> semanticRed()
        PantryExpiryUrgency.SOON -> semanticAmber()
        else -> OnBackground.copy(0.5f)
    }
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(CardRadius.CONTROL),
        color = SurfaceVariant.copy(alpha = StandardCardAlpha),
        modifier = Modifier.fillMaxWidth(),
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
