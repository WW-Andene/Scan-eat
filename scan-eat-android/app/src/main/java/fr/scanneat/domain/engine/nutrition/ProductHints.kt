package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.model.NovaClass
import fr.scanneat.domain.model.Product

// ============================================================================
// PRODUCT HINTS — the "💡" info panel's content. Deliberately rule-based off
// data already on the Product (NOVA class, nutrition thresholds, organic/
// fermented flags), not fabricated medical claims: every hint traces back to
// a concrete field, so there is no line here that isn't backed by the
// product's own declared data.
// ============================================================================

data class ProductHints(
    val benefits: List<String>,
    val risks: List<String>,
    val facts: List<String>,
)

fun generateProductHints(product: Product, lang: String): ProductHints {
    val en = lang == "en"
    val n = product.nutrition
    val benefits = mutableListOf<String>()
    val risks = mutableListOf<String>()
    val facts = mutableListOf<String>()

    // ---- Benefits ----
    if (n.fiberG >= 6.0) benefits += if (en) "High in fiber (${n.fiberG} g/100 g) — supports digestion" else "Riche en fibres (${n.fiberG} g/100 g) — favorise la digestion"
    if (n.proteinG >= 12.0) benefits += if (en) "Good source of protein (${n.proteinG} g/100 g)" else "Bonne source de protéines (${n.proteinG} g/100 g)"
    if (n.saltG <= 0.3) benefits += if (en) "Low salt (${n.saltG} g/100 g)" else "Faible en sel (${n.saltG} g/100 g)"
    if (n.sugarsG <= 5.0) benefits += if (en) "Low sugar (${n.sugarsG} g/100 g)" else "Faible en sucres (${n.sugarsG} g/100 g)"
    if (product.organic) benefits += if (en) "Certified organic" else "Certifié biologique"
    if (product.fermented) benefits += if (en) "Fermented — may support gut health" else "Fermenté — peut favoriser la santé intestinale"
    if (product.declaredMicronutrients.isNotEmpty()) {
        benefits += if (en) "Declared micronutrients: ${product.declaredMicronutrients.joinToString(", ")}"
                    else "Micronutriments déclarés : ${product.declaredMicronutrients.joinToString(", ")}"
    }

    // ---- Risks ----
    if (n.saturatedFatG >= 5.0) risks += if (en) "High in saturated fat (${n.saturatedFatG} g/100 g)" else "Riche en graisses saturées (${n.saturatedFatG} g/100 g)"
    if (n.sugarsG >= 15.0) risks += if (en) "High in sugar (${n.sugarsG} g/100 g)" else "Riche en sucres (${n.sugarsG} g/100 g)"
    if (n.saltG >= 1.2) risks += if (en) "High in salt (${n.saltG} g/100 g)" else "Riche en sel (${n.saltG} g/100 g)"
    if (product.novaClass == NovaClass.ULTRA_PROCESSED) {
        risks += if (en) "Ultra-processed (NOVA 4) — associated with higher long-term health risk in observational studies"
                 else "Ultra-transformé (NOVA 4) — associé à un risque accru sur la santé à long terme dans les études observationnelles"
    }
    if (product.hasHealthClaims) {
        risks += if (en) "Carries marketing health claims — verify against the actual nutrition values above"
                 else "Porte des allégations santé marketing — à vérifier au regard des valeurs nutritionnelles ci-dessus"
    }

    // ---- Facts ----
    facts += if (en) "NOVA processing class: ${product.novaClass.value}/4" else "Classe de transformation NOVA : ${product.novaClass.value}/4"
    facts += if (en) "Energy: ${n.energyKcal} kcal/100 g" else "Énergie : ${n.energyKcal} kcal/100 g"
    if (product.origin != null) facts += if (en) "Origin: ${product.origin}" else "Origine : ${product.origin}"

    return ProductHints(benefits, risks, facts)
}
