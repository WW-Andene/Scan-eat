package fr.scanneat.data.repository.scan

import android.graphics.BitmapFactory
import android.util.Base64
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import fr.scanneat.data.remote.api.ImagePayload
import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.domain.engine.planning.findFoodByName
import fr.scanneat.domain.engine.nutrition.toProduct
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Identifies an unlabelled food (fresh produce, a plain dish) from a photo on
 * the device: ML Kit's bundled image-labelling model names what it sees
 * ("Banana", "Bread"...) and the most confident label that matches an entry
 * of the built-in food database wins. No network, no API key. Replaces the
 * former cloud vision-model "identify".
 */
@javax.inject.Singleton
class OnDeviceFoodIdentifier @javax.inject.Inject constructor() {

    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(MIN_CONFIDENCE).build())
    }

    /** Best food-database match across all photos, or null when nothing recognisable matched. */
    suspend fun identify(images: List<ImagePayload>): FoodEntry? {
        val labels = images.flatMap { img ->
            val bytes = Base64.decode(img.base64, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@flatMap emptyList()
            label(InputImage.fromBitmap(bitmap, 0))
        }
        return labels.sortedByDescending { it.confidence }
            .filter { it.text.lowercase() !in GENERIC_LABELS }
            .firstNotNullOfOrNull { findFoodByName(it.text) }
    }

    fun toResultProduct(food: FoodEntry) = food.toProduct(100.0)

    private suspend fun label(image: InputImage): List<ImageLabel> = suspendCancellableCoroutine { cont ->
        labeler.process(image)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    private companion object {
        const val MIN_CONFIDENCE = 0.6f

        /** Labels the model gives for any food shot - they name no specific food and
         *  would otherwise prefix-match an unrelated entry ("Fruit" -> "fruit de la passion"). */
        val GENERIC_LABELS = setOf(
            "food", "fruit", "vegetable", "dish", "cuisine", "meal", "ingredient", "produce", "plant",
            "tableware", "plate", "bowl", "cutlery", "recipe", "baked goods", "fast food", "junk food",
            "snack", "dessert", "natural foods", "superfood", "leaf", "flower", "table", "drink",
        )
    }
}
