package fr.scanneat.presentation.scan.components

import android.graphics.Bitmap
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import java.util.concurrent.Executors

/**
 * Many French medication boxes carry no EAN-13 at all — only a GS1 DataMatrix
 * (sometimes a QR) encoding a GS1 "element string": AI 01 (14-digit GTIN),
 * often followed by AI 17 (expiry), AI 10 (batch/lot), etc., e.g.
 * "0103400999999941726073110ABC123". This pulls just the GTIN out of that
 * string so it can be looked up exactly like a scanned barcode. FNC1 (ASCII
 * 29, GS) separators before a variable-length field are stripped if present;
 * fixed-length AI 01 doesn't need one to terminate correctly.
 */
private fun extractGtinFromGs1(raw: String): String? {
    val cleaned = raw.filterNot { it.code == 29 } // strip GS1 FNC1/group-separator control chars (ASCII 29)
    val match = Regex("01(\\d{14})").find(cleaned) ?: return null
    return match.groupValues[1]
}

// ImageProxy.image is CameraX's @ExperimentalGetImage API, which is built on
// the androidx.annotation.experimental system rather than Kotlin's native
// @RequiresOptIn - lint's UnsafeOptInUsageDetector only recognizes
// androidx.annotation.OptIn for it, not kotlin.OptIn (which silently didn't
// suppress the warning despite looking like the right fix). Extracted into
// its own function rather than annotating the whole CameraPreview composable
// so the experimental-API requirement doesn't leak onto its callers.
@androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
private fun analyzeFrame(
    proxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onBarcodeDetected: (String) -> Unit,
    onBarcodeBounds: ((android.graphics.Rect, Int, Int) -> Unit)? = null,
    onBoundsCleared: (() -> Unit)? = null,
) {
    val media = proxy.image
    if (media != null) {
        val rotation = proxy.imageInfo.rotationDegrees
        val (imgW, imgH) = if (rotation == 90 || rotation == 270)
            Pair(proxy.height, proxy.width) else Pair(proxy.width, proxy.height)
        val img = InputImage.fromMediaImage(media, rotation)
        scanner.process(img)
            .addOnSuccessListener { barcodes ->
                var foundAny = false
                for (bc in barcodes) {
                    val raw = bc.rawValue ?: continue
                    // CODABAR: older/still-common on some pharmacy and blood-bank packaging
                    // (e.g. some pre-DataMatrix French medication boxes) - like CODE_128/ITF
                    // it's a symbology, not a fixed-length GTIN encoding, so the digit-length
                    // filter below (unchanged) is what actually decides whether a decoded
                    // value looks like a real product/medication barcode.
                    if (bc.format in listOf(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                            Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128,
                            Barcode.FORMAT_ITF, Barcode.FORMAT_CODABAR)) {
                        val digits = raw.filter { it.isDigit() }
                        if (digits.length in listOf(8, 12, 13, 14)) {
                            onBarcodeDetected(digits)
                            bc.boundingBox?.let { onBarcodeBounds?.invoke(it, imgW, imgH) }
                            foundAny = true
                            break
                        }
                    } else if (bc.format == Barcode.FORMAT_DATA_MATRIX || bc.format == Barcode.FORMAT_QR_CODE) {
                        // Many French medication boxes carry no EAN-13 at all - only a
                        // GS1 DataMatrix (2D "CIP DataMatrix"), and some carry a plain
                        // QR code that just encodes the barcode digits directly. Try the
                        // GS1 GTIN extraction first; if that finds nothing, fall back to
                        // treating the raw value as a plain barcode only when it's
                        // exactly digits of a plausible length (never for an arbitrary
                        // QR code like a URL).
                        val digits = extractGtinFromGs1(raw)
                            ?: raw.takeIf { it.all(Char::isDigit) && it.length in listOf(8, 12, 13, 14) }
                        if (digits != null) {
                            onBarcodeDetected(digits)
                            bc.boundingBox?.let { onBarcodeBounds?.invoke(it, imgW, imgH) }
                            foundAny = true
                            break
                        }
                    }
                }
                if (!foundAny) onBoundsCleared?.invoke()
            }
            .addOnCompleteListener { proxy.close() }
    } else proxy.close()
}

@Composable
fun CameraPreview(
    onBarcodeDetected: (String) -> Unit,
    onPhotoCaptured: (Bitmap) -> Unit,
    onCameraError: () -> Unit = {},
    onCaptureError: () -> Unit = {},
    onBarcodeBounds: ((android.graphics.Rect, Int, Int) -> Unit)? = null,
    onBoundsCleared: (() -> Unit)? = null,
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
        AndroidView(
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
                                analyzeFrame(proxy, scanner, onBarcodeDetected, onBarcodeBounds, onBoundsCleared)
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
                        // A fresh Camera instance starts with its torch off regardless of what
                        // torchOn last held - previously never re-applied here, so a rebind
                        // (e.g. retrying after onCameraError) silently turned the torch off
                        // while the FAB kept showing its "on" (coral) state until the next tap.
                        if (torchOn && hasFlash) camera?.cameraControl?.enableTorch(true)
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
                // imageCapture is only assigned once the async ProcessCameraProvider
                // future resolves and binds - a tap landing in that brief startup
                // window previously no-op'd via the safe call with zero feedback, the
                // same silently-does-nothing class of bug onCaptureError below exists
                // to prevent for every *other* capture failure.
                imageCapture?.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bmp = image.toBitmap(); image.close(); onPhotoCaptured(bmp)
                    }
                    // Previously unoverridden (falls back to a no-op default) - a capture
                    // failure (camera momentarily reclaimed by another process, buffer/driver
                    // fault, storage pressure) made the shutter FAB visibly do nothing with
                    // zero feedback, the same class of bug onCameraError above already fixed
                    // for bindToLifecycle failures.
                    override fun onError(exception: ImageCaptureException) {
                        onCaptureError()
                    }
                }) ?: onCaptureError()
            },
            modifier       = Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.L),
            containerColor = SurfaceVariant,
        ) {
            Icon(Icons.Default.CameraAlt, stringResource(R.string.scan_capture), tint = OnSurface)
        }
    }
}
