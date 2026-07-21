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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.data.remote.api.ImagePayload
import fr.scanneat.domain.engine.medication.generateMedicationHints
import fr.scanneat.domain.engine.nonconsumable.generateNonConsumableHints
import fr.scanneat.domain.model.ScanResult
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

    // No Scaffold here — Scan is a genuine TOP_TAB, so MainShell's own floating
    // bottom nav renders on top of this whole screen exactly like it does over
    // every other tab. MainShell itself is a plain Box now (no Scaffold, no
    // contentWindowInsets consumption) — every other screen either goes through
    // FloatingScreenScaffold (which handles its own status-bar/bottom-nav insets)
    // or, like this one, needs to do it by hand since the camera preview is the
    // base layer for the whole tab with everything else floating on top of it
    // rather than sharing the screen as stacked siblings. topInset/bottomNavClearance
    // below are that hand-rolled equivalent — omitting them would let the header sit
    // under the status bar and the FABs/banners sit directly behind the floating nav.
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomNavClearance = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + FloatingBottomNavHeight
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
                bottomNavClearance = bottomNavClearance,
                topInset          = topInset,
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
            ScanPermissionRequestColumn(
                permanentlyDenied = permanentlyDenied,
                manualEntryOpen = manualEntryOpen,
                onOpenAppSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                },
                onRequestPermission = { permLauncher.launch(Manifest.permission.CAMERA) },
                onOpenManualEntry = { manualEntryOpen = true },
                onQuickScan = { viewModel.quickScan(it) },
            )
        }

        if (hasCamera && !cameraUnavailable) {
            // ── Header — top-start, scrimmed so it stays legible over any camera scene ──
            ScanHeaderBar(
                topInset = topInset,
                todayScanCount = todayScanCount.value,
                isScanning = state.value is ScanUiState.Scanning,
                barcode = barcode.value,
                hasQueuedPhotosNoBarcode = images.value.isNotEmpty() && barcode.value == null,
            )

            barcode.value?.let { bc -> ScanBarcodeChip(barcode = bc, topInset = topInset, cachedPreview = cachedPreview.value) }

            // ── Photo queue — floats below the header, distinct corner from the button cluster ──
            if (images.value.isNotEmpty()) {
                ScanPhotoQueue(images = images.value, topInset = topInset, onRemovePhoto = { viewModel.removePhoto(it) })
            }

            // ── Bounding box overlay — drawn in image→screen mapped coordinates ──
            barcodeBounds?.let { (rect, imgW, imgH) -> ScanBoundingBoxOverlay(rect, imgW, imgH) }

            // ── Score FAB — bottom-end ──
            ScanScoreFab(scanState = state.value, bottomNavClearance = bottomNavClearance, onClick = { viewModel.score() })

            // ── Identify-without-label action — only relevant with photos queued and
            // no barcode held (fresh produce, a plated dish: nothing to OCR a label
            // from). Routes to OcrParser.identifyFood, which previously had no caller
            // anywhere in the app despite already being implemented. ──
            if (images.value.isNotEmpty() && barcode.value == null && state.value !is ScanUiState.Scanning) {
                ScanIdentifyFoodAction(
                    bottomNavClearance = bottomNavClearance,
                    onClick = { viewModel.identifyFromPhotos() },
                    // Long-press: same photos, but routes to /api/identify-multi so a
                    // plate with several distinct foods returns one item per food
                    // instead of collapsing the whole plate into a single result.
                    onLongClick = { viewModel.identifyMultiFromPhotos() },
                )
            }

            // ── Recent barcodes quick-rescan chips — bottom-start, above instant FAB ──
            if (recentBarcodes.value.isNotEmpty() && state.value is ScanUiState.Idle) {
                ScanRecentBarcodesRow(
                    recentBarcodes = recentBarcodes.value,
                    bottomNavClearance = bottomNavClearance,
                    onQuickScan = { viewModel.quickScan(it) },
                )
            }

            // ── Instant mode FAB — bottom-start ──
            ScanInstantModeFab(
                instantMode = instantMode.value,
                bottomNavClearance = bottomNavClearance,
                onClick = { viewModel.toggleInstantMode() },
            )

        }

        // ── Result of the last scan attempt — a single exhaustive `when` over all 7
        // ScanUiState variants (no `else`), so a future 8th variant fails to compile
        // here instead of silently rendering nothing. Previously scattered across an
        // `if (hasCamera...) {...} else if (state.value is Error) {...}` pair (each
        // half re-deriving its own `error` via an `as` cast) plus two independent
        // `(state.value as? X)?.let{}` dialog checks below the Box. Kept inside Box (not moved
        // after it) because the Error banners still need BoxScope's Modifier.align;
        // the two dialogs don't need it but sit here too now for one single dispatch
        // point - AlertDialog renders in its own window regardless of tree position,
        // so this is a pure dispatch-structure change with identical rendered output. ──
        ScanStateOverlay(
            state = state.value,
            hasCamera = hasCamera,
            cameraUnavailable = cameraUnavailable,
            bottomNavClearance = bottomNavClearance,
            language = language.value,
            healthConditions = healthConditions.value,
            onRetryScore = { viewModel.score() },
            onDismissError = { viewModel.dismissError() },
            onDismissFound = { viewModel.dismissFound() },
            onSaveDetectedMedication = { entry -> viewModel.saveDetectedMedication(entry) },
            // Same "consumed" cleanup the single-item Success path runs after
            // navigating (see the LaunchedEffect(state.value) above) - without it,
            // MultiFoodFound would stay current and the picked item's dialog (or a
            // stale photo queue) would still be there on returning to this screen.
            onPickMultiFood = { id -> onResultReady(id); viewModel.resultConsumed() },
        )
    }
}

