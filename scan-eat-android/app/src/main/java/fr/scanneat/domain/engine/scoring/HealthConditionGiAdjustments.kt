package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.Product

// IBS, Crohn's/IBD and chronic diarrhea all share a real, sourced trigger:
// high-fat meals slow gastric emptying and provoke the gastrocolic reflex
// (Monash University IBS patient guidance; Crohn's & Colitis Foundation;
// NHS/Mayo Clinic diarrhea-diet advice). 17.5g/100g is the UK Food
// Standards Agency's own public "high fat" traffic-light threshold, not a
// guess - reused here across all three conditions rather than inventing a
// condition-specific number with no comparable public sourcing.
internal fun checkGastrointestinalConditions(
    product: Product,
    conditions: Set<String>,
    lang: String,
    alcoholHit: Boolean,
    adjustments: MutableList<PersonalAdjustment>,
) {
    val highFat = product.nutrition.fatG >= 17.5
    val polyolHit = product.ingredients.any { ing -> POLYOL_PATTERN.containsMatchIn(normalizeForMatching(ing.name)) }
    if ("ibs" in conditions) {
        val fodmapHit = product.ingredients.any { ing -> HIGH_FODMAP_PATTERN.containsMatchIn(normalizeForMatching(ing.name)) }
        if (fodmapHit) {
            adjustments += PersonalAdjustment(
                points = -3.0,
                reason = if (lang == "en") "Contains a high-FODMAP ingredient (onion/garlic/wheat/legumes) — a common IBS symptom trigger per Monash University's Low FODMAP research"
                         else "Contient un ingrédient riche en FODMAP (oignon/ail/blé/légumineuses) — un déclencheur fréquent de symptômes du SII selon les travaux de l'université Monash sur le régime pauvre en FODMAP",
                category = AdjustmentCategory.CONDITION,
            )
        }
        if (polyolHit) {
            adjustments += PersonalAdjustment(
                points = -3.0,
                reason = if (lang == "en") "Contains a sugar alcohol (sorbitol/mannitol/xylitol...) — poorly absorbed and osmotically active, a known IBS trigger (Monash Low FODMAP)"
                         else "Contient un polyol (sorbitol/mannitol/xylitol...) — mal absorbé et osmotiquement actif, un déclencheur connu du SII (régime pauvre en FODMAP, université Monash)",
                category = AdjustmentCategory.CONDITION,
            )
        }
        if (highFat) {
            adjustments += PersonalAdjustment(
                points = -2.0,
                reason = if (lang == "en") "High fat (${product.nutrition.fatG} g/100 g) — fatty meals can trigger IBS symptoms via the gastrocolic reflex"
                         else "Riche en matières grasses (${product.nutrition.fatG} g/100 g) — les repas gras peuvent déclencher des symptômes du SII via le réflexe gastro-colique",
                category = AdjustmentCategory.CONDITION,
            )
        }
        if (!fodmapHit && !polyolHit && !highFat) {
            adjustments += PersonalAdjustment(
                points = 2.0,
                reason = if (lang == "en") "No common high-FODMAP trigger detected — IBS-friendly"
                         else "Aucun déclencheur riche en FODMAP détecté — adapté au SII",
                category = AdjustmentCategory.CONDITION,
            )
        }
    }
    // EU "high fibre" nutrition-claim threshold (Regulation (EC) 1924/2006:
    // ≥6g/100g) reused here, not invented - Crohn's & Colitis Foundation/NHS
    // both recommend a low-residue (low-fiber) diet during a flare, since
    // insoluble fiber is mechanically harder to pass through an inflamed gut.
    if ("crohn_ibd" in conditions) {
        val highFiber = product.nutrition.fiberG >= 6.0
        if (highFiber) {
            adjustments += PersonalAdjustment(
                points = -2.0,
                reason = if (lang == "en") "High fiber (${product.nutrition.fiberG} g/100 g) — a low-residue diet is commonly advised during a Crohn's/IBD flare (Crohn's & Colitis Foundation, NHS)"
                         else "Riche en fibres (${product.nutrition.fiberG} g/100 g) — un régime pauvre en résidus est généralement conseillé lors d'une poussée de Crohn/MICI (Crohn's & Colitis Foundation, NHS)",
                category = AdjustmentCategory.CONDITION,
            )
        }
        if (highFat) {
            adjustments += PersonalAdjustment(
                points = -2.0,
                reason = if (lang == "en") "High fat (${product.nutrition.fatG} g/100 g) — can worsen symptoms during a Crohn's/IBD flare (Crohn's & Colitis Foundation)"
                         else "Riche en matières grasses (${product.nutrition.fatG} g/100 g) — peut aggraver les symptômes lors d'une poussée de Crohn/MICI (Crohn's & Colitis Foundation)",
                category = AdjustmentCategory.CONDITION,
            )
        }
        if (alcoholHit) {
            adjustments += PersonalAdjustment(
                points = -2.0,
                reason = if (lang == "en") "Contains alcohol — commonly advised against during a Crohn's/IBD flare (Crohn's & Colitis Foundation)"
                         else "Contient de l'alcool — généralement déconseillé lors d'une poussée de Crohn/MICI (Crohn's & Colitis Foundation)",
                category = AdjustmentCategory.CONDITION,
            )
        }
        if (!highFiber && !highFat && !alcoholHit) {
            adjustments += PersonalAdjustment(
                points = 2.0,
                reason = if (lang == "en") "Low fiber and low fat — compatible with a low-residue diet"
                         else "Pauvre en fibres et en matières grasses — compatible avec un régime pauvre en résidus",
                category = AdjustmentCategory.CONDITION,
            )
        }
    }
    // NHS/Mayo Clinic chronic-diarrhea dietary advice: limit sugar alcohols
    // (osmotic effect, the same EU-labeled mechanism as the IBS polyol check
    // above), caffeine and alcohol (both gut stimulants/motility accelerants),
    // and fatty/fried food.
    if ("chronic_diarrhea" in conditions) {
        if (polyolHit) {
            adjustments += PersonalAdjustment(
                points = -4.0,
                reason = if (lang == "en") "Contains a sugar alcohol (sorbitol/mannitol/xylitol...) — osmotically draws water into the bowel and can worsen diarrhea (EU-mandated laxative-effect labeling above 10 g/100 g; NHS/Mayo Clinic diarrhea-diet guidance)"
                         else "Contient un polyol (sorbitol/mannitol/xylitol...) — attire l'eau dans l'intestin par effet osmotique et peut aggraver la diarrhée (étiquetage \"effet laxatif\" obligatoire en UE au-delà de 10 g/100 g ; recommandations NHS/Mayo Clinic)",
                category = AdjustmentCategory.CONDITION,
            )
        }
        product.nutrition.caffeineMg?.let { caffeine ->
            if (caffeine >= 20.0) {
                adjustments += PersonalAdjustment(
                    points = -2.0,
                    reason = if (lang == "en") "Contains caffeine (${caffeine.toInt()} mg/100 g) — a gut stimulant that can worsen diarrhea (NHS diarrhea-diet guidance)"
                             else "Contient de la caféine (${caffeine.toInt()} mg/100 g) — un stimulant intestinal pouvant aggraver la diarrhée (recommandations NHS)",
                    category = AdjustmentCategory.CONDITION,
                )
            }
        }
        if (alcoholHit) {
            adjustments += PersonalAdjustment(
                points = -2.0,
                reason = if (lang == "en") "Contains alcohol — can worsen diarrhea (NHS diarrhea-diet guidance)"
                         else "Contient de l'alcool — peut aggraver la diarrhée (recommandations NHS)",
                category = AdjustmentCategory.CONDITION,
            )
        }
        if (highFat) {
            adjustments += PersonalAdjustment(
                points = -2.0,
                reason = if (lang == "en") "High fat (${product.nutrition.fatG} g/100 g) — fatty/fried food can worsen diarrhea (NHS/Mayo Clinic diarrhea-diet guidance)"
                         else "Riche en matières grasses (${product.nutrition.fatG} g/100 g) — les aliments gras/frits peuvent aggraver la diarrhée (recommandations NHS/Mayo Clinic)",
                category = AdjustmentCategory.CONDITION,
            )
        }
        if (!polyolHit && !highFat && (product.nutrition.caffeineMg ?: 0.0) < 20.0 && !alcoholHit) {
            adjustments += PersonalAdjustment(
                points = 2.0,
                reason = if (lang == "en") "No common diarrhea trigger detected"
                         else "Aucun déclencheur habituel de diarrhée détecté",
                category = AdjustmentCategory.CONDITION,
            )
        }
    }
}
