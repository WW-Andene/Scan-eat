package fr.scanneat.data.repository.scan

import android.graphics.BitmapFactory
import android.util.Base64
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import fr.scanneat.data.remote.api.ImagePayload
import fr.scanneat.domain.engine.nutrition.LabelInconsistency
import fr.scanneat.domain.engine.nutrition.OcrLine
import fr.scanneat.domain.engine.nutrition.parseLabelText
import fr.scanneat.domain.model.Product
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ParseLabelResult(
    val product: Product,
    val warnings: List<String>,
    val barcode: String? = null,
)

/**
 * Reads a packaging photo entirely on the device: ML Kit text recognition
 * (bundled model, no network) then [parseLabelText]. Replaces the former
 * cloud vision-model call - nothing leaves the phone and no API key is needed.
 */
@javax.inject.Singleton
class OnDeviceLabelReader @javax.inject.Inject constructor() {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun parseLabel(images: List<ImagePayload>, lang: String = "fr"): ParseLabelResult {
        val lines = readLines(images)
        val parsed = parseLabelText(lines, lang)
        if (parsed.nutrientsRead.isEmpty() && !parsed.ingredientsRead) {
            throw ProductNotFoundException(
                if (lang == "en") "No label text could be read — photograph the ingredient list and the nutrition table, sharp and well lit"
                else "Aucun texte d'étiquette lisible — photographiez la liste d'ingrédients et le tableau nutritionnel, nets et bien éclairés",
            )
        }
        val warnings = warnings(parsed.nutrientsRead, parsed.ingredientsRead, lang) + parsed.inconsistencies.map { inconsistencyMessage(it, lang) }
        return ParseLabelResult(parsed.product, warnings, parsed.barcode)
    }

    /** Every recognised text line across [images]. Several photos (front + back)
     *  are stacked vertically so each keeps its own reading order and rows from
     *  different photos never overlap. */
    suspend fun readLines(images: List<ImagePayload>): List<OcrLine> {
        val lines = mutableListOf<OcrLine>()
        var yOffset = 0
        for (img in images) {
            val bytes = Base64.decode(img.base64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            val text = recognize(InputImage.fromBitmap(bitmap, 0))
            text.textBlocks.flatMap { it.lines }.forEach { line ->
                val box = line.boundingBox ?: return@forEach
                lines += OcrLine(line.text, box.left, box.top + yOffset, box.right, box.bottom + yOffset)
            }
            yOffset += bitmap.height + 1
        }
        return lines
    }

    private fun inconsistencyMessage(i: LabelInconsistency, lang: String): String {
        val en = lang == "en"
        val what = when (i) {
            LabelInconsistency.SUGARS_ABOVE_CARBS -> if (en) "sugars are higher than carbohydrates" else "les sucres dépassent les glucides"
            LabelInconsistency.SATURATED_ABOVE_FAT -> if (en) "saturated fat is higher than total fat" else "les acides gras saturés dépassent les matières grasses"
            LabelInconsistency.MACROS_ABOVE_100G -> if (en) "the nutrients add up to more than 100 g" else "les nutriments dépassent 100 g au total"
            LabelInconsistency.ENERGY_MISMATCH -> if (en) "the energy doesn't match the nutrients" else "l'énergie ne correspond pas aux nutriments"
        }
        return if (en) "Probably misread: $what — retake a sharper photo of the table or correct the values"
        else "Lecture probablement erronée : $what — reprenez une photo plus nette du tableau ou corrigez les valeurs"
    }

    private fun warnings(read: Set<String>, ingredientsRead: Boolean, lang: String): List<String> {
        val en = lang == "en"
        val out = mutableListOf(
            if (en) "Read on this device from the photo — check the values against the label"
            else "Lu sur l'appareil depuis la photo — vérifiez les valeurs avec l'étiquette",
        )
        val labels = linkedMapOf(
            "energy" to (if (en) "energy" else "énergie"), "fat" to (if (en) "fat" else "matières grasses"),
            "saturatedFat" to (if (en) "saturated fat" else "acides gras saturés"), "carbs" to (if (en) "carbohydrates" else "glucides"),
            "sugars" to (if (en) "sugars" else "sucres"), "fiber" to (if (en) "fibre" else "fibres"),
            "protein" to (if (en) "protein" else "protéines"), "salt" to (if (en) "salt" else "sel"),
        )
        val missing = labels.filterKeys { it !in read }.values
        if (missing.isNotEmpty() && read.isNotEmpty()) {
            out += if (en) "Not read on the label (counted as 0): ${missing.joinToString()}" else "Non lu sur l'étiquette (compté comme 0) : ${missing.joinToString()}"
        } else if (read.isEmpty()) {
            out += if (en) "Nutrition table not read — photograph it to get a complete score" else "Tableau nutritionnel non lu — photographiez-le pour un score complet"
        }
        if (!ingredientsRead) {
            out += if (en) "Ingredient list not read — allergens and additives can't be checked" else "Liste d'ingrédients non lue — allergènes et additifs non vérifiables"
        }
        return out
    }

    private suspend fun recognize(image: InputImage): Text = suspendCancellableCoroutine { cont ->
        recognizer.process(image)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }
}
