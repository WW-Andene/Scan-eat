package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*
import fr.scanneat.util.formatDecimal

// ============================================================================
// DIET CHECKER — port of public/core/diets.js
//
// AUTHORITATIVE sources (preserved from original):
//   Vegan/Vegetarian: Vegan Society UK / Vegetarian Society UK definitions
//   Ketogenic: Volek & Phinney clinical ketosis; net carbs ≤10 g/100 g
//   Halal: Qur'anic prohibition (haram 5:3, 5:90)
//   Kosher: Torah (Leviticus 11); certification required for full compliance
//   Gluten-free: EU Commission Regulation (EC) 41/2009 ≤20 mg/kg gluten
//   Low-FODMAP: Monash University Low FODMAP Diet App reference lists
//
// EDITORIAL: detection patterns are heuristic — not a substitute for
// the manufacturer's own allergen/diet declaration.
// ============================================================================

enum class DietKey(val key: String, val labelFr: String, val labelEn: String) {
    NONE("none", "Aucun (score classique)", "None (classic only)"),
    VEGETARIAN("vegetarian", "Végétarien", "Vegetarian"),
    VEGAN("vegan", "Végan", "Vegan"),
    PESCATARIAN("pescatarian", "Pescétarien", "Pescatarian"),
    KETO("keto", "Cétogène", "Ketogenic"),
    HALAL("halal", "Halal", "Halal"),
    KOSHER("kosher", "Casher", "Kosher"),
    GLUTEN_FREE("gluten_free", "Sans gluten", "Gluten-free"),
    DAIRY_FREE("dairy_free", "Sans lactose", "Dairy-free"),
    PALEO("paleo", "Paléo", "Paleo"),
    LOW_FODMAP("low_fodmap", "Pauvre en FODMAP", "Low-FODMAP"),
    MEDITERRANEAN("mediterranean", "Méditerranéen", "Mediterranean"),
    CARNIVORE("carnivore", "Carnivore", "Carnivore");

    companion object {
        fun fromKey(k: String) = entries.firstOrNull { it.key == k } ?: NONE
    }
}

data class DietResult(
    val compliant: Boolean,
    val violations: List<String>,
    val preferredHits: List<String>,
    val certified: Boolean,
    val reason: String?,
)

// b(), DietDef, DIET_DEFS, CERTIFICATION_OVERRIDE_DIETS, UNVERIFIABLE_DIETS
// moved to DietDefinitions.kt

/**
 * Check a product against a diet definition.
 * Port of checkDiet() from diets.js.
 */
/** The diet's own description/adequacy note, e.g. for display next to a diet picker. */
fun dietNote(dietKey: DietKey, lang: String = "fr"): String? =
    DIET_DEFS[dietKey]?.let { if (lang == "en") it.noteEn else it.noteFr }

