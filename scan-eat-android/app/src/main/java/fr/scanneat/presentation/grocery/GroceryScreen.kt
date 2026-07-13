package fr.scanneat.presentation.grocery

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun GroceryScreen(
    viewModel: GroceryViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val items     = viewModel.groceryItems.collectAsStateWithLifecycle()
    val checkable = viewModel.checkableItems.collectAsStateWithLifecycle()
    val manualItemKeys = viewModel.manualItemKeys.collectAsStateWithLifecycle()
    val itemWarnings = viewModel.itemWarnings.collectAsStateWithLifecycle()
    val scopeToPlanned = viewModel.scopeToPlanned.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val haptics   = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.grocery_copied)
    val clearedMessage = stringResource(R.string.grocery_cleared_confirmation)
    var copyMenuExpanded by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.grocery_title), color = OnBackground) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = OnBackground) } },
                actions = {
                    if (checkable.value.any { it.checked }) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Default.RemoveDone, stringResource(R.string.grocery_clear_checked), tint = OnBackground.copy(0.7f))
                        }
                    }
                    if (items.value.isNotEmpty()) {
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        containerColor = Background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
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
                item { Spacer(Modifier.height(4.dp)) }
                item {
                    Text(pluralStringResource(R.plurals.grocery_item_count, items.value.size, items.value.size),
                        style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
                }
                items(checkable.value, key = { it.item.key }) { checkableItem ->
                    val item = checkableItem.item
                    val checked = checkableItem.checked
                    val contentAlpha by animateFloatAsState(if (checked) 0.5f else 1f, label = "groceryItemAlpha")
                    Box(Modifier.fillMaxWidth().glassSheen(edgeAlpha = 0.14f, shape = RoundedCornerShape(CardRadius.CONTROL))) {
                        Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = Spacing.XS, vertical = Spacing.XS), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.toggleChecked(item, it)
                                    },
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
                                    itemWarnings.value[item.key]?.let {
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
                                if (item.key in manualItemKeys.value) {
                                    IconButton(onClick = { viewModel.deleteManualContribution(item.key) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Close, stringResource(R.string.common_delete), tint = OnSurface.copy(0.4f), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
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
