package fr.scanneat.presentation.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
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
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }

    LaunchedEffect(state.value) {
        if (state.value is ScanUiState.Success) {
            onResultReady((state.value as ScanUiState.Success).persistedId)
        }
    }

    // No Scaffold here — MainShell provides the outer Scaffold + NavigationBar
    Column(modifier = Modifier.fillMaxSize().background(Background)) {
        // ── Header ────────────────────────────────────────────────────────────
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("Scan'eat", style = MaterialTheme.typography.headlineMedium, color = OnBackground, fontWeight = FontWeight.Bold)
            Text(
                barcode.value?.let { "Code : $it" } ?: "Photographiez une étiquette → score 0–100",
                style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f),
            )
        }

        // ── Camera / permission ────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            if (hasCamera) {
                CameraPreview(
                    onBarcodeDetected = { viewModel.onBarcodeDetected(it) },
                    onPhotoCaptured   = { viewModel.addPhoto(it) },
                )
                barcode.value?.let { bc ->
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
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
            } else {
                // Camera permission request UI
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Default.CameraAlt, null, tint = OnBackground, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Accès à la caméra requis", style = MaterialTheme.typography.titleMedium,
                        color = OnBackground, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text("Scan'eat utilise la caméra pour lire les codes-barres et photographier les étiquettes.",
                        style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(0.6f), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) {
                        Text("Autoriser", color = Color.Black)
                    }
                }
            }
        }

        // ── Scan FAB ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            FloatingActionButton(
                onClick = { viewModel.score() },
                containerColor = AccentGreen,
                shape = CircleShape,
            ) {
                when (state.value) {
                    is ScanUiState.Scanning -> CircularProgressIndicator(
                        color = Color.Black, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    else -> Icon(Icons.Default.Search, "Scanner", tint = Color.Black)
                }
            }
        }

        // ── Photo queue ────────────────────────────────────────────────────────
        if (images.value.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text("${images.value.size} photo(s)", style = MaterialTheme.typography.labelSmall, color = OnBackground.copy(0.6f))
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(images.value) { index, payload ->
                        Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp))
                            .border(1.dp, OnSurface.copy(0.2f), RoundedCornerShape(8.dp))) {
                            val bmp = remember(payload) { payload.thumbnail }
                            if (bmp != null) {
                                Image(bitmap = bmp.asImageBitmap(), contentDescription = "Photo ${index + 1}",
                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            } else {
                                Box(Modifier.fillMaxSize().background(SurfaceVariant), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Image, null, tint = OnSurface.copy(0.5f), modifier = Modifier.size(24.dp))
                                }
                            }
                            IconButton(onClick = { viewModel.removePhoto(index) },
                                modifier = Modifier.align(Alignment.TopEnd).size(22.dp).background(Background.copy(0.6f), CircleShape)) {
                                Icon(Icons.Default.Close, "Retirer", tint = OnSurface, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        // ── Error ──────────────────────────────────────────────────────────────
        if (state.value is ScanUiState.Error) {
            val msg = (state.value as ScanUiState.Error).message
            Surface(modifier = Modifier.fillMaxWidth().padding(16.dp),
                color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(msg, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    IconButton(onClick = { viewModel.dismissError() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "Fermer", tint = MaterialTheme.colorScheme.onErrorContainer)
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
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.ui.viewinterop.AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
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
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, analysis)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
        )

        FloatingActionButton(
            onClick = {
                imageCapture?.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bmp = image.toBitmap(); image.close(); onPhotoCaptured(bmp)
                    }
                })
            },
            modifier       = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = SurfaceVariant,
        ) {
            Icon(Icons.Default.CameraAlt, "Capturer", tint = OnSurface)
        }
    }
}
