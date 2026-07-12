package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*

// ============================================================================
// SECTION 7: PILLAR 3 — NEGATIVE NUTRIENTS (max 25)
// ============================================================================

fun scoreNegativeNutrients(product: Product): PillarScore {
    val MAX = 25
    val deductions = mutableListOf<Deduction>()
    val bonuses = mutableListOf<Deduction>()
    val n = product.nutrition
    val thresholds = getThresholds(product.category)
    var score = MAX.toDouble()

    // Saturated fat
    val sat = n.saturatedFatG
    val (satMod, satMaj, satCrit) = thresholds.satFatThresholds
    when {
        sat > satCrit -> { score -= 9; deductions += Deduction("negative_nutrients", "Saturated fat ${sat}g/100g (>$satCrit critical)", -9.0, Severity.CRITICAL) }
        sat > satMaj  -> { score -= 6; deductions += Deduction("negative_nutrients", "Saturated fat ${sat}g/100g (>$satMaj major)", -6.0, Severity.MAJOR) }
        sat > satMod  -> { score -= 3; deductions += Deduction("negative_nutrients", "Saturated fat ${sat}g/100g (>$satMod moderate)", -3.0, Severity.MODERATE) }
    }

    // Sugars
    val sugars = n.addedSugarsG ?: n.sugarsG
    val sugarLabel = if (n.addedSugarsG != null) "Added sugars" else "Total sugars (added not declared)"
    val (sMinor, sMod, sMaj, sCrit) = thresholds.sugarThresholds
    when {
        sugars > sCrit -> { score -= 12; deductions += Deduction("negative_nutrients", "$sugarLabel ${sugars}g/100g (>$sCrit critical)", -12.0, Severity.CRITICAL) }
        sugars > sMaj  -> { score -= 9;  deductions += Deduction("negative_nutrients", "$sugarLabel ${sugars}g/100g (>$sMaj major)", -9.0, Severity.MAJOR) }
        sugars > sMod  -> { score -= 6;  deductions += Deduction("negative_nutrients", "$sugarLabel ${sugars}g/100g (>$sMod moderate)", -6.0, Severity.MODERATE) }
        sugars > sMinor -> { score -= 3; deductions += Deduction("negative_nutrients", "$sugarLabel ${sugars}g/100g (>$sMinor minor)", -3.0, Severity.MINOR) }
    }

    // Salt
    val salt = n.saltG
    when {
        salt > 1.5  -> { score -= 6; deductions += Deduction("negative_nutrients", "Salt ${salt}g/100g (>1.5g major)", -6.0, Severity.MAJOR) }
        salt > 1.25 -> { score -= 4; deductions += Deduction("negative_nutrients", "Salt ${salt}g/100g (>1.25g moderate)", -4.0, Severity.MODERATE) }
        salt > 0.75 -> { score -= 2; deductions += Deduction("negative_nutrients", "Salt ${salt}g/100g (>0.75g minor)", -2.0, Severity.MINOR) }
    }

    // Trans fat
    val trans = n.transFatG ?: 0.0
    if (trans > 0.1) {
        score -= 10
        deductions += Deduction("negative_nutrients", "Trans fat present: ${trans}g/100g (no safe level)", -10.0, Severity.CRITICAL)
    }

    // Calorie density anomaly
    val (kcalLow, kcalHigh) = thresholds.expectedKcalRange
    if (n.energyKcal > kcalHigh * 1.25 || n.energyKcal < kcalLow * 0.5) {
        score -= 2
        deductions += Deduction("negative_nutrients", "Energy ${n.energyKcal}kcal/100g outside category norm ($kcalLow–$kcalHigh)", -2.0, Severity.MINOR)
    }

    return PillarScore("Negative Nutrients", MAX, maxOf(0.0, score), deductions, bonuses)
}
