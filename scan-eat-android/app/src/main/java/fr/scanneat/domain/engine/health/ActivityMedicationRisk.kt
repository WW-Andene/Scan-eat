package fr.scanneat.domain.engine.health

import fr.scanneat.domain.engine.scoring.normalizeForMatching

/**
 * User-requested: the overtraining check (see [checkDailyOvertraining]) only
 * personalized on Profile.ageYears/healthConditions - a user on a
 * beta-blocker, anticoagulant, diuretic, or antidiabetic medication (logged
 * in the Medication tab, not necessarily reflected in healthConditions at
 * all) carries real exercise-relevant risks this app had zero awareness of.
 *
 * A saved [fr.scanneat.data.repository.health.Medication] only ever carries a
 * free-text `name` - no substance/class link back to any drug database (the
 * BDPM active-substance lookup only exists transiently at scan time via
 * barcode). Same free-text-keyword-matching approach
 * MedicationViewModel's own `INTERACTION_GROUPS` detector already uses for
 * the identical problem, kept independent of that presentation-layer file
 * (domain shouldn't depend on presentation) with its own keyword lists.
 */
enum class ActivityRelevantDrugClass { BETA_BLOCKER, ANTICOAGULANT, DIURETIC, ANTIDIABETIC }

private val DRUG_CLASS_KEYWORDS: Map<ActivityRelevantDrugClass, List<String>> = mapOf(
    // Blunts the heart-rate response exercise intensity is normally judged
    // by - a user can be working much harder than their pulse suggests.
    ActivityRelevantDrugClass.BETA_BLOCKER to listOf(
        "bisoprolol", "metoprolol", "métoprolol", "atenolol", "aténolol",
        "propranolol", "nebivolol", "nébivolol", "carvedilol",
    ),
    // Bleeding risk compounds with any impact/contact/fall risk.
    ActivityRelevantDrugClass.ANTICOAGULANT to listOf(
        "warfarine", "warfarin", "coumadine", "coumadin", "acenocoumarol",
        "rivaroxaban", "apixaban", "dabigatran", "héparine", "heparine", "heparin",
    ),
    // Fluid/electrolyte loss compounds with exercise's own sweat losses.
    ActivityRelevantDrugClass.DIURETIC to listOf(
        "hydrochlorothiazide", "indapamide", "furosémide", "furosemide",
        "spironolactone", "bumétanide", "bumetanide",
    ),
    // Insulin/sulfonylureas/metformin all raise hypoglycemia risk during and
    // after prolonged exertion - same real-world caution the diabetes
    // healthConditions branch already covers, but a logged medication can
    // apply even when that condition tag was never set.
    ActivityRelevantDrugClass.ANTIDIABETIC to listOf(
        "insuline", "insulin", "metformine", "metformin", "glibenclamide",
        "gliclazide", "glimépiride", "glimepiride", "répaglinide", "repaglinide",
    ),
).mapValues { (_, keywords) -> keywords.map { normalizeForMatching(it) } }

fun detectActivityRelevantDrugClasses(activeMedicationNames: List<String>): Set<ActivityRelevantDrugClass> {
    val normalized = activeMedicationNames.map { normalizeForMatching(it) }
    return DRUG_CLASS_KEYWORDS.filterValues { keywords -> normalized.any { name -> keywords.any { name.contains(it) } } }.keys
}
