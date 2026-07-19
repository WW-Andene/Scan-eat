package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*

// ============================================================================
// SECTION 7: PILLAR 3 — NEGATIVE NUTRIENTS (max 25)
// ============================================================================

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
    val salt = n.saltG
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

    // Calorie density anomaly
    val (kcalLow, kcalHigh) = thresholds.expectedKcalRange
    if (n.energyKcal > kcalHigh * 1.25 || n.energyKcal < kcalLow * 0.5) {
        score -= 2
        deductions += Deduction("negative_nutrients", if (en) "Energy ${n.energyKcal}kcal/100g outside category norm ($kcalLow–$kcalHigh)" else "Énergie ${n.energyKcal}kcal/100g hors norme de la catégorie ($kcalLow–$kcalHigh)", -2.0, Severity.MINOR)
    }

    return PillarScore(if (en) "Negative Nutrients" else "Nutriments négatifs", MAX, maxOf(0.0, score), deductions, bonuses)
}
