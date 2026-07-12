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
        if (items.value.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyListState(icon = Icons.Default.ShoppingCart, message = stringResource(R.string.grocery_empty_body))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                item {
                    Text(stringResource(R.string.grocery_item_count, items.value.size),
                        style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.5f))
                }
                items(checkable.value, key = { it.item.name }) { checkableItem ->
                    val item = checkableItem.item
                    val checked = checkableItem.checked
                    val contentAlpha by animateFloatAsState(if (checked) 0.5f else 1f, label = "groceryItemAlpha")
                    Box(Modifier.fillMaxWidth().glassSheen(edgeAlpha = 0.14f, shape = RoundedCornerShape(10.dp))) {
                        Surface(shape = RoundedCornerShape(10.dp), color = SurfaceVariant, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
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
                                }
                                if (item.grams > 0) {
                                    Text(stringResource(R.string.grocery_grams, item.grams), style = MaterialTheme.typography.labelLarge,
                                        color = AccentCoral.copy(contentAlpha), fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(end = 12.dp))
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
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
