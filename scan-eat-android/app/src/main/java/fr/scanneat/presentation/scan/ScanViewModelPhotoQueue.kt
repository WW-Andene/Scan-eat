package fr.scanneat.presentation.scan

import androidx.lifecycle.viewModelScope
import fr.scanneat.data.remote.api.ImagePayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Disk-backed photo-queue persistence + mutation for [ScanViewModel], split out of
 * the main file to keep it from growing unbounded. All extension functions here
 * touch the same [ScanViewModel]-private-turned-internal state (`_images`,
 * `_scannedBarcode`, `_state`, `pendingBarcode`/`pendingBarcodeStreak`) the main
 * file itself uses — no behavior change from when these lived inline.
 *
 * Disk-backed cache of the photo queue - a ViewModel is a fresh instance after
 * process death (only config-change survives it, not this), so the in-memory
 * _images StateFlow was previously the only copy: backgrounding mid multi-photo
 * shelf/scan capture under memory pressure silently discarded every queued photo
 * with no way back, forcing the whole capture sequence to be redone.
 * filesDir (not cacheDir) - this represents real, deliberately-captured user
 * input the OS shouldn't be free to opportunistically wipe under storage
 * pressure the way it can with cacheDir, even though it's still transient data
 * this ViewModel itself deletes once consumed (see persistQueue()'s callers).
 * One newline-delimited base64 string per photo - base64 (NO_WRAP) never
 * contains a newline itself, so no escaping/JSON parsing is needed.
 */
internal val ScanViewModel.queueFile: File get() = File(appContext.filesDir, "scan_photo_queue")

internal fun ScanViewModel.persistQueue() {
    viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val images = _images.value
            if (images.isEmpty()) queueFile.delete()
            else queueFile.writeText(images.joinToString("\n") { it.base64 })
        }
    }
}

/** Called from ScanViewModel's init block - best-effort restore, off Main - see
 *  [queueFile]'s own doc comment. Wrapped in runCatching (not just relying on
 *  individual restoreImagePayload() null returns) so a corrupt/unreadable cache
 *  file can never crash the app on launch, only silently lose the recovery
 *  opportunity. */
internal fun ScanViewModel.restorePhotoQueue() {
    viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            if (queueFile.exists()) {
                val restored = queueFile.readText().split("\n")
                    .filter { it.isNotBlank() }
                    .mapNotNull { restoreImagePayload(it) }
                if (restored.isNotEmpty()) _images.value = restored else queueFile.delete()
            }
        }
    }
}

/**
 * Capped at MAX_QUEUED_PHOTOS — previously unbounded, unlike the server's own
 * RouteHelpers.normalizeImages() (MAX_IMAGES=8, added round 30). A user tapping
 * the capture FAB repeatedly grew this list forever: in SERVER mode the extra
 * images were uploaded over the network only to be silently dropped server-side,
 * and in DIRECT mode (straight to Groq/Cerebras, no server-side cap at all) every
 * queued photo's full base64 payload was sent to the vision LLM with no limit.
 * Silently ignored past the cap rather than surfaced as an error, matching this
 * screen's other silent caps (e.g. recentBarcodes.takeLast(5)).
 */
// Takes an already-built ImagePayload rather than a raw Bitmap - toPayload()'s
// CPU work (two createScaledBitmap calls + a JPEG compress) is now done by the
// caller (CameraPreview's background capture executor), not here. Keeping this
// synchronous - rather than wrapping it in viewModelScope.launch(Dispatchers.Default) -
// avoids making photo-queue mutation non-deterministic from the caller's point of
// view, which ScanViewModelTest's synchronous addPhoto()-then-assert calls rely on.
internal fun ScanViewModel.addPhoto(payload: ImagePayload) {
    if (_images.value.size >= ScanViewModel.MAX_QUEUED_PHOTOS) return
    _images.value = _images.value + payload
    persistQueue()
}

internal fun ScanViewModel.removePhoto(index: Int) {
    _images.value = _images.value.toMutableList().also { it.removeAt(index) }
    persistQueue()
}

internal fun ScanViewModel.clearQueue() {
    _images.value = emptyList()
    _scannedBarcode.value = null
    _state.value = ScanUiState.Idle
    // See resultConsumed()'s own doc comment for why this stale-streak leak
    // needs resetting here too - clearQueue() is the other path (dismissed
    // found-dialog, manual queue clear) that can leave photos empty again
    // after onBarcodeDetected()'s guard suppressed the debounce for a while.
    pendingBarcode = null
    pendingBarcodeStreak = 0
    persistQueue()
}
