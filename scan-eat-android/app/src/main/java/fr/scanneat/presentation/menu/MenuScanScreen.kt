package fr.scanneat.presentation.menu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.ArrowLeft
import fr.scanneat.R
import fr.scanneat.presentation.scan.components.CameraPreview
import fr.scanneat.presentation.scan.rememberCameraPermissionState
import fr.scanneat.presentation.ui.theme.*

/** Roadmap N3: "Scanner une carte de restaurant". */
@Composable
fun MenuScanScreen(onBack: () -> Unit, viewModel: MenuScanViewModel = hiltViewModel()) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val cameraPermission = rememberCameraPermissionState()
    var hasCamera by cameraPermission.hasCamera
    var cameraUnavailable by cameraPermission.cameraUnavailable
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold)) {
        when (state) {
            is MenuScanState.Idle -> if (hasCamera && !cameraUnavailable) {
                CameraPreview(
                    onBarcodeDetected = {},
                    onPhotoCaptured = { payload -> viewModel.processImage(payload.base64) },
                    onCameraError = { cameraUnavailable = true },
                    topInset = topInset,
                )
                Row(Modifier.fillMaxWidth().padding(top = topInset + Spacing.M, start = Spacing.M, end = Spacing.M), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, colors = IconButtonDefaults.iconButtonColors(containerColor = SurfaceVariant.copy(alpha = 0.7f))) {
                        Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground)
                    }
                    Spacer(Modifier.width(Spacing.M))
                    Text(stringResource(R.string.menu_scan_hint), style = MaterialTheme.typography.bodyMedium, color = OnBackground)
                }
            } else {
                Column(Modifier.fillMaxSize().padding(Spacing.L), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(stringResource(R.string.receipt_scan_permission_body), color = OnBackground)
                    Spacer(Modifier.height(Spacing.M))
                    if (cameraPermission.hasCameraHardware && !cameraUnavailable) Button(onClick = cameraPermission.requestPermission) { Text(stringResource(R.string.receipt_scan_grant_permission)) }
                    TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
                }
            }
            is MenuScanState.Processing -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = AccentCoral)
            }
            is MenuScanState.Error -> Column(Modifier.fillMaxSize().padding(Spacing.L), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.menu_scan_error), color = OnBackground)
                Button(onClick = viewModel::retry) { Text(stringResource(R.string.menu_scan_retry)) }
            }
            is MenuScanState.Result -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.L),
                contentPadding = PaddingValues(top = topInset + Spacing.M, bottom = Spacing.XXL),
                verticalArrangement = Arrangement.spacedBy(Spacing.S),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) { Icon(TablerIcons.ArrowLeft, stringResource(R.string.common_back), tint = OnBackground) }
                        Text(stringResource(R.string.menu_scan_title), style = MaterialTheme.typography.titleMedium, color = OnBackground)
                    }
                }
                item {
                    ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), color = semanticAmber().copy(alpha = 0.15f)) {
                        Text(stringResource(R.string.menu_scan_disclaimer), style = MaterialTheme.typography.bodySmall, color = OnBackground)
                    }
                }
                if (!state.profileHasConstraints) item { Text(stringResource(R.string.menu_scan_no_profile), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(alpha = 0.7f)) }
                val sorted = state.dishes.sortedByDescending { it.allergens.isNotEmpty() || it.dietConflict != null || it.typicalRecipeAllergens.isNotEmpty() }
                items(sorted, key = { it.text }) { d ->
                    val flagged = d.allergens.isNotEmpty() || d.dietConflict != null
                    ScanEatCard(shape = RoundedCornerShape(CardRadius.CONTROL), color = if (flagged) semanticRed().copy(alpha = 0.12f) else SurfaceVariant.copy(alpha = StandardCardAlpha),
                        verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                        Text(d.text, style = MaterialTheme.typography.bodyMedium, fontWeight = if (flagged) FontWeight.SemiBold else FontWeight.Normal, color = OnBackground)
                        if (d.allergens.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                            Icon(TablerIcons.AlertTriangle, null, tint = semanticRed(), modifier = Modifier.size(IconSize.Inline))
                            Text(stringResource(R.string.menu_scan_allergens, d.allergens.joinToString(", ")), style = MaterialTheme.typography.bodySmall, color = semanticRed())
                        }
                        d.dietConflict?.let { Text(stringResource(R.string.menu_scan_diet, it), style = MaterialTheme.typography.bodySmall, color = semanticAmber()) }
                        if (d.typicalRecipeAllergens.isNotEmpty()) Text(stringResource(R.string.menu_scan_typical_allergens, d.typicalRecipeAllergens.joinToString(", ")),
                            style = MaterialTheme.typography.bodySmall, color = semanticAmber())
                        d.estimate?.let { e -> Text(stringResource(R.string.menu_scan_estimate, e.grade, e.score, e.basis), style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f)) }
                    }
                }
                item { TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.menu_scan_retry)) } }
            }
        }
    }
}
