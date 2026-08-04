package fr.scanneat.presentation.recipes

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fr.scanneat.R
import fr.scanneat.data.remote.api.ImagePayload
import fr.scanneat.data.repository.planning.FetchedRecipeResult
import java.io.ByteArrayOutputStream

/**
 * FetchedRecipeResult's ingredients/steps are free text (no gram weight to build a real
 * RecipeComponent from — see that class's own doc comment), so an imported recipe's full
 * detail lands here as pre-filled notes text instead of being silently dropped, while the
 * user adds macro-tracked ingredients via the normal search flow in AddRecipeDialog.
 */
@Composable
internal fun formatImportedNotes(result: FetchedRecipeResult): String = buildString {
    if (result.ingredients.isNotEmpty()) {
        appendLine(stringResource(R.string.recipes_import_notes_ingredients))
        result.ingredients.forEach { appendLine("- $it") }
        appendLine()
    }
    if (result.steps.isNotEmpty()) {
        appendLine(stringResource(R.string.recipes_import_notes_steps))
        result.steps.forEachIndexed { i, step -> appendLine("${i + 1}. $step") }
        appendLine()
    }
    result.cookTimeMinutes?.let { appendLine(stringResource(R.string.recipes_import_notes_cook_time, it)) }
    val kcal = result.kcal
    if (kcal != null) {
        appendLine(stringResource(
            R.string.recipes_import_notes_nutrition,
            kcal.toInt(), (result.proteinG ?: 0.0).toInt(), (result.carbsG ?: 0.0).toInt(), (result.fatG ?: 0.0).toInt(),
        ))
    }
    if (result.sourceUrl.isNotBlank()) appendLine(stringResource(R.string.recipes_import_notes_source, result.sourceUrl))
}.trim()

/**
 * Decodes a gallery-picked photo into the same [ImagePayload] shape Scan already
 * uses, scaled down for upload same as ScanViewModel.toPayload() (no OCR accuracy
 * benefit past a moderate resolution, just wasted bandwidth). No EXIF-orientation
 * correction on the API 26/27 fallback path (MediaStore.Images.Media.getBitmap
 * predates ImageDecoder's automatic handling of it) - a real but minor limitation
 * on a shrinking slice of devices, not a functional failure.
 */
internal fun decodeImagePayload(context: android.content.Context, uri: Uri): ImagePayload? = runCatching {
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
            decoder.isMutableRequired = false
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
    val maxPx = 1024
    val scale = maxPx.toFloat() / maxOf(bitmap.width, bitmap.height)
    val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
    if (scaled !== bitmap) bitmap.recycle()
    scaled.recycle()
    ImagePayload(base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
}.getOrNull()
