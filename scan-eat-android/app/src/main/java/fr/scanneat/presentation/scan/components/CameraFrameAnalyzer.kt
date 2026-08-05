package fr.scanneat.presentation.scan.components

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.text.TextRecognizer

// ============================================================================
// Per-frame CameraX ImageAnalysis pipeline for CameraPreview's ImageAnalysis
// use case — barcode/GS1-DataMatrix decoding plus the optional shelf-scan
// object detector. Split out of CameraPreview.kt since none of this touches
// Compose state; it's a pure frame-in, callback-out analyzer.
// ============================================================================

/**
 * Many French medication boxes carry no EAN-13 at all — only a GS1 DataMatrix
 * (sometimes a QR) encoding a GS1 "element string": AI 01 (14-digit GTIN),
 * often followed by AI 17 (expiry), AI 10 (batch/lot), etc., e.g.
 * "0103400999999941726073110ABC123". This pulls just the GTIN out of that
 * string so it can be looked up exactly like a scanned barcode. FNC1 (ASCII
 * 29, GS) separators before a variable-length field are stripped if present;
 * fixed-length AI 01 doesn't need one to terminate correctly.
 */
internal fun extractGtinFromGs1(raw: String): String? {
    val cleaned = raw.filterNot { it.code == 29 } // strip GS1 FNC1/group-separator control chars (ASCII 29)
    val match = Regex("01(\\d{14})").find(cleaned) ?: return null
    return match.groupValues[1]
}

/**
 * One generically-detected object region from a live camera frame (shelf-scan
 * overlay) — [trackingId] comes from ML Kit's STREAM_MODE tracker (stable
 * across frames for the same physical object as the camera pans, -1 if the
 * tracker couldn't assign one for that detection). No product identity here
 * by design: the detector only tells you *something distinct is here*, not
 * what it is — that's the tap-triggered vision-LLM call's job.
 */
data class DetectedBox(val trackingId: Int, val rect: android.graphics.Rect)

// ImageProxy.image is CameraX's @ExperimentalGetImage API, which is built on
// the androidx.annotation.experimental system rather than Kotlin's native
// @RequiresOptIn - lint's UnsafeOptInUsageDetector only recognizes
// androidx.annotation.OptIn for it, not kotlin.OptIn (which silently didn't
// suppress the warning despite looking like the right fix). Extracted into
// its own function rather than annotating the whole CameraPreview composable
// so the experimental-API requirement doesn't leak onto its callers.
/** One decoded barcode from a live camera frame, alongside its screen-space bounding box. */
data class DetectedBarcode(val value: String, val rect: android.graphics.Rect)

@androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
internal fun analyzeFrame(
    proxy: ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onBarcodeDetected: (String) -> Unit,
    onBarcodesInFrame: ((List<DetectedBarcode>, Int, Int) -> Unit)? = null,
    objectDetector: ObjectDetector? = null,
    onObjectsDetected: ((List<DetectedBox>, Int, Int) -> Unit)? = null,
    // User-requested: detect a price displayed right next to the barcode at
    // the same moment it's scanned, not only via PriceEntryCard's own manual
    // "scan this price tag" button. Only run (see below) on a frame that
    // actually decoded a barcode - unconditionally OCR'ing every single
    // camera frame would burn CPU/battery for no benefit on the vast majority
    // of frames, which show no barcode at all yet.
    priceScanner: TextRecognizer? = null,
    onPriceTextDetected: ((String) -> Unit)? = null,
) {
    val media = proxy.image
    if (media == null) { proxy.close(); return }
    val rotation = proxy.imageInfo.rotationDegrees
    val (imgW, imgH) = if (rotation == 90 || rotation == 270)
        Pair(proxy.height, proxy.width) else Pair(proxy.width, proxy.height)
    val img = InputImage.fromMediaImage(media, rotation)

    // Every detector that actually runs on this frame is tracked here - closing
    // the proxy only once every task in flight has completed (not just the
    // first one to finish, as a single addOnCompleteListener would) so none of
    // them ever reads from a buffer CameraX has already recycled. priceScanner
    // only ever runs conditionally (see below), so its slot is released
    // immediately here if it turns out not to fire for this frame.
    val pending = java.util.concurrent.atomic.AtomicInteger(
        1 + (if (objectDetector != null) 1 else 0) + (if (priceScanner != null) 1 else 0),
    )
    fun releaseIfDone() { if (pending.decrementAndGet() == 0) proxy.close() }

    scanner.process(img)
        .addOnSuccessListener { barcodes ->
            // Every valid barcode in the frame is reported, not just the first one ML
            // Kit happens to list - a shelf with two products side by side (or a
            // multi-pack showing several distinct codes at once) previously only ever
            // got the single barcode ML Kit's list-order put first that frame, silently
            // dropping every other one in view no matter how clearly it decoded, which
            // made both a plain single scan and instant/multi mode miss neighboring
            // codes entirely. Deduplicated by value (the same physical barcode can
            // occasionally decode twice from slightly different regions of one frame).
            val detected = mutableListOf<DetectedBarcode>()
            val seenValues = mutableSetOf<String>()
            for (bc in barcodes) {
                val raw = bc.rawValue ?: continue
                // CODABAR: older/still-common on some pharmacy and blood-bank packaging
                // (e.g. some pre-DataMatrix French medication boxes) - like CODE_128/ITF
                // it's a symbology, not a fixed-length GTIN encoding, so the digit-length
                // filter below (unchanged) is what actually decides whether a decoded
                // value looks like a real product/medication barcode.
                val digits = if (bc.format in listOf(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                        Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128,
                        Barcode.FORMAT_ITF, Barcode.FORMAT_CODABAR)) {
                    raw.filter { it.isDigit() }.takeIf { it.length in listOf(8, 12, 13, 14) }
                } else if (bc.format == Barcode.FORMAT_DATA_MATRIX || bc.format == Barcode.FORMAT_QR_CODE) {
                    // Many French medication boxes carry no EAN-13 at all - only a
                    // GS1 DataMatrix (2D "CIP DataMatrix"), and some carry a plain
                    // QR code that just encodes the barcode digits directly. Try the
                    // GS1 GTIN extraction first; if that finds nothing, fall back to
                    // treating the raw value as a plain barcode only when it's
                    // exactly digits of a plausible length (never for an arbitrary
                    // QR code like a URL).
                    extractGtinFromGs1(raw)
                        ?: raw.takeIf { it.all(Char::isDigit) && it.length in listOf(8, 12, 13, 14) }
                } else null
                if (digits != null && seenValues.add(digits)) {
                    onBarcodeDetected(digits)
                    bc.boundingBox?.let { detected += DetectedBarcode(digits, it) }
                }
            }
            onBarcodesInFrame?.invoke(detected, imgW, imgH)

            // Only OCR'd on a frame that actually decoded a barcode (see this
            // param's own doc comment above) - every other frame releases this
            // slot immediately without ever calling process().
            if (priceScanner != null) {
                if (detected.isNotEmpty()) {
                    priceScanner.process(img)
                        .addOnSuccessListener { result -> onPriceTextDetected?.invoke(result.text) }
                        .addOnCompleteListener { releaseIfDone() }
                } else {
                    releaseIfDone()
                }
            }
        }
        .addOnCompleteListener { releaseIfDone() }

    objectDetector?.process(img)
        ?.addOnSuccessListener { objects ->
            onObjectsDetected?.invoke(objects.map { DetectedBox(it.trackingId ?: -1, it.boundingBox) }, imgW, imgH)
        }
        ?.addOnCompleteListener { releaseIfDone() }
}
