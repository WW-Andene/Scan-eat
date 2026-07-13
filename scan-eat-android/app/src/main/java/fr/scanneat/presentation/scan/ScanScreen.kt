package fr.scanneat.presentation.scan

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import fr.scanneat.R
import fr.scanneat.data.remote.api.ImagePayload
import fr.scanneat.presentation.ui.theme.*
import java.util.concurrent.Executors

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
    val state          = viewModel.state.collectAsStateWithLifecycle()
    val images         = viewModel.images.collectAsStateWithLifecycle()
    val barcode        = viewModel.scannedBarcode.collectAsStateWithLifecycle()

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

    LaunchedEffect(state.value) {
        if (state.value is ScanUiState.Success) {
            onResultReady((state.value as ScanUiState.Success).persistedId)
        }
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
    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        if (hasCamera && !cameraUnavailable) {
            CameraPreview(
                onBarcodeDetected = { viewModel.onBarcodeDetected(it) },
                onPhotoCaptured   = { viewModel.addPhoto(it) },
                onCameraError     = { cameraUnavailable = true },
            )
        } else if (!hasCameraHardware) {
            // Camera-less device (manifest declares both <uses-feature> entries
            // required="false") - a permission prompt here would be pointless theater,
            // so this goes straight to the one input path that still works: manual entry.
            NoCameraFallback(
                titleRes = R.string.scan_no_camera_title,
                bodyRes  = R.string.scan_no_camera_body,
                onSubmit = { viewModel.onBarcodeDetected(it); viewModel.score() },
            )
        } else if (cameraUnavailable) {
            NoCameraFallback(
                titleRes = R.string.scan_camera_unavailable_title,
                bodyRes  = R.string.scan_camera_unavailable_body,
                onSubmit = { viewModel.onBarcodeDetected(it); viewModel.score() },
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
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.scan_camera_permission_title), style = MaterialTheme.typography.titleMedium,
                    color = OnBackground, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.camera_permission_rationale),
                    style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f), textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
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
                Spacer(Modifier.height(16.dp))
                if (!manualEntryOpen) {
                    TextButton(onClick = { manualEntryOpen = true }) {
                        Text(stringResource(R.string.scan_manual_entry_toggle), color = OnBackground.copy(0.8f))
                    }
                } else {
                    ManualBarcodeEntry(onSubmit = { viewModel.onBarcodeDetected(it); viewModel.score() })
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
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    barcode.value?.let { stringResource(R.string.scan_barcode_prefix, it) } ?: stringResource(R.string.scan_hint),
                    style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.8f),
                )
            }

            barcode.value?.let { bc ->
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = SurfaceVariant.copy(0.9f),
                ) {
                    Row(Modifier.padding(horizontal = Spacing.L, vertical = Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QrCodeScanner, null, tint = AccentCoral, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(bc, style = MaterialTheme.typography.labelLarge, color = OnSurface, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // ── Photo queue — floats below the header, distinct corner from the button cluster ──
            if (images.value.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopStart).padding(top = 88.dp)
                        .padding(horizontal = Spacing.L),
                ) {
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
                                                Icon(Icons.Default.Image, null, tint = OnSurface.copy(0.5f), modifier = Modifier.size(20.dp))
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

            // ── Score FAB — bottom-end, distinct corner from CameraPreview's own
            // flash toggle (top-end) and photo-capture button (bottom-center) ──
            FloatingActionButton(
                onClick = { viewModel.score() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp),
                containerColor = AccentCoral,
                shape = CircleShape,
            ) {
                when (state.value) {
                    is ScanUiState.Scanning -> CircularProgressIndicator(
                        color = Color.Black, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    else -> Icon(Icons.Default.Search, stringResource(R.string.scan_cd_scan), tint = Color.Black)
                }
            }

            // ── Error — floats just above the button cluster ──
            if (state.value is ScanUiState.Error) {
                val error = state.value as ScanUiState.Error
                if (error.needsPhoto) {
                    Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = Spacing.L, end = Spacing.L, bottom = 96.dp),
                        color = SurfaceVariant, shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(Spacing.M), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CameraAlt, null, tint = AccentCoral)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.scan_needs_photo),
                                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = OnSurface)
                            IconButton(onClick = { viewModel.dismissError() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, stringResource(R.string.common_close), tint = OnSurface)
                            }
                        }
                    }
                } else {
                    ErrorBanner(
                        message     = error.message,
                        modifier    = Modifier.align(Alignment.BottomCenter).padding(start = Spacing.L, end = Spacing.L, bottom = 96.dp),
                        actionLabel = stringResource(R.string.common_retry),
                        onAction    = { viewModel.score() },
                        onDismiss   = { viewModel.dismissError() },
                    )
                }
            }
        } else if (state.value is ScanUiState.Error) {
            // Same error surface as the camera path above, but reachable from the
            // no-camera/camera-unavailable fallbacks too - those flows call
            // viewModel.score() straight from manual barcode entry, with no FAB or
            // camera preview underneath, so a scoring failure there still needs
            // somewhere to show up instead of silently going nowhere.
            val error = state.value as ScanUiState.Error
            ErrorBanner(
                message     = error.message,
                modifier    = Modifier.align(Alignment.BottomCenter).padding(start = Spacing.L, end = Spacing.L, bottom = 24.dp),
                actionLabel = stringResource(R.string.common_retry),
                onAction    = { viewModel.score() },
                onDismiss   = { viewModel.dismissError() },
            )
        }
    }

    (state.value as? ScanUiState.MedicationFound)?.let { found ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            containerColor = SurfaceVariant,
            title = { Text(stringResource(R.string.scan_medication_found_title), color = OnBackground) },
            text = { Text(stringResource(R.string.scan_medication_found_body, found.entry.name), color = OnBackground.copy(0.7f)) },
            confirmButton = {
                TextButton(onClick = { viewModel.saveDetectedMedication(found.entry) }) {
                    Text(stringResource(R.string.scan_medication_found_add), color = Teal)
                }
            },
            dismissButton = { TextButton(onClick = { viewModel.dismissError() }) { Text(stringResource(R.string.common_cancel), color = OnBackground.copy(0.6f)) } },
        )
    }

    (state.value as? ScanUiState.NonConsumableFound)?.let { found ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            containerColor = SurfaceVariant,
            title = { Text(stringResource(R.string.scan_nonconsumable_found_title), color = OnBackground) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Text(stringResource(R.string.scan_nonconsumable_found_body, found.entry.name, found.entry.brand), color = OnBackground.copy(0.8f))
                    Text(stringResource(R.string.scan_nonconsumable_safety_line), color = FlagRed, fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.dismissError() }) { Text(stringResource(R.string.common_close), color = AccentCoral) } },
        )
    }
}

