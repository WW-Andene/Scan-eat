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
import fr.scanneat.data.repository.planning.RecipeComponent
import fr.scanneat.domain.engine.planning.parseIngredientLine
import fr.scanneat.domain.engine.planning.toRecipeComponent
import java.io.ByteArrayOutputStream

/**
 * An imported recipe's ingredient lines are parsed locally (IngredientLineParser):
 * lines that match a food become real RecipeComponents with nutrition; the rest,
 * plus the steps and source, land in the notes for the user to finish by hand.
 */
/** Imported ingredient lines matched to the food database (with grams), plus the lines that weren't. */
internal data class ImportedIngredients(
    val components: List<RecipeComponent>,
    val unmatched: List<String>,
    val estimated: List<String>,
)

internal fun splitImportedIngredients(result: FetchedRecipeResult): ImportedIngredients {
    val parsed = result.ingredients.map(::parseIngredientLine)
    val matched = parsed.mapNotNull { p -> p.toRecipeComponent()?.let { p to it } }
    return ImportedIngredients(
        components = matched.map { it.second },
        unmatched = parsed.filter { p -> matched.none { it.first === p } }.map { it.line },
        estimated = matched.filter { it.first.gramsEstimated }.map { it.first.line },
    )
}

@Composable
internal fun formatImportedNotes(result: FetchedRecipeResult, split: ImportedIngredients = splitImportedIngredients(result)): String = buildString {
    // Matched lines are already in the ingredient list; only the rest needs transcribing.
    if (split.unmatched.isNotEmpty()) {
        appendLine(stringResource(R.string.recipes_import_notes_ingredients))
        split.unmatched.forEach { appendLine("- $it") }
        appendLine()
    }
    if (split.estimated.isNotEmpty()) {
        appendLine(stringResource(R.string.recipes_import_notes_estimated))
        split.estimated.forEach { appendLine("- $it") }
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
