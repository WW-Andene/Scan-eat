package fr.scanneat.presentation.expenses

import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.FileInvoice
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.presentation.scan.components.CameraPreview
import fr.scanneat.presentation.scan.rememberCameraPermissionState
import fr.scanneat.presentation.ui.theme.*

/**
 * User-requested: photograph a purchase receipt and pull out (name, price)
 * lines to log in one pass, instead of typing each line into Dépenses by
 * hand. Reuses ScanScreen's own CameraPreview (its built-in shutter FAB
 * delivers the captured photo via onPhotoCaptured) and ML Kit's on-device
 * text recognizer (see ReceiptScanViewModel.processImage). Every extracted
 * line always lands in an editable review list before anything is saved -
 * receipt OCR misreads item names and drops/merges lines often enough that
 * auto-logging without review would silently corrupt price history.
 */
@Composable
fun ReceiptScanScreen(onBack: () -> Unit, viewModel: ReceiptScanViewModel = hiltViewModel()) {
    val state = viewModel.state.collectAsStateWithLifecycle()
    val cameraPermission = rememberCameraPermissionState()
    val hasCameraHardware = cameraPermission.hasCameraHardware
    var hasCamera by cameraPermission.hasCamera
    var cameraUnavailable by cameraPermission.cameraUnavailable
    val permanentlyDenied by cameraPermission.permanentlyDenied
    val context = LocalContext.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LaunchedEffect(state.value) {
        if (state.value is ReceiptScanState.Done) onBack()
    }

    Box(Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold)) {
        val s = state.value
        when {
            s is ReceiptScanState.Idle && hasCamera && !cameraUnavailable -> {
                CameraPreview(
                    onBarcodeDetected = {},
                    onPhotoCaptured = { payload -> viewModel.processImage(payload.base64) },
                    onCameraError = { cameraUnavailable = true },
                    topInset = topInset,
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = topInset + Spacing.M, start = Spacing.M, end = Spacing.M),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack, colors = IconButtonDefaults.iconButtonColors(containerColor = SurfaceVariant.copy(alpha = 0.7f))) {
                        Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground)
                    }
                    Spacer(Modifier.width(Spacing.M))
                    Text(stringResource(R.string.receipt_scan_hint), style = MaterialTheme.typography.bodyMedium, color = OnBackground)
                }
            }
            s is ReceiptScanState.Idle && (!hasCameraHardware || cameraUnavailable) -> {
                Column(
                    Modifier.fillMaxSize().padding(Spacing.L),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Rounded.CameraAlt, null, tint = OnBackground.copy(0.5f), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(Spacing.M))
                    Text(stringResource(R.string.receipt_scan_no_camera_body), style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.8f))
                    Spacer(Modifier.height(Spacing.L))
                    TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
                }
            }
            s is ReceiptScanState.Idle -> {
                // Camera hardware present but permission not granted (or not yet asked) -
                // same request-button pattern as ScanScreen's own ScanPermissionRequestColumn.
                Column(
                    Modifier.fillMaxSize().padding(Spacing.L),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Rounded.CameraAlt, null, tint = OnBackground.copy(0.5f), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(Spacing.M))
                    Text(stringResource(R.string.receipt_scan_permission_body), style = MaterialTheme.typography.bodyMedium, color = OnBackground.copy(0.8f))
                    Spacer(Modifier.height(Spacing.L))
                    Button(
                        onClick = if (permanentlyDenied) {
                            {
                                context.startActivity(
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        android.net.Uri.fromParts("package", context.packageName, null),
                                    )
                                )
                            }
                        } else cameraPermission.requestPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCoral),
                    ) { Text(stringResource(if (permanentlyDenied) R.string.scan_open_settings_button else R.string.receipt_scan_grant_permission)) }
                    Spacer(Modifier.height(Spacing.S))
                    TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
                }
            }
            s is ReceiptScanState.Processing -> {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = AccentCoral)
                    Spacer(Modifier.height(Spacing.M))
                    Text(stringResource(R.string.receipt_scan_processing), color = OnBackground)
                }
            }
            s is ReceiptScanState.NoLinesFound || s is ReceiptScanState.Error -> {
                Column(
                    Modifier.fillMaxSize().padding(Spacing.L),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(TablerIcons.FileInvoice, null, tint = OnBackground.copy(0.5f), modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(Spacing.M))
                    Text(
                        stringResource(if (s is ReceiptScanState.NoLinesFound) R.string.receipt_scan_no_lines_body else R.string.receipt_scan_error_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnBackground.copy(0.8f),
                    )
                    Spacer(Modifier.height(Spacing.L))
                    Button(onClick = { viewModel.retry() }, colors = ButtonDefaults.buttonColors(containerColor = AccentCoral)) {
                        Text(stringResource(R.string.receipt_scan_retry))
                    }
                    Spacer(Modifier.height(Spacing.S))
                    TextButton(onClick = onBack) { Text(stringResource(R.string.common_cancel)) }
                }
            }
            s is ReceiptScanState.Review -> {
                ReceiptReviewList(lines = s.lines, onBack = onBack, viewModel = viewModel)
            }
            else -> {}
        }
    }
}