/**
 * Full-screen fallback shown in place of the camera preview: either the device has
 * no camera hardware at all (`FEATURE_CAMERA_ANY` false, see hasCameraHardware above),
 * or CameraPreview's own bind attempt failed at runtime. Either way the barcode-entry
 * path via ManualBarcodeEntry below is the only way left to reach ScanViewModel.score()
 * with a barcode, so this isn't a dead-end message — it's a working substitute input.
 */
@Composable
private fun NoCameraFallback(
    titleRes: Int,
    bodyRes: Int,
    onSubmit: (String) -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.XXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.QrCodeScanner, null, tint = OnBackground, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium, color = OnBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(bodyRes), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f), textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        if (onRetry != null) {
            ScanEatPrimaryButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
            Spacer(Modifier.height(16.dp))
        }
        ManualBarcodeEntry(onSubmit = onSubmit)
    }
}

/**
 * Same digit-count validation analyzeFrame applies to camera-detected barcodes
 * (8/12/13/14 digits, covering EAN-8/UPC-A/EAN-13/GTIN-14) so a manually typed
 * code reaches ScanRepository in exactly the shape it already expects.
 */
@Composable
private fun ManualBarcodeEntry(onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val digits = remember(text) { text.filter { it.isDigit() } }
    val isValid = digits.length in listOf(8, 12, 13, 14)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.scan_manual_entry_label)) },
            placeholder = { Text(stringResource(R.string.scan_manual_entry_hint)) },
            singleLine = true,
            isError = text.isNotEmpty() && !isValid,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(220.dp),
        )
        Spacer(Modifier.height(12.dp))
        ScanEatPrimaryButton(onClick = { onSubmit(digits) }, enabled = isValid) {
            Text(stringResource(R.string.scan_manual_entry_submit))
        }
    }
}

