package fr.scanneat.domain.engine.medication

import fr.scanneat.domain.engine.nutrition.matchNameDictionary

// ============================================================================
// MEDICATION SUBSTANCE DB — same table-plus-substring-match pattern used
// throughout domain.engine.nutrition (AdditivesDb / NamedSubstanceDb /
// IngredientFactsDb), applied here to a medication's active substance(s)
// (MedicationDbEntry.activeSubstances, itself real BDPM CIS_COMPO_bdpm.txt
// data — see MedicationLookupDb).
//
// Split (same package) into three data tables plus this orchestration file:
//  - DrugFactsDb.kt: DRUG_FACTS, one neutral, Wikipedia-sourced fact per
//    substance (fr/en article's own opening sentence, same bulk-fetch method
//    as IngredientFactsDb — fetched 2026-07-13, re-verifiable against the
//    live article).
//  - DrugClassCautionsDb.kt: DrugClass enum + CLASS_CAUTIONS, general,
//    well-established drug-class-level safety information (interactions/
//    side-effect classes documented in every EU Summary of Product
//    Characteristics for that class — e.g. "NSAIDs carry a GI bleeding
//    risk"), grouped by pharmacological class rather than repeated per
//    substance. This is deliberately NOT dosage, diagnosis, or treatment
//    advice, and every caution ends by pointing back to a
//    pharmacist/doctor rather than telling the user what to do — the same
//    line MedicationLookupDb's own header already draws between "identify
//    what was scanned" and "give medical advice".
//  - ConditionAmplificationsDb.kt: CONDITION_AMPLIFICATIONS, condition-
//    specific amplifications on top of the generic class caution above.
//
// Covers the ~35 most common active substances by presentation count in
// the commercialized BDPM dataset (see the CIS_COMPO frequency count used
// to build this list) — not exhaustive, since a wrong drug-safety note is
// far worse than a missing one.
// ============================================================================

data class MedicationHints(
    val facts: List<String>,
    val cautions: List<String>,
)

/**
 * Build hints for a scanned medication from its BDPM active substance(s)
 * and dispensing condition — the same "trace back to a concrete sourced
 * field" discipline as ProductHints, applied to MedicationDbEntry. Also
 * cross-references [healthConditions] (Profile.healthConditions) the same
 * way PersonalScoreEngine already does for the numeric score.
 */
fun generateMedicationHints(entry: MedicationDbEntry, healthConditions: Set<String>, lang: String): MedicationHints {
    val en = lang == "en"
    val facts = mutableListOf<String>()
    val cautions = mutableListOf<String>()

    matchNameDictionary(entry.activeSubstances, DRUG_FACTS, DrugFact::keys)
        .forEach { facts += if (en) it.factEn else it.factFr }

    val matchedClasses = matchNameDictionary(entry.activeSubstances, CLASS_CAUTIONS, ClassCaution::keys)
    matchedClasses.forEach { cautions += if (en) it.cautionEn else it.cautionFr }

    for (caution in matchedClasses) {
        for (condition in healthConditions) {
            CONDITION_AMPLIFICATIONS[caution.drugClass to condition]?.let { (frText, enText) ->
                cautions += if (en) enText else frText
            }
        }
    }

    // BDPM's own dispensing-condition field (CIS_CPD_bdpm.txt) — a real
    // sourced fact, not an inference: an empty list means BDPM recorded no
    // dispensing restriction for this presentation (i.e. sold over the
    // counter); a non-empty list is quoted as-is from the source.
    if (entry.dispensingConditions.isNotEmpty()) {
        val conditions = entry.dispensingConditions.joinToString(", ")
        cautions += if (en) "Dispensing condition (per BDPM): $conditions"
                    else "Condition de délivrance (source BDPM) : $conditions"
    } else {
        facts += if (en) "No dispensing restriction recorded in BDPM (sold over the counter)"
                 else "Aucune restriction de délivrance enregistrée dans la BDPM (vente libre)"
    }

    return MedicationHints(facts, cautions)
}
