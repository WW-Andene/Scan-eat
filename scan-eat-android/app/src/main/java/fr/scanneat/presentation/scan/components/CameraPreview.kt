package fr.scanneat.presentation.scan.components

import android.graphics.Bitmap
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.Observer
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
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import fr.scanneat.R
import fr.scanneat.presentation.ui.theme.AccentCoral
import fr.scanneat.presentation.ui.theme.OnSurface
import fr.scanneat.presentation.ui.theme.Spacing
import fr.scanneat.presentation.ui.theme.SurfaceVariant
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    onBarcodeDetected: (String) -> Unit,
    onPhotoCaptured: (Bitmap) -> Unit,
    onCameraError: () -> Unit = {},
    onCaptureError: () -> Unit = {},
    onBarcodeBounds: ((android.graphics.Rect, Int, Int) -> Unit)? = null,
    onBoundsCleared: (() -> Unit)? = null,
    // Shelf-scan live overlay (hybrid mode: free on-device boxes, tap fires the
    // actual identify call) — both null by default so every other CameraPreview
    // call site (Scan's main barcode/photo flow) pays zero extra inference cost.
    onObjectsDetected: ((List<DetectedBox>, Int, Int) -> Unit)? = null,
    // Lets the caller trigger its own on-demand capture (e.g. a box tap) via the
    // same ImageCapture use case the shutter FAB below already uses, without
    // duplicating the bind-to-lifecycle setup a second time.
    onImageCaptureReady: ((ImageCapture) -> Unit)? = null,
    // The floating bottom nav is a separate, later z-layer drawn on top of the
    // whole screen - every other overlay on ScanScreen was given this same
    // clearance for exactly that reason. Without it, the capture FAB's ~16-72dp
    // band sits inside that danger zone and can render partially/fully hidden
    // behind the nav bar.
    bottomNavClearance: androidx.compose.ui.unit.Dp = 0.dp,
    topInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val context        = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scanner        = remember { BarcodeScanning.getClient() }
    // Only spun up when a caller actually wants boxes (ScanScreen's shelf mode) —
    // every other screen reusing CameraPreview never allocates this detector.
    // Bundled model (see mlkit-object-detection's own comment), so this works
    // fully offline with no first-use download delay.
    val objectDetector = remember(onObjectsDetected != null) {
        if (onObjectsDetected != null) {
            ObjectDetection.getClient(
                ObjectDetectorOptions.Builder()
                    .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                    .enableMultipleObjects()
                    .build(),
            )
        } else null
    }

    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { executor.shutdown(); scanner.close(); objectDetector?.close() } }

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
                    onImageCaptureReady?.invoke(capture)
                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { ia ->
                            ia.setAnalyzer(executor) { proxy ->
                                analyzeFrame(proxy, scanner, onBarcodeDetected, onBarcodeBounds, onBoundsCleared, objectDetector, onObjectsDetected)
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
                        // bindToLifecycle only throws for *synchronous* bind failures (no
                        // camera, bad config). A camera that opens fine and then dies at
                        // runtime - reclaimed by another app/the system camera service,
                        // a thermal shutdown, a driver fault - is reported asynchronously
                        // via cameraState, not as an exception here, so it was previously
                        // invisible to this runCatching entirely: the preview just froze on
                        // its last frame forever with no error, no fallback, and a shutter
                        // button that looked present but did nothing - indistinguishable
                        // from "the camera closed" to anyone looking at it. A CLOSED state
                        // that carries a StateError (as opposed to the error-free CLOSED a
                        // normal lifecycle-driven unbind produces) is CameraX's own signal
                        // that this was an unrequested failure, not an intentional stop.
                        camera?.cameraInfo?.cameraState?.observe(
                            lifecycleOwner,
                            Observer { state ->
                                if (state.type == CameraState.Type.CLOSED && state.error != null) onCameraError()
                            },
                        )
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
                modifier       = Modifier.align(Alignment.TopEnd).padding(top = topInset + Spacing.L, end = Spacing.L).size(40.dp),
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
                // ContextCompat.getMainExecutor, not the analyzer's background `executor` -
                // takePicture's executor param only controls which thread the callback runs
                // on, not where the actual capture work happens, and onCaptureError() below
                // calls Toast.makeText(...).show(), which requires a prepared Looper. The
                // background analyzer executor has none, so every real capture failure (the
                // exact case this callback exists to surface) crashed with "Can't create
                // handler inside thread that has not called Looper.prepare()" instead of
                // showing the intended error toast. Running on Main also serializes every
                // onPhotoCaptured()/onCaptureError() call with the click-handler thread that
                // reads/writes ScanViewModel's photo queue, closing a cross-thread race on it.
                imageCapture?.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
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
            modifier       = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomNavClearance + Spacing.L),
            containerColor = SurfaceVariant,
        ) {
            Icon(Icons.Default.CameraAlt, stringResource(R.string.scan_capture), tint = OnSurface)
        }
    }
}
