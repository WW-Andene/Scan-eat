package fr.scanneat.domain.engine.medication

import fr.scanneat.domain.engine.nutrition.wordBoundaryMatch
import fr.scanneat.domain.engine.scoring.normalizeForMatching
import fr.scanneat.domain.model.Ingredient
import fr.scanneat.domain.model.Product

/**
 * User-requested: "can the app know the side effects for a mix of
 * medication + ingredients?" - it couldn't. `DrugClassCautionsDb.kt`'s
 * grapefruit/statin mention (and similar) was only ever static informational
 * text shown while viewing that medication's own detail sheet - never
 * actually checked against a food product the user scanned. This closes that
 * gap for real: a scanned/logged product's name+ingredients are matched
 * against the small set of well-established, high-confidence food-drug
 * interactions below, and only fires when the user's own active medications
 * (Medication.name, free-text, same keyword-matching limitation every other
 * medication-name lookup in this app already has - see MedicationViewModel's
 * own detectInteractions doc comment) actually match the relevant drug class.
 *
 * Deliberately narrow (3 interactions, not an exhaustive pharmacology
 * database this app has no legitimate data source for) - each one is a
 * textbook-level, broadly-cited interaction, not a judgment call. Every
 * caution ends by pointing to a pharmacist/doctor, same line
 * DrugClassCautionsDb's own header already draws between "flag a real,
 * known risk" and "give medical advice".
 */
private enum class FoodInteractionDrugClass { ANTICOAGULANT, MAOI, STATIN_OR_CCB }

private data class DrugKeywordGroup(val drugClass: FoodInteractionDrugClass, val keywords: List<String>)

private val DRUG_KEYWORD_GROUPS: List<DrugKeywordGroup> = listOf(
    DrugKeywordGroup(
        FoodInteractionDrugClass.ANTICOAGULANT,
        listOf("warfarine", "warfarin", "coumadine", "coumadin", "acenocoumarol", "rivaroxaban", "apixaban", "dabigatran", "heparine", "heparin"),
    ),
    DrugKeywordGroup(
        FoodInteractionDrugClass.MAOI,
        listOf("phenelzine", "tranylcypromine", "moclobemide", "selegiline"),
    ),
    DrugKeywordGroup(
        FoodInteractionDrugClass.STATIN_OR_CCB,
        listOf("atorvastatine", "rosuvastatine", "simvastatine", "amlodipine", "felodipine", "nifedipine"),
    ),
).map { it.copy(keywords = it.keywords.map(::normalizeForMatching)) }

private data class FoodCaution(
    val drugClass: FoodInteractionDrugClass,
    val foodKeywords: List<String>,
    val cautionFr: String,
    val cautionEn: String,
)

private val FOOD_CAUTIONS: List<FoodCaution> = listOf(
    FoodCaution(
        FoodInteractionDrugClass.ANTICOAGULANT,
        listOf("epinard", "brocoli", "chou", "persil", "kale", "choux de bruxelles"),
        "Riche en vitamine K : une variation brutale de votre consommation peut modifier l'efficacité de votre traitement anticoagulant — gardez un apport régulier plutôt que de l'éviter totalement, et demandez conseil à votre médecin/pharmacien.",
        "High in vitamin K: a sudden change in how much you eat of this can affect your anticoagulant's effectiveness — keep your intake consistent rather than avoiding it entirely, and ask your doctor/pharmacist for advice.",
    ),
    FoodCaution(
        FoodInteractionDrugClass.MAOI,
        listOf("fromage affine", "parmesan", "roquefort", "comte", "charcuterie", "salami", "chorizo", "choucroute", "soja fermente", "vin rouge"),
        "Riche en tyramine : associé à un IMAO (inhibiteur de la monoamine oxydase), ce type d'aliment peut déclencher une poussée tensionnelle dangereuse (crise hypertensive) — demandez conseil à votre médecin/pharmacien avant d'en consommer régulièrement.",
        "High in tyramine: combined with an MAOI (monoamine oxidase inhibitor), this type of food can trigger a dangerous blood-pressure spike (hypertensive crisis) — ask your doctor/pharmacist before eating it regularly.",
    ),
    FoodCaution(
        FoodInteractionDrugClass.STATIN_OR_CCB,
        listOf("pamplemousse", "grapefruit"),
        "Le pamplemousse peut augmenter la concentration sanguine de votre statine/inhibiteur calcique (interaction bien documentée), avec un risque accru d'effets indésirables — évitez de les associer sans avis médical.",
        "Grapefruit can raise your statin's/calcium channel blocker's blood concentration (a well-documented interaction), increasing the risk of side effects — avoid combining them without medical advice.",
    ),
).map { it.copy(foodKeywords = it.foodKeywords.map(::normalizeForMatching)) }

/**
 * Name+ingredients rather than a full [Product] - lets a call site that only
 * has a lighter-weight shape (e.g. a logged DiaryEntry, or a Pantry row) run
 * this check without constructing a throwaway Product just to satisfy the
 * signature. [Product]'s own convenience overload below delegates here.
 */
fun checkFoodDrugInteractions(name: String, ingredients: List<Ingredient>, activeMedicationNames: Set<String>, lang: String): List<String> {
    if (activeMedicationNames.isEmpty()) return emptyList()
    // app-audit §K2: word-boundary matching, not raw .contains() - the same
    // false-positive/negative risk IngredientMatcher.kt's own doc comment
    // documents ("mate" matching inside "tomate"), just never applied here.
    // A user-typed medication name or ingredient list is free text, not a
    // controlled vocabulary, so a short keyword could otherwise match inside
    // an unrelated word.
    val normalizedMedNames = activeMedicationNames.map(::normalizeForMatching)
    val activeDrugClasses = DRUG_KEYWORD_GROUPS
        .filter { group -> normalizedMedNames.any { medName -> group.keywords.any { wordBoundaryMatch(medName, it) } } }
        .map { it.drugClass }
        .toSet()
    if (activeDrugClasses.isEmpty()) return emptyList()
    val haystack = normalizeForMatching(name + " " + ingredients.joinToString(" ") { it.name })
    return FOOD_CAUTIONS
        .filter { it.drugClass in activeDrugClasses && it.foodKeywords.any { kw -> wordBoundaryMatch(haystack, kw) } }
        .map { if (lang == "en") it.cautionEn else it.cautionFr }
}

fun checkFoodDrugInteractions(product: Product, activeMedicationNames: Set<String>, lang: String): List<String> =
    checkFoodDrugInteractions(product.name, product.ingredients, activeMedicationNames, lang)
