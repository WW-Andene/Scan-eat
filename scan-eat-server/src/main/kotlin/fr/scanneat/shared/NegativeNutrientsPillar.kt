package fr.scanneat.shared

// ============================================================================
// SECTION 7: PILLAR 3 — NEGATIVE NUTRIENTS (max 25)
//
// Split out of ScoringEngine.kt - see CategoryThresholds.kt's header comment
// for why. Purely structural move, no behavior change.
// ============================================================================

/** Mirrors NegativeNutrientsPillar.kt on the Android project — shared with
 *  ScoringEngine.kt's high-proof veto so tuning one without the other can't
 *  silently desync the deduction from the veto it accompanies. */
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

    // Belt-and-suspenders by design, not double-counting by accident: this
    // tiered deduction and checkVeto's separate alcohol cap are not
    // independent penalties stacking on top of each other in practice - for a
    // clean-profile beer/wine, the veto's flat cap (54/45/40) binds regardless
    // of this deduction's finer -6/-9/-12 gradient. Kept anyway because it's
    // the only mechanism visible when the veto doesn't bind, and it keeps the
    // audit trail proportional to %vol. Same layering applies to trans fat and
    // nitrites in ScoringEngine.kt's checkVeto. Mirrors the identical
    // clarifying comment on the Android side (see Scoring Drift Check).
    val abv = n.alcoholPercentVol ?: 0.0
    val alcoholLabel = if (en) "Alcohol" else "Alcool"
    when {
        abv > HIGH_ABV_THRESHOLD -> { score -= 12; deductions += Deduction("negative_nutrients", "$alcoholLabel ${abv.formatDecimal(1)}% vol (" + (if (en) "no safe consumption level — WHO/IARC Group 1 carcinogen" else "aucun seuil de consommation sûr — cancérigène IARC groupe 1 (OMS)") + ")", -12.0, Severity.CRITICAL) }
        abv > 5.0  -> { score -= 9;  deductions += Deduction("negative_nutrients", "$alcoholLabel ${abv.formatDecimal(1)}% vol (" + (if (en) "no safe consumption level — WHO/IARC Group 1 carcinogen" else "aucun seuil de consommation sûr — cancérigène IARC groupe 1 (OMS)") + ")", -9.0, Severity.CRITICAL) }
        abv > 1.2  -> { score -= 6;  deductions += Deduction("negative_nutrients", "$alcoholLabel ${abv.formatDecimal(1)}% vol (" + (if (en) "no safe consumption level — WHO/IARC Group 1 carcinogen" else "aucun seuil de consommation sûr — cancérigène IARC groupe 1 (OMS)") + ")", -6.0, Severity.CRITICAL) }
    }

    val caffeine = n.caffeineMg ?: 0.0
    val caffeineLabel = if (en) "Caffeine" else "Caféine"
    when {
        caffeine > 300.0 -> { score -= 8; deductions += Deduction("negative_nutrients", "$caffeineLabel ${caffeine.formatDecimal(1)}mg/100g (" + (if (en) "concentrated enough that a typical single serving would exceed EFSA's ~200mg single-dose guidance" else "concentration telle qu'une portion normale dépasserait le repère de prudence EFSA d'environ 200mg par prise") + ")", -8.0, Severity.CRITICAL) }
        caffeine > 150.0 -> { score -= 5; deductions += Deduction("negative_nutrients", "$caffeineLabel ${caffeine.formatDecimal(1)}mg/100g (" + (if (en) "concentrated enough that a generous serving could approach EFSA's ~200mg single-dose guidance" else "concentration telle qu'une portion généreuse pourrait approcher le repère de prudence EFSA d'environ 200mg par prise") + ")", -5.0, Severity.MAJOR) }
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
