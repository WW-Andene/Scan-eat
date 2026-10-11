package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.Product
import fr.scanneat.domain.model.ProductCategory

// ============================================================================
// WATER MINERAL PROFILE — user-requested: "expertise" on water the same way
// the app already has on food, i.e. "is this brand good, does it have
// minerals". Bottled/mineral water almost never trips the generic "high in
// calcium/magnesium" benefit thresholds in ProductHintsBenefitsRisks.kt (those
// are calibrated for solid food - 240mg/100g calcium - while even a
// calcium-rich mineral water like Hépar sits around 55mg/100mL), so water's
// real mineral content was silently invisible in the hint panel before this.
//
// AUTHORITATIVE: EU Directive 2009/54/EC Annex III (natural mineral water
// labelling descriptors) - the exact per-litre thresholds a French/EU bottled
// water label is legally allowed to print ("faible teneur en sodium",
// "contient du calcium", etc.), converted to per-100mL here to match
// NutritionPer100g's convention (bottled water's "100g" and "100mL" are the
// same thing - water's density is 1g/mL).
// ============================================================================

internal fun appendWaterMineralHints(product: Product, lang: String, benefits: MutableList<String>, facts: MutableList<String>) {
    if (product.category != ProductCategory.BEVERAGE_WATER) return
    val en = lang == "en"
    val n = product.nutrition

    // Annex III: "low sodium content" ≤20mg/L; "suitable for a low-sodium
    // diet" ≤20mg/L (same threshold, doctors' reference for hypertension/
    // kidney/heart-failure patients specifically watching sodium).
    n.sodiumMg?.let {
        if (it <= 2.0) benefits += if (en) "Low sodium content (≤20 mg/L) — suitable for a low-sodium diet (EU Directive 2009/54/EC labelling threshold)"
                                    else "Faible teneur en sodium (≤20 mg/L) — convient à un régime pauvre en sodium (seuil d'étiquetage, directive 2009/54/CE)"
        else if (it >= 20.0) facts += if (en) "Contains sodium (${(it * 10).toInt()} mg/L)"
                                       else "Contient du sodium (${(it * 10).toInt()} mg/L)"
    }
    // Annex III: "contains calcium" >150mg/L.
    n.calciumMg?.let {
        if (it >= 15.0) benefits += if (en) "Contains calcium (>150 mg/L) — EU natural mineral water labelling threshold"
                                     else "Contient du calcium (>150 mg/L) — seuil d'étiquetage des eaux minérales naturelles (directive 2009/54/CE)"
    }
    // Annex III: "contains magnesium" >50mg/L.
    n.magnesiumMg?.let {
        if (it >= 5.0) benefits += if (en) "Contains magnesium (>50 mg/L) — EU natural mineral water labelling threshold"
                                    else "Contient du magnésium (>50 mg/L) — seuil d'étiquetage des eaux minérales naturelles (directive 2009/54/CE)"
    }
    n.potassiumMg?.let {
        if (it >= 1.0) facts += if (en) "Contains potassium (${(it * 10).toInt()} mg/L)"
                                 else "Contient du potassium (${(it * 10).toInt()} mg/L)"
    }
    // No dry-residue ("résidu sec"), bicarbonate, or fluoride field exists on
    // NutritionPer100g/OFF's mapped fields - the overall "weakly/strongly
    // mineralized" classification a real label carries (<500 / 500-1500 /
    // >1500 mg/L dry residue) deliberately isn't fabricated from an
    // incomplete proxy sum of the minerals above; only claims backed by an
    // actual declared field are made here.
}
