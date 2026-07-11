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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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

    var hasCamera by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
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
        if (hasCamera) {
            CameraPreview(
                onBarcodeDetected = { viewModel.onBarcodeDetected(it) },
                onPhotoCaptured   = { viewModel.addPhoto(it) },
            )
        } else {
            // Camera permission request UI — no camera feed behind it, so this fills the screen itself.
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
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
                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                    ) {
                        Text(stringResource(R.string.scan_open_settings_button), color = Color.Black)
                    }
                } else {
                    Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) {
                        Text(stringResource(R.string.common_allow), color = Color.Black)
                    }
                }
            }
        }

        if (hasCamera) {
            // ── Header — top-start, scrimmed so it stays legible over any camera scene ──
            Column(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopStart)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(0.55f), Color.Transparent)))
                    .padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 28.dp),
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
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QrCodeScanner, null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(bc, style = MaterialTheme.typography.labelLarge, color = OnSurface, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // ── Photo queue — floats below the header, distinct corner from the button cluster ──
            if (images.value.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopStart).padding(top = 88.dp)
                        .padding(horizontal = 16.dp),
                ) {
                    Surface(shape = RoundedCornerShape(10.dp), color = Background.copy(0.7f)) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(stringResource(R.string.scan_photo_count, images.value.size), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.8f))
                            Spacer(Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                containerColor = AccentGreen,
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
                    Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 96.dp),
                        color = SurfaceVariant, shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CameraAlt, null, tint = AccentGreen)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.scan_needs_photo),
                                Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = OnSurface)
                            IconButton(onClick = { viewModel.dismissError() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, stringResource(R.string.common_close), tint = OnSurface)
                            }
                        }
                    }
                } else {
                    Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 96.dp),
                        color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(error.message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            IconButton(onClick = { viewModel.dismissError() }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, stringResource(R.string.common_close), tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    onBarcodeDetected: (String) -> Unit,
    onPhotoCaptured: (Bitmap) -> Unit,
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
                                val media = proxy.image
                                if (media != null) {
                                    val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                                    scanner.process(img)
                                        .addOnSuccessListener { barcodes ->
                                            for (bc in barcodes) {
                                                val raw = bc.rawValue ?: continue
                                                if (bc.format in listOf(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                                                        Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128)) {
                                                    val digits = raw.filter { it.isDigit() }
                                                    if (digits.length in listOf(8, 12, 13)) onBarcodeDetected(digits)
                                                }
                                            }
                                        }
                                        .addOnCompleteListener { proxy.close() }
                                } else proxy.close()
                            }
                        }
                    runCatching {
                        provider.unbindAll()
                        camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, analysis)
                        hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
                    }
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
                modifier       = Modifier.align(Alignment.TopEnd).padding(16.dp).size(40.dp),
                containerColor = if (torchOn) AccentGreen else SurfaceVariant,
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
            modifier       = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            containerColor = SurfaceVariant,
        ) {
            Icon(Icons.Default.CameraAlt, stringResource(R.string.scan_capture), tint = OnSurface)
        }
    }
}
