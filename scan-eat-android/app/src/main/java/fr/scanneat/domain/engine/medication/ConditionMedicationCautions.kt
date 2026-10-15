package fr.scanneat.domain.engine.medication

import fr.scanneat.domain.engine.nutrition.wordBoundaryMatch
import fr.scanneat.domain.engine.scoring.normalizeForMatching

/** Drugs whose blood level rises when smoking stops: tobacco smoke induces
 *  CYP1A2, their main elimination route (SmPC of each, ANSM/EMA: dose review
 *  on smoking cessation). */
private val CYP1A2_TOBACCO_SENSITIVE = listOf(
    "clozapine", "leponex", "olanzapine", "zyprexa", "theophylline", "dilatrane", "theostat", "xanthium",
).map(::normalizeForMatching)

private val DISULFIRAM = listOf("disulfirame", "disulfiram", "esperal", "antabuse", "antabus").map(::normalizeForMatching)

/**
 * Medication cautions for the "quitting tobacco" / "quitting alcohol"
 * profile conditions. Never a dose: each one sends to the doctor or pharmacist.
 */
fun conditionMedicationCautions(conditions: Set<String>, activeMedicationNames: Collection<String>, lang: String): List<String> {
    val en = lang == "en"
    val meds = activeMedicationNames.map { it to normalizeForMatching(it) }
    val out = mutableListOf<String>()
    if ("tobacco_quit" in conditions) {
        meds.filter { (_, n) -> CYP1A2_TOBACCO_SENSITIVE.any { wordBoundaryMatch(n, it) } }.forEach { (name, _) ->
            out += if (en) "$name: stopping smoking raises its blood level (tobacco speeds up its elimination). Tell your doctor before or as soon as you stop: the dose may need adjusting. Don't change it yourself."
                else "$name : l'arrêt du tabac augmente son taux dans le sang (le tabac accélère son élimination). Prévenez votre médecin avant ou dès l'arrêt : la dose peut devoir être ajustée. Ne la modifiez pas vous-même."
        }
    }
    if ("alcohol_quit" in conditions) {
        out += if (en) "Some medicines contain alcohol (syrups, oral solutions, drops, mother tinctures): the leaflet says so. Ask your pharmacist for an alcohol-free alternative."
            else "Certains médicaments contiennent de l'alcool (sirops, solutions buvables, gouttes, teintures mères) : la notice l'indique. Demandez à votre pharmacien une alternative sans alcool."
        if (meds.any { (_, n) -> DISULFIRAM.any { wordBoundaryMatch(n, it) } }) {
            out += if (en) "Disulfiram: any alcohol, including in food, sauces, medicines, mouthwash or cosmetics, triggers a disulfiram reaction (SmPC)."
                else "Disulfirame (Espéral) : tout alcool, y compris dans les aliments, sauces, médicaments, bains de bouche ou cosmétiques, provoque une réaction antabuse (RCP)."
        }
        out += if (en) "Stopping alcohol after regular heavy drinking can cause a dangerous withdrawal (seizures, delirium): talk to a doctor before stopping. Alcool Info Service: 0 980 980 930 (free, 7 days a week)."
            else "Arrêter l'alcool après une consommation régulière importante peut provoquer un sevrage dangereux (convulsions, delirium) : parlez-en à un médecin avant d'arrêter. Alcool Info Service : 0 980 980 930 (gratuit, 7j/7)."
    }
    return out
}
