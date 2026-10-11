package fr.scanneat.presentation.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import fr.scanneat.data.remote.api.ImagePayload
import java.io.ByteArrayOutputStream

private const val THUMBNAIL_MAX_PX = 160
private const val UPLOAD_MAX_PX = 1024   // ample for label OCR - well under the raw 1600x1200 capture

/**
 * Photo-queue thumbnails render in a 64.dp box, but the capture itself is
 * 1600×1200 — holding that full bitmap per queued photo (~7.7MB each,
 * ARGB_8888) risks real memory pressure with more than one or two photos
 * queued. Scale down (aspect-preserving) for display.
 *
 * The upload bytes are also capped, separately from the thumbnail - the
 * vision model needs enough resolution to read label text, not the raw
 * capture resolution, and every uncapped photo was previously shipped to
 * Groq (or proxied through server mode) at full 1600×1200 for no OCR
 * accuracy benefit, just wasted bandwidth/tokens.
 */
internal fun Bitmap.toPayload(): ImagePayload {
    val uploadScale = UPLOAD_MAX_PX.toFloat() / maxOf(width, height)
    val uploadBitmap = if (uploadScale < 1f) {
        Bitmap.createScaledBitmap(this, (width * uploadScale).toInt(), (height * uploadScale).toInt(), true)
    } else this

    val out = ByteArrayOutputStream()
    uploadBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)

    val thumbScale = THUMBNAIL_MAX_PX.toFloat() / maxOf(width, height)
    val thumb = if (thumbScale < 1f) {
        Bitmap.createScaledBitmap(this, (width * thumbScale).toInt(), (height * thumbScale).toInt(), true)
    } else this

    if (uploadBitmap !== this) uploadBitmap.recycle()
    if (thumb !== this) recycle()

    return ImagePayload(
        base64    = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP),
        thumbnail = thumb,
    )
}

/**
 * Rebuilds an ImagePayload from a disk-persisted base64 string (see
 * ScanViewModel's photo-queue disk cache, added so process death mid-scan
 * doesn't silently discard queued photos). [base64] is already the
 * upload-resolution (<=1024px) JPEG this file's own toPayload() produced, so
 * only the thumbnail needs regenerating - no second upload-scale pass.
 * Returns null on a corrupt/undecodable entry rather than throwing, since
 * this only ever runs against this app's own previously-written cache file.
 */
internal fun restoreImagePayload(base64: String): ImagePayload? {
    val bytes = runCatching { Base64.decode(base64, Base64.NO_WRAP) }.getOrNull() ?: return null
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val thumbScale = THUMBNAIL_MAX_PX.toFloat() / maxOf(decoded.width, decoded.height)
    val thumb = if (thumbScale < 1f) {
        Bitmap.createScaledBitmap(decoded, (decoded.width * thumbScale).toInt(), (decoded.height * thumbScale).toInt(), true)
    } else decoded
    if (thumb !== decoded) decoded.recycle()
    return ImagePayload(base64 = base64, thumbnail = thumb)
}
