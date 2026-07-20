package fr.scanneat.presentation.scan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.domain.engine.medication.generateMedicationHints
import fr.scanneat.domain.engine.nonconsumable.generateNonConsumableHints
import fr.scanneat.presentation.result.FactsCautionsColumn
import fr.scanneat.presentation.scan.components.CameraPreview
import fr.scanneat.presentation.scan.components.ManualBarcodeEntry
import fr.scanneat.presentation.scan.components.NoCameraFallback
import fr.scanneat.presentation.ui.theme.*

@Composable
fun ScanScreen(
    viewModel: ScanViewModel = hiltViewModel(),
    onResultReady: (Long) -> Unit,
    // These are kept for callers that may still pass them, but ignored — MainShell owns the nav
    onOpenDiary: () -> Unit = {},
    onOpenDashboard: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic      = LocalHapticFeedback.current
    val state       = viewModel.state.collectAsStateWithLifecycle()
    val images      = viewModel.images.collectAsStateWithLifecycle()
    val barcode     = viewModel.scannedBarcode.collectAsStateWithLifecycle()
    val instantMode = viewModel.instantMode.collectAsStateWithLifecycle()
    val language    = viewModel.language.collectAsStateWithLifecycle()
    val healthConditions = viewModel.healthConditions.collectAsStateWithLifecycle()
    val recentBarcodes = viewModel.recentBarcodes.collectAsStateWithLifecycle()
    val todayScanCount = viewModel.todayScanCount.collectAsStateWithLifecycle()
    val cachedPreview  = viewModel.cachedPreview.collectAsStateWithLifecycle()
    val captureErrorMessage = stringResource(R.string.scan_capture_error)

    // android:required="false" on both camera <uses-feature> entries in the manifest
    // (see AndroidManifest.xml) tells the Play Store this app installs fine on devices
    // with no camera at all (some tablets/Chromebooks/emulators). Requesting the CAMERA
    // *permission* on such a device still "succeeds" trivially - there's simply no
    // hardware behind it - so hasCamera below would stay true forever while
    // CameraPreview's bindToLifecycle silently fails every time. Checking the actual
    // hardware feature up front lets these devices skip straight to a usable fallback
    // instead of a dead permission prompt.
    val hasCameraHardware = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    var hasCamera by remember {
        mutableStateOf(
            hasCameraHardware &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    // Set when CameraPreview's own bindToLifecycle call fails (e.g. camera held by
    // another app, or a hardware/driver fault) - previously swallowed by a bare
    // runCatching with no onFailure branch, leaving a permanently blank preview and a
    // capture button that silently did nothing, with zero feedback for the user.
    var cameraUnavailable by remember { mutableStateOf(false) }
    var manualEntryOpen by remember { mutableStateOf(false) }
    // Once the user permanently denies (checked "don't ask again", or a 2nd
    // straight denial on API 30+), RequestPermission() silently returns false
    // without even showing the system dialog again — "Autoriser" would look
    // broken forever with no way to reach the scanner, the app's core
    // feature. Track a request having already happened once, so a denial
    // with no rationale available next time is recognized as permanent.
    var requestedOnce by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamera = granted
        if (!granted) {
            val activity = context as? Activity
            val canShowRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
            } ?: true
            if (requestedOnce && !canShowRationale) permanentlyDenied = true
        }
        requestedOnce = true
    }

    // hasCamera was only ever updated by the permission-request launcher's own
    // callback - revoking Camera permission from system Settings while this
    // screen is backgrounded left it stuck true, so CameraPreview's next bind
    // attempt threw a SecurityException that got misclassified as a hardware/
    // driver fault (cameraUnavailable=true), trapping the user in a "Retry"
    // loop that could never succeed since the real problem (missing permission)
    // was never rechecked and the permission-request UI was never re-shown.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = hasCameraHardware &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (granted != hasCamera) {
                    hasCamera = granted
                    cameraUnavailable = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.value) {
        val s = state.value
        if (s is ScanUiState.Success) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onResultReady(s.persistedId)
            viewModel.resultConsumed()
        }
    }

    // Haptic tick when a barcode first appears in frame
    LaunchedEffect(barcode.value) {
        if (barcode.value != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // No Scaffold here — MainShell provides the outer Scaffold + NavigationBar.
    // Full-bleed *within the safe content area*: MainShell's own Scaffold already
    // consumes systemBars insets (contentWindowInsets) before AppNavGraph renders,
    // so this doesn't actually extend behind the status/nav bars — but within the
    // space it does get, the camera preview is the base layer for the whole tab;
    // header, photo queue, error banner, and both action buttons float on top of
    // it instead of sharing the screen as stacked siblings (the previous layout
    // left the camera only the leftover space between a header row and a
    // separate button row below it).
    Box(modifier = Modifier.fillMaxSize().ambientGloom(base = Background, primary = AccentCoral, secondary = Gold)) {
        // barcodeBounds: (rect, rotatedImgW, rotatedImgH) — updated on every frame that contains a barcode
        var barcodeBounds by remember { mutableStateOf<Triple<android.graphics.Rect, Int, Int>?>(null) }

        if (hasCamera && !cameraUnavailable) {
            CameraPreview(
                onBarcodeDetected = { viewModel.onBarcodeDetected(it) },
                onPhotoCaptured   = { viewModel.addPhoto(it) },
                onCameraError     = { cameraUnavailable = true },
                // A capture failure is transient (unlike a bind failure) - don't drop into
                // the full manual-entry fallback, just surface it so the shutter button
                // doesn't look silently broken.
                onCaptureError    = { Toast.makeText(context, captureErrorMessage, Toast.LENGTH_SHORT).show() },
                onBarcodeBounds   = { rect, w, h -> barcodeBounds = Triple(rect, w, h) },
                onBoundsCleared   = { barcodeBounds = null; viewModel.onBarcodeLost() },
            )
        } else if (!hasCameraHardware) {
            // Camera-less device (manifest declares both <uses-feature> entries
            // required="false") - a permission prompt here would be pointless theater,
            // so this goes straight to the one input path that still works: manual entry.
            NoCameraFallback(
                titleRes = R.string.scan_no_camera_title,
                bodyRes  = R.string.scan_no_camera_body,
                // quickScan(), not onBarcodeDetected()+score() - the latter's photo-queue
                // guard (meant only to reject an *incidental* live-camera barcode while
                // photos are queued) also silently swallowed this deliberately typed
                // barcode whenever photos were already queued, since onBarcodeDetected()
                // no-ops in that case and score() then ran with barcode=null. quickScan()
                // already exists for exactly this "explicit, deliberate entry" case (see
                // its use for the recent-barcode chips below) and bypasses that guard.
                onSubmit = { viewModel.quickScan(it) },
            )
        } else if (cameraUnavailable) {
            NoCameraFallback(
                titleRes = R.string.scan_camera_unavailable_title,
                bodyRes  = R.string.scan_camera_unavailable_body,
                onSubmit = { viewModel.quickScan(it) },
                onRetry  = { cameraUnavailable = false },
            )
        } else {
            // Camera permission request UI — no camera feed behind it, so this fills the screen itself.
            Column(
                modifier = Modifier.fillMaxSize().padding(Spacing.XXL),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.CameraAlt, null, tint = OnBackground, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(Spacing.L))
                Text(stringResource(R.string.scan_camera_permission_title), style = MaterialTheme.typography.titleMedium,
                    color = OnBackground, textAlign = TextAlign.Center)
                Spacer(Modifier.height(Spacing.S))
                Text(stringResource(R.string.camera_permission_rationale),
                    style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f), textAlign = TextAlign.Center)
                Spacer(Modifier.height(Spacing.XL))
                if (permanentlyDenied) {
                    ScanEatPrimaryButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            )
                        },
                    ) {
                        Text(stringResource(R.string.scan_open_settings_button))
                    }
                } else {
                    ScanEatPrimaryButton(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.common_allow))
                    }
                }
                Spacer(Modifier.height(Spacing.L))
                if (!manualEntryOpen) {
                    TextButton(onClick = { manualEntryOpen = true }) {
                        Text(stringResource(R.string.scan_manual_entry_toggle), color = OnBackground.copy(0.8f))
                    }
                } else {
                    ManualBarcodeEntry(onSubmit = { viewModel.quickScan(it) })
                }
            }
        }

        if (hasCamera && !cameraUnavailable) {
            // ── Header — top-start, scrimmed so it stays legible over any camera scene ──
            Column(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopStart)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(0.55f), Color.Transparent)))
                    .padding(horizontal = 20.dp).padding(top = Spacing.L, bottom = 28.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                    // New: today's scan count badge — previously there was no way to know how
                    // many products you'd already scanned today without leaving the scan tab.
                    if (todayScanCount.value > 0) {
                        Surface(shape = RoundedCornerShape(50), color = AccentCoral.copy(0.85f)) {
                            Text(
                                "${todayScanCount.value}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                // Improvement: state-aware subtitle instead of static hint — previously the
                // text never changed between Idle, Scanning, and photos-queued-no-barcode states,
                // so users had no text feedback that analysis was happening or what to do next.
                Text(
                    when {
                        state.value is ScanUiState.Scanning -> stringResource(R.string.scan_analyzing)
                        barcode.value != null -> stringResource(R.string.scan_barcode_prefix, barcode.value!!)
                        images.value.isNotEmpty() && barcode.value == null -> stringResource(R.string.scan_hint_photos_ready)
                        else -> stringResource(R.string.scan_hint)
                    },
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.8f),
                )
            }

            barcode.value?.let { bc ->
                Box(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp)
                        .glassSheen(edgeAlpha = 0.22f, shape = RoundedCornerShape(24.dp), glowTint = AccentCoral, glowAlpha = 0.07f),
                ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = SurfaceVariant.copy(0.9f),
                ) {
                    Row(Modifier.padding(horizontal = Spacing.L, vertical = Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QrCodeScanner, null, tint = AccentCoral, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.S))
                        Text(bc, style = MaterialTheme.typography.labelLarge, color = OnSurface, fontWeight = FontWeight.Medium)
                        // "Already scanned this" cue — the local-cache lookup
                        // scoreBarcode() already does to skip the network on a
                        // rescan, surfaced here for the first time so the user
                        // sees it's a known product before even tapping the score
                        // FAB, instead of only finding out after the round-trip.
                        cachedPreview.value?.takeIf { it.barcode == bc }?.let { cached ->
                            Spacer(Modifier.width(Spacing.S))
                            Surface(shape = RoundedCornerShape(12.dp), color = gradeColor(cached.audit.grade).copy(alpha = 0.2f)) {
                                Text(
                                    "${cached.audit.score} ${cached.audit.grade.label}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = gradeColor(cached.audit.grade),
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = Spacing.S, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
                }
            }

            // ── Photo queue — floats below the header, distinct corner from the button cluster ──
            if (images.value.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopStart).padding(top = 88.dp)
                        .padding(horizontal = Spacing.L),
                ) {
                    Box(Modifier.glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(10.dp))) {
                    Surface(shape = RoundedCornerShape(10.dp), color = Background.copy(0.7f)) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(pluralStringResource(R.plurals.scan_photo_count, images.value.size, images.value.size), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.8f))
                            Spacer(Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                                itemsIndexed(images.value) { index, payload ->
                                    Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, OnSurface.copy(0.2f), RoundedCornerShape(8.dp))) {
                                        val bmp = remember(payload) { payload.thumbnail }
                                        if (bmp != null) {
                                            Image(bitmap = bmp.asImageBitmap(), contentDescription = stringResource(R.string.scan_photo_index, index + 1),
                                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        } else {
                                            Box(Modifier.fillMaxSize().background(SurfaceVariant), contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Image, null, tint = OnSurface.copy(0.5f), modifier = Modifier.size(IconSize.Inline))
                                            }
                                        }
                                        IconButton(onClick = { viewModel.removePhoto(index) },
                                            modifier = Modifier.align(Alignment.TopEnd).size(20.dp).background(Background.copy(0.6f), CircleShape)) {
                                            Icon(Icons.Default.Close, stringResource(R.string.common_remove), tint = OnSurface, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }

            // ── Bounding box overlay — drawn in image→screen mapped coordinates ──
            barcodeBounds?.let { (rect, imgW, imgH) ->
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val scaleX = size.width / imgW.toFloat()
                    val scaleY = size.height / imgH.toFloat()
                    val scale  = maxOf(scaleX, scaleY)
                    val offX   = (size.width  - imgW  * scale) / 2f
                    val offY   = (size.height - imgH * scale) / 2f
                    val left   = offX + rect.left   * scale
                    val top    = offY + rect.top    * scale
                    val right  = offX + rect.right  * scale
                    val bottom = offY + rect.bottom * scale
                    drawRoundRect(
                        color        = AccentCoral,
                        topLeft      = Offset(left, top),
                        size         = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                        cornerRadius = CornerRadius(8f, 8f),
                        style        = Stroke(width = 3f),
                        alpha        = 0.85f,
                    )
                }
            }

            // ── Score FAB — bottom-end ──
            FloatingActionButton(
                onClick = { viewModel.score() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
                containerColor = AccentCoral,
                shape = CircleShape,
            ) {
                // Exhaustive over all 6 ScanUiState variants (no `else`) - a future
                // 7th variant now fails to compile here instead of silently falling
                // through to the generic search icon unnoticed.
                when (state.value) {
                    is ScanUiState.Scanning -> CircularProgressIndicator(
                        color = Color.Black, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    is ScanUiState.Idle, is ScanUiState.Success, is ScanUiState.Error,
                    is ScanUiState.MedicationFound, is ScanUiState.NonConsumableFound ->
                        Icon(Icons.Default.Search, stringResource(R.string.scan_cd_scan), tint = Color.Black)
                }
            }

            // ── Identify-without-label action — only relevant with photos queued and
            // no barcode held (fresh produce, a plated dish: nothing to OCR a label
            // from). Routes to OcrParser.identifyFood, which previously had no caller
            // anywhere in the app despite already being implemented. ──
            if (images.value.isNotEmpty() && barcode.value == null && state.value !is ScanUiState.Scanning) {
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 84.dp, bottom = 28.dp)
                        .glassSheen(edgeAlpha = 0.20f, shape = RoundedCornerShape(CardRadius.PROMINENT), glowTint = AccentCoral, glowAlpha = 0.06f),
                ) {
                Surface(
                    shape = RoundedCornerShape(CardRadius.PROMINENT),
                    color = SurfaceVariant.copy(0.9f),
                    onClick = { viewModel.identifyFromPhotos() },
                ) {
                    Row(Modifier.padding(horizontal = Spacing.M, vertical = Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Fastfood, null, tint = AccentCoral, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.scan_identify_food_button), style = MaterialTheme.typography.labelSmall, color = OnSurface)
                    }
                }
                }
            }

            // ── Recent barcodes quick-rescan chips — bottom-start, above instant FAB ──
            if (recentBarcodes.value.isNotEmpty() && state.value is ScanUiState.Idle) {
                Column(
                    modifier = Modifier.align(Alignment.BottomStart)
                        .padding(start = 20.dp, bottom = 84.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    recentBarcodes.value.takeLast(3).reversed().forEach { bc ->
                        Box(Modifier.glassSheen(edgeAlpha = 0.12f, shape = RoundedCornerShape(20.dp), glowAlpha = 0f, reliefAlpha = 0f)) {
                        Surface(
                            onClick = { viewModel.quickScan(bc) },
                            shape = RoundedCornerShape(20.dp),
                            color = SurfaceVariant.copy(0.85f),
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.History, null, tint = AccentCoral, modifier = Modifier.size(12.dp))
                                Text(bc, style = MaterialTheme.typography.labelSmall, color = OnSurface.copy(0.85f))
                            }
                        }
                        }
                    }
                }
            }

            // ── Instant mode FAB — bottom-start ──
            FloatingActionButton(
                onClick = { viewModel.toggleInstantMode() },
                modifier       = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 20.dp),
                containerColor = if (instantMode.value) AccentCoral else SurfaceVariant,
                shape          = CircleShape,
            ) {
                Icon(Icons.Default.Bolt, stringResource(R.string.scan_instant_toggle), tint = if (instantMode.value) Color.Black else OnSurface)
            }

        }

        // ── Result of the last scan attempt — a single exhaustive `when` over all 6
        // ScanUiState variants (no `else`), so a future 7th variant fails to compile
        // here instead of silently rendering nothing. Previously scattered across an
        // `if (hasCamera...) {...} else if (state.value is Error) {...}` pair (each
        // half re-deriving its own `error` via an `as` cast) plus two independent
        // `(state.value as? X)?.let{}` dialog checks below the Box. Kept inside Box (not moved
        // after it) because the Error banners still need BoxScope's Modifier.align;
        // the two dialogs don't need it but sit here too now for one single dispatch
        // point - AlertDialog renders in its own window regardless of tree position,
        // so this is a pure dispatch-structure change with identical rendered output. ──
        when (val s = state.value) {
            is ScanUiState.Idle, is ScanUiState.Scanning, is ScanUiState.Success -> Unit
            is ScanUiState.Error -> {
                if (hasCamera && !cameraUnavailable) {
                    if (s.needsPhoto) {
                        Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = Spacing.L, end = Spacing.L, bottom = 96.dp),
                            color = SurfaceVariant, shape = RoundedCornerShape(CardRadius.CONTROL)) {
                            Row(Modifier.padding(Spacing.M), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CameraAlt, null, tint = AccentCoral)
                                Spacer(Modifier.width(Spacing.S))
                                Text(stringResource(R.string.scan_needs_photo),
                                    Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = OnSurface)
                                IconButton(onClick = { viewModel.dismissError() }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Close, stringResource(R.string.common_close), tint = OnSurface)
                                }
                            }
                        }
                    } else {
                        ErrorBanner(
                            message     = s.message,
                            modifier    = Modifier.align(Alignment.BottomCenter).padding(start = Spacing.L, end = Spacing.L, bottom = 96.dp),
                            actionLabel = stringResource(R.string.common_retry),
                            onAction    = { viewModel.score() },
                            onDismiss   = { viewModel.dismissError() },
                        )
                    }
                } else {
                    // Same error surface as the camera path above, but reachable from the
                    // no-camera/camera-unavailable fallbacks too - those flows call
                    // viewModel.score() straight from manual barcode entry, with no FAB or
                    // camera preview underneath, so a scoring failure there still needs
                    // somewhere to show up instead of silently going nowhere.
                    ErrorBanner(
                        message     = s.message,
                        modifier    = Modifier.align(Alignment.BottomCenter).padding(start = Spacing.L, end = Spacing.L, bottom = 24.dp),
                        actionLabel = stringResource(R.string.common_retry),
                        onAction    = { viewModel.score() },
                        onDismiss   = { viewModel.dismissError() },
                    )
                }
            }
            is ScanUiState.MedicationFound -> {
                val hints = remember(s.entry, language.value, healthConditions.value) {
                    generateMedicationHints(s.entry, healthConditions.value, language.value)
                }
                AlertDialog(
                    onDismissRequest = { viewModel.dismissFound() },
                    containerColor = SurfaceVariant,
                    title = { Text(stringResource(R.string.scan_medication_found_title), color = OnBackground) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            Text(stringResource(R.string.scan_medication_found_body, s.entry.name), color = OnBackground.copy(0.7f))
                            FactsCautionsColumn(hints.facts, hints.cautions)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.saveDetectedMedication(s.entry) }) {
                            Text(stringResource(R.string.scan_medication_found_add), color = Teal)
                        }
                    },
                    dismissButton = { TextButton(onClick = { viewModel.dismissFound() }) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
                )
            }
            is ScanUiState.NonConsumableFound -> {
                val hints = remember(s.entry, language.value) { generateNonConsumableHints(s.entry.category, language.value) }
                AlertDialog(
                    onDismissRequest = { viewModel.dismissFound() },
                    containerColor = SurfaceVariant,
                    title = { Text(stringResource(R.string.scan_nonconsumable_found_title), color = OnBackground) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            Text(stringResource(R.string.scan_nonconsumable_found_body, s.entry.name, s.entry.brand), color = OnBackground.copy(0.8f))
                            Text(stringResource(R.string.scan_nonconsumable_safety_line), color = semanticRed(), fontWeight = FontWeight.SemiBold)
                            FactsCautionsColumn(hints.facts, hints.cautions)
                        }
                    },
                    confirmButton = { TextButton(onClick = { viewModel.dismissFound() }) { Text(stringResource(R.string.common_close), color = AccentCoral) } },
                )
            }
        }
    }
}

