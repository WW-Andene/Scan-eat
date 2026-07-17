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
        NonConsumableCategory.PERSONAL_CARE -> {
            facts += if (en) "Cosmetic and personal-care products (soap, shampoo, toothpaste, deodorant, ...) are regulated in the EU under Regulation (EC) No 1223/2009, distinct from food safety law — they're assessed for skin/topical use, not ingestion."
                     else "Les produits cosmétiques et d'hygiène (savon, shampoing, dentifrice, déodorant, ...) sont encadrés dans l'UE par le règlement (CE) n° 1223/2009, distinct de la réglementation alimentaire — ils sont évalués pour un usage cutané/topique, pas pour l'ingestion."
            cautions += if (en) "Accidental ingestion (especially by young children) can cause nausea or GI irritation depending on the product — contact a poison control center if it happens, and keep these products out of children's reach."
                        else "Une ingestion accidentelle (en particulier chez le jeune enfant) peut provoquer nausées ou irritations digestives selon le produit — contactez un centre antipoison si cela se produit, et tenez ces produits hors de portée des enfants."
        }
        NonConsumableCategory.HOUSEHOLD_CHEMICAL -> {
            facts += if (en) "Household chemicals (paints, solvents, degreasers, and similar) are classified for hazard under the same EU CLP Regulation (EC) No 1272/2008 as cleaning products, with the specific class printed on the product's own label."
                     else "Les produits chimiques ménagers (peintures, solvants, dégraissants, etc.) sont classés selon la même réglementation CLP (CE) n° 1272/2008 que les produits d'entretien, la classe précise figurant sur l'étiquette du produit."
            cautions += if (en) "Store in the original, labeled container, away from food-storage areas, and ventilate the room while using any spray or solvent-based product."
                        else "Conservez dans l'emballage d'origine étiqueté, à l'écart des zones de stockage alimentaire, et aérez la pièce pendant l'utilisation d'un produit en spray ou à base de solvant."
        }
        NonConsumableCategory.TOBACCO -> {
            facts += if (en) "Nicotine, tobacco's principal psychoactive compound, is classified by WHO as one of the most addictive substances in common use."
                     else "La nicotine, principal composé psychoactif du tabac, est classée par l'OMS parmi les substances les plus addictives en usage courant."
            cautions += if (en) "Ingested tobacco or e-cigarette liquid is a recognized acute poisoning risk, especially in children and pets — even a small amount can cause nicotine poisoning. Keep out of reach and seek emergency care immediately if ingestion is suspected."
                        else "L'ingestion de tabac ou de liquide de cigarette électronique est un risque reconnu d'intoxication aiguë, en particulier chez l'enfant et l'animal domestique — même une petite quantité peut provoquer une intoxication à la nicotine. Tenir hors de portée et consulter en urgence en cas d'ingestion suspectée."
        }
        NonConsumableCategory.BATTERY -> {
            facts += if (en) "Button/coin cell batteries are a well-documented pediatric emergency: if swallowed, they can generate an electrical current in moist tissue that causes severe internal chemical burns within hours."
                     else "Les piles bouton sont une cause bien documentée d'urgence pédiatrique : en cas d'ingestion, elles peuvent générer un courant électrique au contact des tissus humides et provoquer de graves brûlures chimiques internes en quelques heures."
            cautions += if (en) "If ingestion of any battery — especially a button/coin cell — is suspected, treat it as a medical emergency and seek care immediately; do not wait for symptoms to appear."
                        else "En cas d'ingestion suspectée d'une pile, en particulier une pile bouton, considérez-la comme une urgence médicale et consultez immédiatement — n'attendez pas l'apparition de symptômes."
        }
        NonConsumableCategory.HYGIENE_PRODUCT -> {
            facts += if (en) "Absorbent hygiene products (diapers, feminine sanitary products) are engineered to absorb and retain liquid, using superabsorbent polymers not intended for ingestion."
                     else "Les produits d'hygiène absorbants (couches, protections féminines) sont conçus pour absorber et retenir le liquide grâce à des polymères superabsorbants, non destinés à être ingérés."
            cautions += if (en) "Keep away from infants and pets — a swallowed piece can pose a choking or intestinal blockage risk."
                        else "Tenir à l'écart des nourrissons et des animaux domestiques — un morceau avalé peut présenter un risque d'étouffement ou d'occlusion intestinale."
        }
        NonConsumableCategory.PET_SUPPLY -> {
            facts += if (en) "Pet food and care products are formulated for a given species' specific nutritional needs and regulatory standards, not for human consumption."
                     else "Les aliments et produits pour animaux sont formulés selon les besoins nutritionnels et normes réglementaires propres à l'espèce concernée, pas pour la consommation humaine."
            cautions += if (en) "Not suitable for human consumption — some ingredients, additive levels, or fortification are calibrated for the target animal species, not for people."
                        else "Non adapté à la consommation humaine — certains ingrédients, niveaux d'additifs ou enrichissements sont calibrés pour l'espèce animale visée, pas pour l'humain."
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