@Composable
private fun ReceiptReviewList(lines: List<ReceiptReviewLine>, onBack: () -> Unit, viewModel: ReceiptScanViewModel) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(Modifier.fillMaxSize().padding(top = topInset)) {
        Row(
            Modifier.fillMaxWidth().padding(Spacing.L),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) }
            Spacer(Modifier.width(Spacing.S))
            Text(stringResource(R.string.receipt_scan_review_title), style = MaterialTheme.typography.titleMedium, color = OnBackground)
        }
        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = Spacing.L), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            items(lines, key = { it.id }) { line ->
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = line.included, onCheckedChange = { viewModel.toggleIncluded(line.id) }, colors = CheckboxDefaults.colors(checkedColor = AccentCoral))
                        Column(Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = line.name,
                                onValueChange = { viewModel.updateLine(line.id, it, line.priceEuros) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (line.matched) {
                                Text(stringResource(R.string.receipt_scan_matched), style = MaterialTheme.typography.labelSmall, color = AccentCoral)
                            }
                        }
                        Spacer(Modifier.width(Spacing.S))
                        // Was `value = "%.2f".format(line.priceEuros)` bound directly to
                        // the parsed Double - a classic controlled-field trap: every
                        // keystroke re-derived the displayed text from the just-parsed
                        // value, snapping back to a 2-decimal-formatted string and
                        // fighting the user's own typing/cursor position (e.g. typing
                        // "3" then trying to add ".50" got reformatted to "3.00"
                        // immediately). A local text buffer decouples what's on screen
                        // from the formatted Double, only pushing a re-parse to the
                        // ViewModel on each change - same "type freely, parse on the
                        // side" shape as every other price field in this app
                        // (ExpensesDialogs.kt's AddExpenseDialog/EditExpenseDialog).
                        var priceText by remember(line.id) { mutableStateOf("%.2f".format(line.priceEuros)) }
                        OutlinedTextField(
                            value = priceText,
                            onValueChange = { priceText = it; it.replace(',', '.').toDoubleOrNull()?.let { p -> viewModel.updateLine(line.id, line.name, p) } },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.width(84.dp),
                        )
                    }
                    // User-requested: when a line doesn't exactly match a known
                    // product, offer suppositions from price history to pick
                    // from instead of leaving the OCR'd spelling as-is - see
                    // ReceiptScanViewModel.suggestionsFor's own doc comment.
                    // Never applied automatically, and offset past the
                    // checkbox column so it visually belongs to the name field.
                    if (line.suggestions.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 48.dp, top = Spacing.XS),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.XS),
                        ) {
                            line.suggestions.forEach { suggestion ->
                                AssistChip(
                                    onClick = { viewModel.applySuggestion(line.id, suggestion) },
                                    label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) },
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(Spacing.XXL)) }
        }
        Button(
            onClick = { viewModel.confirmAll() },
            enabled = lines.any { it.included },
            colors = ButtonDefaults.buttonColors(containerColor = AccentCoral),
            modifier = Modifier.fillMaxWidth().padding(Spacing.L),
        ) {
            Text(stringResource(R.string.receipt_scan_confirm, lines.count { it.included }))
        }
    }
}
