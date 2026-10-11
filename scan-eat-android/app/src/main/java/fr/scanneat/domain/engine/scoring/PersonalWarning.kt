package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.Profile

/**
 * One-line personal-safety summary of [product] against [profile] - the allergens, diet
 * and health conditions the user declared - or null when nothing conflicts.
 *
 * Single source of truth for the "warning chip" shown on History, Dashboard, Diary,
 * Meal plan, Grocery, Recipes, Templates and the scan preview. Each of those screens
 * used to hand-copy this logic, and the copies had drifted: only some included
 * health-condition cautions (so the same product showed a caution in the Diary but
 * nothing in History), all of them listed only the first allergen, and a precautionary
 * "may contain traces" hit was worded like a confirmed allergen.
 */
fun personalWarning(product: Product, profile: Profile, lang: String = "fr"): String? {
    val en = lang == "en"
    val parts = mutableListOf<String>()

    if (profile.allergens.isNotEmpty()) {
        val hits = checkUserAllergens(product, profile.allergens, lang)
        val confirmed = hits.filter { !it.isTraceOnly }.map { if (en) it.labelEn else it.labelFr }
        val traces = hits.filter { it.isTraceOnly }.map { if (en) it.labelEn else it.labelFr }
        if (confirmed.isNotEmpty()) parts += (if (en) "Allergen: " else "Allergène : ") + confirmed.joinToString(", ")
        if (traces.isNotEmpty()) parts += (if (en) "May contain: " else "Peut contenir : ") + traces.joinToString(", ")
    }

    checkDiet(product, profile.diet, lang).reason?.let { parts += it }
    healthConditionCautions(product, profile.healthConditions, lang).firstOrNull()?.let { parts += it }

    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
