package fr.scanneat.data.remote.api

import android.graphics.Bitmap

/**
 * Holds a JPEG image as base64 for API transmission.
 * [thumbnail] is the original Bitmap kept in memory for UI display only.
 * It is never serialized.
 */
data class ImagePayload(
    val base64: String,
    val mime: String = "image/jpeg",
    @Transient val thumbnail: Bitmap? = null,
)