fun checkDiet(product: Product, dietKey: DietKey, lang: String = "fr"): DietResult {
    if (dietKey == DietKey.NONE) {
        return DietResult(true, emptyList(), emptyList(), false, null)
    }

    val def = DIET_DEFS[dietKey] ?: return DietResult(true, emptyList(), emptyList(), false, null)

    val haystacks: List<String> = listOf(product.name) + product.ingredients.map { it.name }

    // Matches against a lowercased copy - same accented-uppercase gap as
    // AllergenDetector.detectAllergens() (RegexOption.IGNORE_CASE alone doesn't
    // Unicode-fold accents), so e.g. "BŒUF HACHÉ" silently passed as vegetarian-
    // compliant. firstOrNull still returns the original-casing element from
    // [haystacks], so violation/preferredHits text keeps its original casing.
    fun testAny(re: Regex): String? = haystacks.firstOrNull { re.containsMatchIn(it.lowercase()) }

    val violations    = mutableListOf<String>()
    val preferredHits = mutableListOf<String>()

    for (re in def.forbidden) {
        testAny(re)?.let { violations += it }
    }
    for (re in def.preferred) {
        testAny(re)?.let { preferredHits += it }
    }

    // Macro-based check - data-driven off DietDef.maxNetCarbsG/minFatFractionOfKcal
    // rather than hardcoded to one enum value, so any future diet needing the same
    // net-carbs/fat-fraction rule (not just KETO) only needs a DIET_DEFS entry.
    if (def.maxNetCarbsG != null || def.minFatFractionOfKcal != null) {
        val netCarbs = (product.nutrition.carbsG - product.nutrition.fiberG).coerceAtLeast(0.0)
        val maxNet   = def.maxNetCarbsG ?: 10.0
        if (netCarbs > maxNet) {
            // formatDecimal() pins Locale.US - %.1f with no explicit Locale renders
            // "15,0" on comma-decimal devices, which then glued onto an English/
            // French unit suffix looked mixed-up twice over.
            val netCarbsStr = netCarbs.formatDecimal(1)
            violations += if (lang == "en") "$netCarbsStr g net carbs/100 g" else "$netCarbsStr g glucides nets/100 g"
        }

        val kcal    = product.nutrition.energyKcal
        val fatKcal = product.nutrition.fatG * 9.0
        val fatFrac = if (kcal > 0) fatKcal / kcal else 0.0
        val minFat  = def.minFatFractionOfKcal ?: 0.60
        // A whole-food protein source (meat/fish/eggs, roughly >=20g protein/100g)
        // fails this ratio on its own even when genuinely fatty by any normal
        // standard - e.g. braised chicken thigh (~16g fat, ~24g protein, ~230kcal)
        // lands at ~58% fat-of-calories, just under the 60% bar, because its own
        // high protein content dilutes the ratio. Keto diners pair meat with added
        // fat rather than expecting the meat itself to hit a fat-ratio threshold,
        // so this check only makes sense for a product claiming to BE a complete
        // keto meal/snack - skip it here rather than flag ordinary keto-staple
        // proteins as "not enough fat."
        if (kcal > 50 && fatFrac < minFat && product.nutrition.proteinG < 20.0) {
            val fatPct = (fatFrac * 100).toInt()
            violations += if (lang == "en") "only $fatPct% from fat" else "seulement $fatPct % de lipides"
        }
    }

    // Only diets whose `preferred` list is actual certification marks (halal/kosher/
    // vegan seals) should report certified=true. MEDITERRANEAN's `preferred` list is
    // diet-friendly ingredient words ("poisson", "tomate"...), not certifications - it
    // isn't in CERTIFICATION_OVERRIDE_DIETS, so without this guard `certified` was
    // defined identically to `preferredHits.isNotEmpty()` for every diet, making
    // PersonalScoreEngine's separate "certification detected" (+5) vs "diet-friendly
    // ingredients" (+3) branches collapse into always taking the former — the
    // preferredHits-only branch below could never actually run, and Mediterranean
    // matches (e.g. "poisson") were mislabeled as a nonexistent "certification".
    val certified              = preferredHits.isNotEmpty() && dietKey in CERTIFICATION_OVERRIDE_DIETS
    val certificationOverride  = certified
    val compliant              = certificationOverride || violations.isEmpty()
    val effectiveViolations    = if (certificationOverride) emptyList() else violations

    val reason = if (compliant) null else {
        val unverifiableNote = if (dietKey in UNVERIFIABLE_DIETS) {
            val note = if (lang == "en") def.noteEn else def.noteFr
            val connector = if (lang == "en") " — Note: " else " — Remarque : "
            "$connector$note"
        } else ""
        val labelStr = if (lang == "en") dietKey.labelEn else dietKey.labelFr
        val prefix   = if (lang == "en") "Not" else "Non"
        "$prefix $labelStr : ${effectiveViolations.take(3).joinToString()}$unverifiableNote"
    }

    return DietResult(
        compliant      = compliant,
        violations     = effectiveViolations,
        preferredHits  = preferredHits,
        certified      = certified,
        reason         = reason,
    )
}