@Composable
private fun ScanPermissionRequestColumn(
    permanentlyDenied: Boolean,
    manualEntryOpen: Boolean,
    onOpenAppSettings: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenManualEntry: () -> Unit,
    onQuickScan: (String) -> Unit,
) {
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
            ScanEatPrimaryButton(onClick = onOpenAppSettings) {
                Text(stringResource(R.string.scan_open_settings_button))
            }
        } else {
            ScanEatPrimaryButton(onClick = onRequestPermission) {
                Text(stringResource(R.string.common_allow))
            }
        }
        Spacer(Modifier.height(Spacing.L))
        if (!manualEntryOpen) {
            TextButton(onClick = onOpenManualEntry) {
                Text(stringResource(R.string.scan_manual_entry_toggle), color = OnBackground.copy(0.8f))
            }
        } else {
            ManualBarcodeEntry(onSubmit = onQuickScan)
        }
    }
}

@Composable
private fun BoxScope.ScanHeaderBar(
    topInset: Dp,
    todayScanCount: Int,
    isScanning: Boolean,
    barcode: String?,
    hasQueuedPhotosNoBarcode: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth().align(Alignment.TopStart)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(0.55f), Color.Transparent)))
            .padding(horizontal = 20.dp).padding(top = topInset + Spacing.L, bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
            // New: today's scan count badge — previously there was no way to know how
            // many products you'd already scanned today without leaving the scan tab.
            if (todayScanCount > 0) {
                Surface(shape = RoundedCornerShape(50), color = AccentCoral.copy(0.85f)) {
                    Text(
                        "$todayScanCount",
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
                isScanning -> stringResource(R.string.scan_analyzing)
                barcode != null -> stringResource(R.string.scan_barcode_prefix, barcode)
                hasQueuedPhotosNoBarcode -> stringResource(R.string.scan_hint_photos_ready)
                else -> stringResource(R.string.scan_hint)
            },
            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.8f),
        )
    }
}

