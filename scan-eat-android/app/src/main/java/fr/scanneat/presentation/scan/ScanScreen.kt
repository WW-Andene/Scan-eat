package fr.scanneat.presentation.scan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.scanneat.R
import fr.scanneat.presentation.scan.components.CameraPreview
import fr.scanneat.presentation.scan.components.DetectedBarcode
import fr.scanneat.presentation.scan.components.DetectedBox
import fr.scanneat.presentation.scan.components.NoCameraFallback
import fr.scanneat.presentation.scan.components.ScanBarcodeArPanel
import fr.scanneat.presentation.scan.components.ScanBarcodeChip
import fr.scanneat.presentation.scan.components.ScanBoundingBoxesOverlay
import fr.scanneat.presentation.scan.components.ScanHeaderBar
import fr.scanneat.presentation.scan.components.ScanIdentifyFoodAction
import fr.scanneat.presentation.scan.components.ScanInstantModeFab
import fr.scanneat.presentation.scan.components.ScanPermissionRequestColumn
import fr.scanneat.presentation.scan.components.ScanPhotoQueue
import fr.scanneat.presentation.scan.components.ScanRecentBarcodesRow
import fr.scanneat.presentation.scan.components.ScanScoreFab
import fr.scanneat.presentation.scan.components.ScanShelfModeFab
import fr.scanneat.presentation.scan.components.ScanShelfObjectOverlay
import fr.scanneat.presentation.scan.components.ScanShelfPeekChip
import fr.scanneat.presentation.scan.components.ScanStateOverlay
import fr.scanneat.presentation.scan.components.ShelfPeek
import fr.scanneat.presentation.scan.components.ShelfPeekStatus
import fr.scanneat.presentation.ui.theme.*
import kotlinx.coroutines.launch

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
    val cachedPreviewWarning = viewModel.cachedPreviewWarning.collectAsStateWithLifecycle()
    val visibleBarcodeCachedPreviews = viewModel.visibleBarcodeCachedPreviews.collectAsStateWithLifecycle()
    val captureErrorMessage = stringResource(R.string.scan_capture_error)

    // ── Shelf-scan mode (hybrid live-boxes/tap-to-identify) ──────────────────
    // Off by default: CameraPreview only allocates the on-device object
    // detector when onObjectsDetected is non-null, so every other tab of this
    // screen (barcode/photo scanning) pays zero extra inference cost.
    var shelfMode by remember { mutableStateOf(false) }
    var shelfObjects by remember { mutableStateOf<Triple<List<DetectedBox>, Int, Int>?>(null) }
    var shelfImageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var shelfPeeks by remember { mutableStateOf<List<ShelfPeek>>(emptyList()) }
    var nextPeekId by remember { mutableStateOf(0L) }
    val shelfCoroutineScope = rememberCoroutineScope()
    LaunchedEffect(shelfMode) {
        if (!shelfMode) { shelfObjects = null; shelfPeeks = emptyList() }
    }

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
        // barcodesInFrame: every barcode decoded this frame plus the rotated image
        // dimensions used to map each one's rect into screen space - see
        // CameraFrameAnalyzer's own doc comment on why this is now a list rather
        // than a single (rect, w, h) triple.
        var barcodesInFrame by remember { mutableStateOf<Triple<List<DetectedBarcode>, Int, Int>?>(null) }

        if (hasCamera && !cameraUnavailable) {
            CameraPreview(
                onBarcodeDetected = { viewModel.onBarcodeDetected(it) },
                onPhotoCaptured   = { viewModel.addPhoto(it) },
                onCameraError     = { cameraUnavailable = true },
                // A capture failure is transient (unlike a bind failure) - don't drop into
                // the full manual-entry fallback, just surface it so the shutter button
                // doesn't look silently broken.
                onCaptureError    = { Toast.makeText(context, captureErrorMessage, Toast.LENGTH_SHORT).show() },
                onBarcodesInFrame = { boxes, w, h ->
                    barcodesInFrame = Triple(boxes, w, h)
                    viewModel.onBarcodesVisible(boxes.map { it.value })
                    if (boxes.isEmpty()) viewModel.onBarcodeLost()
                },
                onObjectsDetected = if (shelfMode) { objs, w, h -> shelfObjects = Triple(objs, w, h) } else null,
                onImageCaptureReady = { shelfImageCapture = it },
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

            barcode.value?.let { bc ->
                ScanBarcodeChip(barcode = bc, topInset = topInset, cachedPreview = cachedPreview.value, warning = cachedPreviewWarning.value)
            }

            // ── Photo queue — floats below the header, distinct corner from the button cluster ──
            if (images.value.isNotEmpty()) {
                ScanPhotoQueue(images = images.value, topInset = topInset, onRemovePhoto = { viewModel.removePhoto(it) })
            }

            // ── Bounding box overlay — every decoded barcode this frame, drawn in
            // image→screen mapped coordinates (not just one) ──
            barcodesInFrame?.let { (boxes, imgW, imgH) -> ScanBoundingBoxesOverlay(boxes, imgW, imgH) }

            // ── AR-style auto mini panel — for each barcode already in scan history
            // (visibleBarcodeCachedPreviews), anchored directly above its own
            // detected box, no tap required. A barcode with no prior scan still only
            // gets the plain bounding box above - there's nothing cached to show for
            // it before it's actually been scanned once. ──
            barcodesInFrame?.let { (boxes, imgW, imgH) ->
                boxes.forEach { box ->
                    visibleBarcodeCachedPreviews.value[box.value]?.let { cached ->
                        ScanBarcodeArPanel(box = box, imgW = imgW, imgH = imgH, cached = cached, topInset = topInset)
                    }
                }
            }

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

            // ── Shelf-scan mode toggle — top-end, below the flash toggle ──
            ScanShelfModeFab(
                shelfMode = shelfMode,
                topInset = topInset,
                onClick = { shelfMode = !shelfMode },
            )

            // ── Shelf-scan live boxes + tap-to-identify mini panels ──
            if (shelfMode) {
                shelfObjects?.let { (objs, w, h) ->
                    ScanShelfObjectOverlay(
                        objects = objs,
                        imgW = w,
                        imgH = h,
                        onBoxTapped = { box, tapOffset ->
                            // Cap concurrent peeks — evict the oldest rather than ignore the
                            // tap, same "capped not unbounded" pattern as MAX_QUEUED_PHOTOS.
                            if (shelfPeeks.size >= 3) shelfPeeks = shelfPeeks.drop(1)
                            val peekId = nextPeekId
                            nextPeekId += 1
                            shelfPeeks = shelfPeeks + ShelfPeek(peekId, tapOffset, ShelfPeekStatus.Loading)
                            val capture = shelfImageCapture
                            if (capture == null) {
                                shelfPeeks = shelfPeeks.map { if (it.id == peekId) it.copy(status = ShelfPeekStatus.Failed(captureErrorMessage)) else it }
                            } else {
                                capture.takePicture(
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            val full = image.toBitmap()
                                            image.close()
                                            val cropped = cropAroundBox(full, box.rect, w, h)
                                            // Bitmap.createBitmap(source, x, y, w, h) returns `source` itself,
                                            // not a copy, when the requested region is the whole bitmap (x=0,
                                            // y=0, w/h matching) - which cropAroundBox's own clamping can produce
                                            // for a box spanning nearly the entire frame. Recycling unconditionally
                                            // would then recycle `cropped` out from under identifyShelfBox() too.
                                            // Only recycle `full` when cropAroundBox actually produced a distinct
                                            // bitmap - otherwise this leaked ~7.7MB (1600x1200 ARGB_8888) per
                                            // shelf-mode tap, unlike the main capture path's
                                            // ScanImagePayload.toPayload(), which already recycles every
                                            // intermediate bitmap it creates.
                                            if (cropped !== full) full.recycle()
                                            shelfCoroutineScope.launch {
                                                val result = viewModel.identifyShelfBox(cropped)
                                                shelfPeeks = shelfPeeks.map { p ->
                                                    if (p.id != peekId) p else p.copy(
                                                        status = result.fold(
                                                            onSuccess = { (scanResult, id) -> ShelfPeekStatus.Ready(scanResult.product.name, scanResult.audit.grade, id) },
                                                            onFailure = { e -> ShelfPeekStatus.Failed(e.message ?: captureErrorMessage) },
                                                        ),
                                                    )
                                                }
                                            }
                                        }
                                        override fun onError(exception: ImageCaptureException) {
                                            shelfPeeks = shelfPeeks.map { if (it.id == peekId) it.copy(status = ShelfPeekStatus.Failed(captureErrorMessage)) else it }
                                        }
                                    },
                                )
                            }
                        },
                    )
                }
                shelfPeeks.forEach { peek ->
                    ScanShelfPeekChip(
                        peek = peek,
                        onDismiss = { shelfPeeks = shelfPeeks.filter { it.id != peek.id } },
                        onOpenResult = { id -> onResultReady(id) },
                    )
                }
            }
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

