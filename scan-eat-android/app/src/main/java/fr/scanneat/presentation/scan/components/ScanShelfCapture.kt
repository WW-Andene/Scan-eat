package fr.scanneat.presentation.scan.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.MutableState
import androidx.compose.ui.geometry.Offset
import androidx.core.content.ContextCompat
import fr.scanneat.presentation.scan.ScanViewModel
import fr.scanneat.presentation.scan.toPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tap-to-identify handler for one shelf-scan live-box (ScanShelfObjectOverlay's
 * onBoxTapped) - extracted out of ScanScreen.kt to keep it from growing
 * unbounded. Takes the screen's own `shelfPeeks`/`nextPeekId` remembered state
 * as [MutableState] (rather than the `by remember` delegate syntax the
 * screen itself uses for readability) so this can mutate them from a plain,
 * non-@Composable function - behavior is unchanged from when this logic was
 * inline in ScanScreen's onBoxTapped lambda.
 */
internal fun handleShelfBoxTapped(
    box: DetectedBox,
    tapOffset: Offset,
    imgW: Int,
    imgH: Int,
    shelfImageCapture: ImageCapture?,
    context: Context,
    scope: CoroutineScope,
    captureErrorMessage: String,
    viewModel: ScanViewModel,
    shelfPeeksState: MutableState<List<ShelfPeek>>,
    nextPeekIdState: MutableState<Long>,
) {
    // Cap concurrent peeks — evict the oldest rather than ignore the
    // tap, same "capped not unbounded" pattern as MAX_QUEUED_PHOTOS.
    if (shelfPeeksState.value.size >= 3) shelfPeeksState.value = shelfPeeksState.value.drop(1)
    val peekId = nextPeekIdState.value
    nextPeekIdState.value += 1
    shelfPeeksState.value = shelfPeeksState.value + ShelfPeek(peekId, tapOffset, ShelfPeekStatus.Loading)
    val capture = shelfImageCapture
    if (capture == null) {
        shelfPeeksState.value = shelfPeeksState.value.map { if (it.id == peekId) it.copy(status = ShelfPeekStatus.Failed(captureErrorMessage)) else it }
        return
    }
    capture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                // image.toBitmap() (CameraX's own extension) only supports
                // YUV_420_888/RGBA_8888 - it throws UnsupportedOperationException
                // on the JPEG-format ImageProxy this in-memory ImageCapture
                // callback actually delivers, crashing on every shelf-mode box
                // tap. Same bug, same fix as CameraPreview.kt's main capture
                // path - this is a separate takePicture() call site that fix
                // didn't cover. No rotation correction here (unlike
                // CameraPreview.kt's fix): box.rect/w/h come from the same
                // ImageAnalysis stream in its raw, unrotated sensor orientation,
                // and cropAroundBox maps box coordinates onto `full` purely by
                // fraction-of-width/height - rotating `full` but not the box
                // would misalign the crop against streams that share that
                // convention already.
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()
                // Decoding the full 1600x1200 JPEG just to crop a small region was
                // previously done synchronously on this Main-executor callback,
                // janking the UI thread on every shelf-mode tap. Moved onto
                // Dispatchers.Default inside the same coroutine that already does
                // the (network) identify call, rather than adding a second
                // executor hop - shelfPeeks is only ever written from this single
                // scope.launch block per tap, so no new race.
                scope.launch {
                    // Builds the finished ImagePayload (decode + crop + toPayload's own
                    // scale/compress) entirely on Dispatchers.Default, so
                    // identifyShelfBox() itself can stay a plain suspend function with
                    // no CPU work of its own - same shape as CameraPreview's fix above.
                    val payload = withContext(Dispatchers.Default) {
                        val full = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
                        val c = cropAroundBox(full, box.rect, imgW, imgH)
                        // Bitmap.createBitmap(source, x, y, w, h) returns `source` itself,
                        // not a copy, when the requested region is the whole bitmap (x=0,
                        // y=0, w/h matching) - which cropAroundBox's own clamping can produce
                        // for a box spanning nearly the entire frame. Recycling unconditionally
                        // would then recycle `c` out from under toPayload() below too.
                        // Only recycle `full` when cropAroundBox actually produced a distinct
                        // bitmap - otherwise this leaked ~7.7MB (1600x1200 ARGB_8888) per
                        // shelf-mode tap.
                        if (c !== full) full.recycle()
                        c.toPayload()
                    }
                    if (payload == null) {
                        shelfPeeksState.value = shelfPeeksState.value.map { if (it.id == peekId) it.copy(status = ShelfPeekStatus.Failed(captureErrorMessage)) else it }
                        return@launch
                    }
                    val result = viewModel.identifyShelfBox(payload)
                    shelfPeeksState.value = shelfPeeksState.value.map { p ->
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
                shelfPeeksState.value = shelfPeeksState.value.map { if (it.id == peekId) it.copy(status = ShelfPeekStatus.Failed(captureErrorMessage)) else it }
            }
        },
    )
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
internal fun cropAroundBox(bitmap: Bitmap, box: android.graphics.Rect, analysisW: Int, analysisH: Int): Bitmap {
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