@Composable
private fun BoxScope.ScanBarcodeChip(barcode: String, topInset: Dp, cachedPreview: ScanResult?) {
    Box(
        modifier = Modifier.align(Alignment.TopCenter).padding(top = topInset + 96.dp)
            .glassSheen(edgeAlpha = 0.22f, shape = RoundedCornerShape(24.dp), glowTint = AccentCoral, glowAlpha = 0.07f),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceVariant.copy(0.9f),
        ) {
            Row(Modifier.padding(horizontal = Spacing.L, vertical = Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QrCodeScanner, null, tint = AccentCoral, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.S))
                Text(barcode, style = MaterialTheme.typography.labelLarge, color = OnSurface, fontWeight = FontWeight.Medium)
                // "Already scanned this" cue — the local-cache lookup
                // scoreBarcode() already does to skip the network on a
                // rescan, surfaced here for the first time so the user
                // sees it's a known product before even tapping the score
                // FAB, instead of only finding out after the round-trip.
                cachedPreview?.takeIf { it.barcode == barcode }?.let { cached ->
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

@Composable
private fun BoxScope.ScanPhotoQueue(images: List<ImagePayload>, topInset: Dp, onRemovePhoto: (Int) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().align(Alignment.TopStart).padding(top = topInset + 88.dp)
            .padding(horizontal = Spacing.L),
    ) {
        Box(Modifier.glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(10.dp))) {
            Surface(shape = RoundedCornerShape(10.dp), color = Background.copy(0.7f)) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(pluralStringResource(R.plurals.scan_photo_count, images.size, images.size), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.8f))
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        itemsIndexed(images) { index, payload ->
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
                                IconButton(onClick = { onRemovePhoto(index) },
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

@Composable
private fun ScanBoundingBoxOverlay(rect: android.graphics.Rect, imgW: Int, imgH: Int) {
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

@Composable
private fun BoxScope.ScanScoreFab(scanState: ScanUiState, bottomNavClearance: Dp, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = bottomNavClearance + 20.dp),
        containerColor = AccentCoral,
        shape = CircleShape,
    ) {
        // Exhaustive over all 7 ScanUiState variants (no `else`) - a future
        // 8th variant now fails to compile here instead of silently falling
        // through to the generic search icon unnoticed.
        when (scanState) {
            is ScanUiState.Scanning -> CircularProgressIndicator(
                color = Color.Black, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            is ScanUiState.Idle, is ScanUiState.Success, is ScanUiState.Error,
            is ScanUiState.MedicationFound, is ScanUiState.NonConsumableFound,
            is ScanUiState.MultiFoodFound ->
                Icon(Icons.Default.Search, stringResource(R.string.scan_cd_scan), tint = Color.Black)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BoxScope.ScanIdentifyFoodAction(bottomNavClearance: Dp, onClick: () -> Unit, onLongClick: () -> Unit) {
    // Long-press is a hidden gesture by nature — this one-line caption (shown only
    // while the pill itself is, i.e. photos queued / no barcode / not scanning,
    // same gate the caller already applies) is what makes identifyMultiFromPhotos()
    // discoverable at all, instead of a feature nobody ever stumbles onto.
    val multiHint = stringResource(R.string.scan_identify_multi_hint)
    Column(
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 84.dp, bottom = bottomNavClearance + 28.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            multiHint,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(0.75f),
        )
        Box(modifier = Modifier.glassSheen(edgeAlpha = 0.20f, shape = RoundedCornerShape(CardRadius.PROMINENT), glowTint = AccentCoral, glowAlpha = 0.06f)) {
            Surface(
                shape = RoundedCornerShape(CardRadius.PROMINENT),
                color = SurfaceVariant.copy(0.9f),
                modifier = Modifier.combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onLongClickLabel = multiHint,
                ),
            ) {
                Row(Modifier.padding(horizontal = Spacing.M, vertical = Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Fastfood, null, tint = AccentCoral, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.scan_identify_food_button), style = MaterialTheme.typography.labelSmall, color = OnSurface)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.ScanRecentBarcodesRow(recentBarcodes: List<String>, bottomNavClearance: Dp, onQuickScan: (String) -> Unit) {
    Column(
        modifier = Modifier.align(Alignment.BottomStart)
            .padding(start = 20.dp, bottom = bottomNavClearance + 84.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        recentBarcodes.takeLast(3).reversed().forEach { bc ->
            Box(Modifier.glassSheen(edgeAlpha = 0.12f, shape = RoundedCornerShape(20.dp), glowAlpha = 0f, reliefAlpha = 0f)) {
                Surface(
                    onClick = { onQuickScan(bc) },
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

@Composable
private fun BoxScope.ScanInstantModeFab(instantMode: Boolean, bottomNavClearance: Dp, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier       = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = bottomNavClearance + 20.dp),
        containerColor = if (instantMode) AccentCoral else SurfaceVariant,
        shape          = CircleShape,
    ) {
        Icon(Icons.Default.Bolt, stringResource(R.string.scan_instant_toggle), tint = if (instantMode) Color.Black else OnSurface)
    }
}

@Composable
private fun BoxScope.ScanStateOverlay(
    state: ScanUiState,
    hasCamera: Boolean,
    cameraUnavailable: Boolean,
    bottomNavClearance: Dp,
    language: String,
    healthConditions: Set<String>,
    onRetryScore: () -> Unit,
    onDismissError: () -> Unit,
    onDismissFound: () -> Unit,
    onSaveDetectedMedication: (fr.scanneat.domain.engine.medication.MedicationDbEntry) -> Unit,
    onPickMultiFood: (Long) -> Unit,
) {
    when (val s = state) {
        is ScanUiState.Idle, is ScanUiState.Scanning, is ScanUiState.Success -> Unit
        is ScanUiState.Error -> {
            if (hasCamera && !cameraUnavailable) {
                if (s.needsPhoto) {
                    Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = Spacing.L, end = Spacing.L, bottom = bottomNavClearance + 96.dp)
                        .glassSheen(edgeAlpha = 0.16f, shape = RoundedCornerShape(CardRadius.CONTROL), glowAlpha = 0.06f),
                        color = SurfaceVariant.copy(alpha = 0.42f), shape = RoundedCornerShape(CardRadius.CONTROL)) {
                        Row(Modifier.padding(Spacing.M), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CameraAlt, null, tint = AccentCoral)
                            Spacer(Modifier.width(Spacing.S))
                            Text(stringResource(R.string.scan_needs_photo),
                                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = OnSurface)
                            IconButton(onClick = onDismissError, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, stringResource(R.string.common_close), tint = OnSurface)
                            }
                        }
                    }
                } else {
                    ErrorBanner(
                        message     = s.message,
                        modifier    = Modifier.align(Alignment.BottomCenter).padding(start = Spacing.L, end = Spacing.L, bottom = bottomNavClearance + 96.dp),
                        actionLabel = stringResource(R.string.common_retry),
                        onAction    = onRetryScore,
                        onDismiss   = onDismissError,
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
                    modifier    = Modifier.align(Alignment.BottomCenter).padding(start = Spacing.L, end = Spacing.L, bottom = bottomNavClearance + 24.dp),
                    actionLabel = stringResource(R.string.common_retry),
                    onAction    = onRetryScore,
                    onDismiss   = onDismissError,
                )
            }
        }
        is ScanUiState.MedicationFound -> {
            val hints = remember(s.entry, language, healthConditions) {
                generateMedicationHints(s.entry, healthConditions, language)
            }
            AlertDialog(
                onDismissRequest = onDismissFound,
                containerColor = SurfaceVariant,
                title = { Text(stringResource(R.string.scan_medication_found_title), color = OnBackground) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Text(stringResource(R.string.scan_medication_found_body, s.entry.name), color = OnBackground.copy(0.7f))
                        FactsCautionsColumn(hints.facts, hints.cautions)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onSaveDetectedMedication(s.entry) }) {
                        Text(stringResource(R.string.scan_medication_found_add), color = Teal)
                    }
                },
                dismissButton = { TextButton(onClick = onDismissFound) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
            )
        }
        is ScanUiState.NonConsumableFound -> {
            val hints = remember(s.entry, language) { generateNonConsumableHints(s.entry.category, language) }
            AlertDialog(
                onDismissRequest = onDismissFound,
                containerColor = SurfaceVariant,
                title = { Text(stringResource(R.string.scan_nonconsumable_found_title), color = OnBackground) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Text(stringResource(R.string.scan_nonconsumable_found_body, s.entry.name, s.entry.brand), color = OnBackground.copy(0.8f))
                        Text(stringResource(R.string.scan_nonconsumable_safety_line), color = semanticRed(), fontWeight = FontWeight.SemiBold)
                        FactsCautionsColumn(hints.facts, hints.cautions)
                    }
                },
                confirmButton = { TextButton(onClick = onDismissFound) { Text(stringResource(R.string.common_close), color = AccentCoral) } },
            )
        }
        is ScanUiState.MultiFoodFound -> {
            MultiFoodFoundDialog(items = s.items, onPick = onPickMultiFood, onDismiss = onDismissFound)
        }
    }
}

/**
 * identifyMultiFromPhotos() success dialog - a plate photo returned several
 * distinct foods, each already scored and persisted (see ScanViewModel's
 * ScanUiState.MultiFoodFound doc comment), so this is purely a picker: tapping
 * a row hands its scan_history id to [onPick], which navigates straight to the
 * existing Result screen exactly like the single-item Success path does.
 * Styled like the sibling MedicationFound/NonConsumableFound AlertDialogs above,
 * with a pickable-row list modeled on SuggestRecipesDialog's LazyColumn-in-an-
 * AlertDialog pattern and a grade badge matching ScanHistoryScreen's ScanHistoryRow.
 */
@Composable
private fun MultiFoodFoundDialog(
    items: List<Pair<ScanResult, Long>>,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceVariant,
        title = { Text(stringResource(R.string.scan_identify_multi_found_title), color = OnBackground) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(stringResource(R.string.scan_identify_multi_found_body), color = OnBackground.copy(0.7f))
                HorizontalDivider(color = OnBackground.copy(0.1f))
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(items) { _, (result, persistedId) ->
                        val grade = gradeColor(result.audit.grade)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = OnBackground.copy(0.05f),
                            onClick = { onPick(persistedId) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                            ) {
                                Surface(shape = RoundedCornerShape(CardRadius.CONTROL), color = grade.copy(0.2f)) {
                                    Text(
                                        result.audit.grade.label,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = grade, fontWeight = FontWeight.Bold,
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        result.product.name, style = MaterialTheme.typography.bodyMedium,
                                        color = OnSurface, fontWeight = FontWeight.Medium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    Text("${result.audit.score}", style = MaterialTheme.typography.bodySmall, color = OnSurface.copy(0.6f))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close), color = AccentCoral) } },
    )
}