/**
 * Maps a box detected against the (lower-res, 16:9) analysis stream onto the
 * (higher-res, 4:3) capture stream by fraction-of-frame, not pixel-for-pixel —
 * CameraX's ImageAnalysis and ImageCapture use cases independently crop/scale
 * from the same sensor to their own target resolutions, so this is an
 * approximation rather than an exact geometric correspondence. A generous
 * 25%-per-side margin (clamped to the bitmap's own bounds) absorbs that
 * imprecision so the tapped product still ends up inside the crop even when
 * the mapping isn't perfectly centered.
 */
private fun cropAroundBox(bitmap: Bitmap, box: android.graphics.Rect, analysisW: Int, analysisH: Int): Bitmap {
    val fracLeft   = box.left.toFloat()   / analysisW
    val fracTop    = box.top.toFloat()    / analysisH
    val fracRight  = box.right.toFloat()  / analysisW
    val fracBottom = box.bottom.toFloat() / analysisH
    val marginX = (fracRight - fracLeft) * 0.25f
    val marginY = (fracBottom - fracTop) * 0.25f
    val left   = ((fracLeft   - marginX) * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
    val top    = ((fracTop    - marginY) * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
    val right  = ((fracRight  + marginX) * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
    val bottom = ((fracBottom + marginY) * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
    return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
}
