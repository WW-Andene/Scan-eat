package fr.scanneat.domain.engine.nonconsumable

// ============================================================================
// NON-CONSUMABLE HINTS — category-level (not per-product) safety cautions
// and neutral facts, since OPF gives us name/brand/category only, not a
// verified per-product ingredient/hazard breakdown (see
// NonConsumableLookupDb's own header for why fabricating per-product
// toxicology data is out of scope here).
//
// Cautions are general hazard-class information already published for
// consumers by EU CLP Regulation (EC) No 1272/2008 classification and by
// national poison-control / safety agencies (ANSES, INRS, CDC) — e.g.
// "never mix bleach with ammonia-based products", not a treatment
// instruction for a specific ingestion/exposure. Facts are neutral
// chemistry/history, sourced the same Wikipedia-extract way as
// IngredientFactsDb.
// ============================================================================

data class NonConsumableHints(
    val facts: List<String>,
    val cautions: List<String>,
)

fun generateNonConsumableHints(category: NonConsumableCategory, lang: String): NonConsumableHints {
    val en = lang == "en"
    val facts = mutableListOf<String>()
    val cautions = mutableListOf<String>()

    when (category) {
        NonConsumableCategory.BLEACH -> {
            facts += if (en) "Household bleach is a dilute solution of sodium hypochlorite, first produced industrially in the late 18th century as a textile-bleaching agent."
                     else "L'eau de Javel domestique est une solution diluée d'hypochlorite de sodium, produite industriellement dès la fin du XVIIIe siècle comme agent de blanchiment textile."
            cautions += if (en) "Never mix bleach with ammonia-based products or acidic cleaners (incl. some toilet/descaling products) — the reaction releases toxic chloramine or chlorine gas (EU CLP hazard class: corrosive/irritant, oxidizing)."
                        else "Ne jamais mélanger l'eau de Javel avec un produit à base d'ammoniaque ou un nettoyant acide (y compris certains détartrants WC) — la réaction dégage des gaz toxiques (chloramines ou chlore). Classification EU CLP : corrosif/irritant, comburant."
            cautions += if (en) "Keep out of reach of children and away from food-storage areas; in case of ingestion or eye contact, contact emergency services or a poison control center immediately — do not rely on general guidance for what to do."
                        else "Tenir hors de portée des enfants et à l'écart des zones de stockage alimentaire ; en cas d'ingestion ou de contact oculaire, contactez immédiatement les services d'urgence ou un centre antipoison — ne vous fiez pas à une indication générale pour savoir quoi faire."
        }
        NonConsumableCategory.LAUNDRY -> {
            facts += if (en) "Liquid laundry detergent capsules/pods became widely available in the 2010s and are formulated to dissolve quickly in water, which is also why they're a recognized child-poisoning risk."
                     else "Les capsules de lessive liquide se sont largement répandues dans les années 2010 ; leur enveloppe est conçue pour se dissoudre rapidement dans l'eau, ce qui en fait aussi un risque reconnu d'intoxication accidentelle chez l'enfant."
            cautions += if (en) "Laundry detergents (especially concentrated pods) are a leading cause of accidental child poisoning in the EU — store sealed, out of sight and reach, never in a food/drink container."
                        else "Les lessives (en particulier les capsules concentrées) sont l'une des principales causes d'intoxication accidentelle chez l'enfant en Europe — conservez-les fermées, hors de vue et de portée, jamais dans un contenant alimentaire."
        }
        NonConsumableCategory.CLEANING_PRODUCT -> {
            facts += if (en) "Most multi-purpose household cleaners combine a surfactant with a mild acid or alkali — the specific hazard class depends on that formulation, printed on the product's own label."
                     else "La plupart des nettoyants multi-usages combinent un tensioactif avec un acide ou une base douce — la classe de danger précise dépend de cette formulation, indiquée sur l'étiquette du produit lui-même."
            cautions += if (en) "Never mix different cleaning products together, even from the same brand — read the label's own hazard pictograms (EU CLP), and ventilate the room while using any spray cleaner."
                        else "Ne mélangez jamais différents produits d'entretien entre eux, même de la même marque — lisez les pictogrammes de danger figurant sur l'étiquette (réglementation CLP) et aérez la pièce pendant l'utilisation d'un nettoyant en spray."
        }
        NonConsumableCategory.OTHER -> {
            // No category-specific data to add beyond the generic safety line below.
        }
    }

    if (category != NonConsumableCategory.OTHER) {
        cautions += if (en) "This is not a food product — refer to the label's own hazard pictograms and safety data sheet for complete handling information."
                    else "Ceci n'est pas un produit alimentaire — reportez-vous aux pictogrammes de danger et à la fiche de données de sécurité du produit pour des informations complètes."
    }

    return NonConsumableHints(facts, cautions)
}
