package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*
import fr.scanneat.util.formatDecimal

// ============================================================================
// SECTION 7: PILLAR 3 — NEGATIVE NUTRIENTS (max 25)
// ============================================================================

/** Shared with ScoringEngine.kt's high-proof veto — was duplicated as a
 *  literal 15.0 in both places, so tuning one without the other would have
 *  silently desynced the deduction from the veto it's meant to accompany. */
internal const val HIGH_ABV_THRESHOLD = 15.0

fun scoreNegativeNutrients(product: Product, lang: String = "en"): PillarScore {
    val en = lang == "en"
    val MAX = 25
    val deductions = mutableListOf<Deduction>()
    val bonuses = mutableListOf<Deduction>()
    val n = product.nutrition
    val thresholds = getThresholds(product.category)
    var score = MAX.toDouble()

    // Saturated fat
    val sat = n.saturatedFatG
    val (satMod, satMaj, satCrit) = thresholds.satFatThresholds
    val satLabel = if (en) "Saturated fat" else "Graisses saturées"
    when {
        sat > satCrit -> { score -= 9; deductions += Deduction("negative_nutrients", "$satLabel ${sat}g/100g (>$satCrit " + (if (en) "critical" else "critique") + ")", -9.0, Severity.CRITICAL) }
        sat > satMaj  -> { score -= 6; deductions += Deduction("negative_nutrients", "$satLabel ${sat}g/100g (>$satMaj " + (if (en) "major" else "majeur") + ")", -6.0, Severity.MAJOR) }
        sat > satMod  -> { score -= 3; deductions += Deduction("negative_nutrients", "$satLabel ${sat}g/100g (>$satMod " + (if (en) "moderate" else "modéré") + ")", -3.0, Severity.MODERATE) }
    }

    // Sugars
    val sugars = n.addedSugarsG ?: n.sugarsG
    val sugarLabel = if (n.addedSugarsG != null) (if (en) "Added sugars" else "Sucres ajoutés")
                     else (if (en) "Total sugars (added not declared)" else "Sucres totaux (sucres ajoutés non déclarés)")
    val (sMinor, sMod, sMaj, sCrit) = thresholds.sugarThresholds
    when {
        sugars > sCrit -> { score -= 12; deductions += Deduction("negative_nutrients", "$sugarLabel ${sugars}g/100g (>$sCrit " + (if (en) "critical" else "critique") + ")", -12.0, Severity.CRITICAL) }
        sugars > sMaj  -> { score -= 9;  deductions += Deduction("negative_nutrients", "$sugarLabel ${sugars}g/100g (>$sMaj " + (if (en) "major" else "majeur") + ")", -9.0, Severity.MAJOR) }
        sugars > sMod  -> { score -= 6;  deductions += Deduction("negative_nutrients", "$sugarLabel ${sugars}g/100g (>$sMod " + (if (en) "moderate" else "modéré") + ")", -6.0, Severity.MODERATE) }
        sugars > sMinor -> { score -= 3; deductions += Deduction("negative_nutrients", "$sugarLabel ${sugars}g/100g (>$sMinor " + (if (en) "minor" else "mineur") + ")", -3.0, Severity.MINOR) }
    }

    // Salt — category-relative like sat fat/sugar above, not a flat cutoff.
    // Salt is structurally inherent to some categories (soy sauce/miso are
    // brine-fermented; dry-cured meat is salt-cured) the same way sat fat is
    // inherent to cheese - a flat 1.5g bar flagged literally every soy sauce
    // and prosciutto regardless of whether it was unusually salty even for
    // its own category. See CategoryThresholds.kt's saltThresholds doc comment.
    // Defensive fallback lives here too, not just in OffMapper.kt's OFF-specific
    // conversion — any other entry path (LLM extraction, manual entry, a future
    // parser) that populates sodiumMg without saltG would otherwise silently
    // score as salt-free. 2.5 is the standard sodium→salt conversion factor
    // (NaCl molar mass ratio), same as OffMapper.kt's own fallback.
    val salt = if (n.saltG > 0.0) n.saltG else (n.sodiumMg?.let { it / 1000.0 * 2.5 } ?: 0.0)
    val (saltMinor, saltMod, saltMaj) = thresholds.saltThresholds
    val saltLabel = if (en) "Salt" else "Sel"
    when {
        salt > saltMaj  -> { score -= 6; deductions += Deduction("negative_nutrients", "$saltLabel ${salt}g/100g (>$saltMaj " + (if (en) "major" else "majeur") + ")", -6.0, Severity.MAJOR) }
        salt > saltMod  -> { score -= 4; deductions += Deduction("negative_nutrients", "$saltLabel ${salt}g/100g (>$saltMod " + (if (en) "moderate" else "modéré") + ")", -4.0, Severity.MODERATE) }
        salt > saltMinor -> { score -= 2; deductions += Deduction("negative_nutrients", "$saltLabel ${salt}g/100g (>$saltMinor " + (if (en) "minor" else "mineur") + ")", -2.0, Severity.MINOR) }
    }

    // Trans fat
    val trans = n.transFatG ?: 0.0
    if (trans > 0.1) {
        score -= 10
        deductions += Deduction("negative_nutrients", if (en) "Trans fat present: ${trans}g/100g (no safe level)" else "Présence de graisses trans : ${trans}g/100g (aucun seuil sûr)", -10.0, Severity.CRITICAL)
    }

    // Alcohol — WHO/IARC classify ethanol as a Group 1 carcinogen with no
    // established safe consumption level (same "no safe level" framing as
    // trans fat above), yet nothing before this pillar ever looked at
    // alcoholPercentVol: an anonymous scan of a beer/wine with unremarkable
    // sugar/fat/salt scored as a healthy beverage purely because this was the
    // only place a base (non-personalized) penalty could live - the
    // pregnancy/migraine/GI alcohol checks in HealthConditionAdjustments.kt
    // only fire when the user has declared that specific condition. Tiered by
    // %vol (dealcoholised <1.2%, beer-strength, wine-strength, spirit-strength)
    // rather than a flat penalty, since a 0.3% "sans alcool" beer and a 40%
    // spirit are not the same risk.
    //
    // Belt-and-suspenders by design, not double-counting by accident: this
    // tiered deduction and checkVeto's separate alcohol cap are not
    // independent penalties stacking on top of each other in practice. For a
    // clean-profile beer/wine (the exact case the veto was added to close),
    // the veto's flat cap (54/45/40) binds regardless of this deduction's
    // finer -6/-9/-12 gradient — the pillar math here rarely changes the
    // FINAL score once the veto is in play. It's kept anyway because it's
    // still the only mechanism that shows up when the veto DOESN'T bind (a
    // product whose other pillars already sit below the veto's cap) and it
    // keeps the audit trail proportional to %vol rather than a flat "alcohol
    // present" flag. Same layering applies to trans fat and nitrites below.
    val abv = n.alcoholPercentVol ?: 0.0
    val alcoholLabel = if (en) "Alcohol" else "Alcool"
    when {
        abv > HIGH_ABV_THRESHOLD -> { score -= 12; deductions += Deduction("negative_nutrients", "$alcoholLabel ${abv.formatDecimal(1)}% vol (" + (if (en) "no safe consumption level — WHO/IARC Group 1 carcinogen" else "aucun seuil de consommation sûr — cancérigène IARC groupe 1 (OMS)") + ")", -12.0, Severity.CRITICAL) }
        abv > 5.0  -> { score -= 9;  deductions += Deduction("negative_nutrients", "$alcoholLabel ${abv.formatDecimal(1)}% vol (" + (if (en) "no safe consumption level — WHO/IARC Group 1 carcinogen" else "aucun seuil de consommation sûr — cancérigène IARC groupe 1 (OMS)") + ")", -9.0, Severity.CRITICAL) }
        abv > 1.2  -> { score -= 6;  deductions += Deduction("negative_nutrients", "$alcoholLabel ${abv.formatDecimal(1)}% vol (" + (if (en) "no safe consumption level — WHO/IARC Group 1 carcinogen" else "aucun seuil de consommation sûr — cancérigène IARC groupe 1 (OMS)") + ")", -6.0, Severity.CRITICAL) }
    }

    // Caffeine — EFSA sets 400mg/day as the safe upper limit for healthy
    // adults and flags single doses above ~200mg for cardiovascular/anxiety
    // effects, yet like alcohol this was only ever checked in
    // HealthConditionMetabolicAdjustments.kt (hypertension) and
    // HealthConditionGiAdjustments.kt (GI conditions) - a user with no
    // declared condition scanning a high-caffeine energy drink got zero base
    // penalty. Thresholds are per-100g/100ml: a standard energy drink
    // (~32mg/100ml, e.g. Red Bull) stays under the minor bar; concentrated
    // energy shots/certain "extra strength" drinks (150mg+/100ml) push past
    // EFSA's single-dose caution level.
    val caffeine = n.caffeineMg ?: 0.0
    val caffeineLabel = if (en) "Caffeine" else "Caféine"
    when {
        caffeine > 300.0 -> { score -= 8; deductions += Deduction("negative_nutrients", "$caffeineLabel ${caffeine.formatDecimal(1)}mg/100g (" + (if (en) "well above EFSA single-dose caution level" else "bien au-delà du seuil de prudence EFSA par prise") + ")", -8.0, Severity.CRITICAL) }
        caffeine > 150.0 -> { score -= 5; deductions += Deduction("negative_nutrients", "$caffeineLabel ${caffeine.formatDecimal(1)}mg/100g (" + (if (en) "above EFSA single-dose caution level (~200mg)" else "au-delà du seuil de prudence EFSA par prise (~200mg)") + ")", -5.0, Severity.MAJOR) }
        caffeine > 80.0  -> { score -= 3; deductions += Deduction("negative_nutrients", "$caffeineLabel ${caffeine.formatDecimal(1)}mg/100g (" + (if (en) "high caffeine content" else "teneur élevée en caféine") + ")", -3.0, Severity.MODERATE) }
        caffeine > 40.0  -> { score -= 1; deductions += Deduction("negative_nutrients", "$caffeineLabel ${caffeine.formatDecimal(1)}mg/100g (" + (if (en) "elevated caffeine content" else "teneur en caféine élevée") + ")", -1.0, Severity.MINOR) }
    }

    // Calorie density anomaly
    val (kcalLow, kcalHigh) = thresholds.expectedKcalRange
    if (n.energyKcal > kcalHigh * 1.25 || n.energyKcal < kcalLow * 0.5) {
        score -= 2
        deductions += Deduction("negative_nutrients", if (en) "Energy ${n.energyKcal}kcal/100g outside category norm ($kcalLow–$kcalHigh)" else "Énergie ${n.energyKcal}kcal/100g hors norme de la catégorie ($kcalLow–$kcalHigh)", -2.0, Severity.MINOR)
    }

    return PillarScore(if (en) "Negative Nutrients" else "Nutriments négatifs", MAX, maxOf(0.0, minOf(MAX.toDouble(), score)), deductions, bonuses)
}