// ImageProxy.image is CameraX's @ExperimentalGetImage API, which is built on
// the androidx.annotation.experimental system rather than Kotlin's native
// @RequiresOptIn - lint's UnsafeOptInUsageDetector only recognizes
// androidx.annotation.OptIn for it, not kotlin.OptIn (which silently didn't
// suppress the warning despite looking like the right fix). Extracted into
// its own function rather than annotating the whole CameraPreview composable
// so the experimental-API requirement doesn't leak onto its callers.
@androidx.annotation.OptIn(markerClass = [androidx.camera.core.ExperimentalGetImage::class])
private fun analyzeFrame(proxy: ImageProxy, scanner: com.google.mlkit.vision.barcode.BarcodeScanner, onBarcodeDetected: (String) -> Unit) {
    val media = proxy.image
    if (media != null) {
        val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        scanner.process(img)
            .addOnSuccessListener { barcodes ->
                for (bc in barcodes) {
                    val raw = bc.rawValue ?: continue
                    // FORMAT_ITF included for GTIN-14 case/pallet codes (14 digits) -
                    // ScanRepository.gtin14ToEan13() already handles converting these
                    // to a real retail EAN-13, but that logic was unreachable without
                    // the scanner actually passing the format+length through here.
                    if (bc.format in listOf(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                            Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128,
                            Barcode.FORMAT_ITF)) {
                        val digits = raw.filter { it.isDigit() }
                        if (digits.length in listOf(8, 12, 13, 14)) onBarcodeDetected(digits)
                    }
                }
            }
            .addOnCompleteListener { proxy.close() }
    } else proxy.close()
}

@Composable
fun CameraPreview(
    onBarcodeDetected: (String) -> Unit,
    onPhotoCaptured: (Bitmap) -> Unit,
    onCameraError: () -> Unit = {},
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scanner        = remember { BarcodeScanning.getClient() }

    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown(); scanner.close() } }

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var camera: Camera? by remember { mutableStateOf(null) }
    var hasFlash by remember { mutableStateOf(false) }
    var torchOn by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.ui.viewinterop.AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    // COMPATIBLE (TextureView) instead of the default PERFORMANCE (SurfaceView):
                    // a SurfaceView composites in its own hardware layer and always draws either
                    // fully above or fully below the rest of the view hierarchy, ignoring normal
                    // z-order — it can bleed over sibling Compose UI (e.g. clipping the header text).
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    val provider = future.get()
                    val preview  = Preview.Builder().build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                    val capture  = ImageCapture.Builder()
                        .setTargetResolution(Size(1600, 1200))
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    imageCapture = capture
                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { ia ->
                            ia.setAnalyzer(executor) { proxy ->
                                analyzeFrame(proxy, scanner, onBarcodeDetected)
                            }
                        }
                    // Previously a bare runCatching with no onFailure branch: a bind failure
                    // (camera held by another app, transient driver/hardware fault, etc.)
                    // left `camera` null forever with no signal to the caller - the preview
                    // stayed blank and the capture FAB silently did nothing on every tap,
                    // with no error shown anywhere. onCameraError lets ScanScreen switch to
                    // the manual-entry fallback instead of a dead screen.
                    runCatching {
                        provider.unbindAll()
                        camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, analysis)
                        hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
                    }.onFailure { onCameraError() }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )

        if (hasFlash) {
            FloatingActionButton(
                onClick = {
                    torchOn = !torchOn
                    camera?.cameraControl?.enableTorch(torchOn)
                },
                modifier       = Modifier.align(Alignment.TopEnd).padding(Spacing.L).size(40.dp),
                containerColor = if (torchOn) AccentCoral else SurfaceVariant,
            ) {
                Icon(
                    if (torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    stringResource(R.string.scan_flash_toggle),
                    tint = if (torchOn) Color.Black else OnSurface,
                )
            }
        }

        FloatingActionButton(
            onClick = {
                imageCapture?.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bmp = image.toBitmap(); image.close(); onPhotoCaptured(bmp)
                    }
                })
            },
            modifier       = Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.L),
            containerColor = SurfaceVariant,
        ) {
            Icon(Icons.Default.CameraAlt, stringResource(R.string.scan_capture), tint = OnSurface)
        }
    }
}
