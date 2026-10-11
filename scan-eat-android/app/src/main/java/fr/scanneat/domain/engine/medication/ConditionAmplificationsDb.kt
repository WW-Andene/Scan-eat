package fr.scanneat.domain.engine.medication

// ============================================================================
// CONDITION AMPLIFICATIONS — split out of MedicationSubstanceDb.kt:
// condition-specific amplifications on top of the generic class caution
// (DrugClassCautionsDb) — closes the same PersonalScoreEngine-vs-hint-panel
// gap ProductHints closes for food (see HealthConditionGuidanceDb). These
// are the well-known, SPC-documented condition/class interactions (e.g. ACE
// inhibitors and sartans are formally contraindicated in pregnancy; NSAIDs
// can both raise blood pressure and blunt antihypertensive drugs) — not a
// general "ask your doctor" restatement.
// ============================================================================

internal val CONDITION_AMPLIFICATIONS: Map<Pair<DrugClass, String>, Pair<String, String>> = mapOf(
    (DrugClass.ACE_INHIBITOR to "pregnancy") to (
        "Votre profil indique une grossesse : les IEC sont formellement contre-indiqués pendant la grossesse (risque foetal documenté) — contactez votre médecin sans attendre." to
        "Your profile indicates pregnancy: ACE inhibitors are formally contraindicated during pregnancy (documented fetal risk) — contact your doctor promptly."),
    (DrugClass.SARTAN to "pregnancy") to (
        "Votre profil indique une grossesse : les sartans sont formellement contre-indiqués pendant la grossesse (risque foetal documenté) — contactez votre médecin sans attendre." to
        "Your profile indicates pregnancy: sartans are formally contraindicated during pregnancy (documented fetal risk) — contact your doctor promptly."),
    (DrugClass.NSAID to "pregnancy") to (
        "Votre profil indique une grossesse : les AINS sont déconseillés dès le début du 2e trimestre et contre-indiqués au 3e trimestre — demandez conseil à votre médecin ou pharmacien." to
        "Your profile indicates pregnancy: NSAIDs are discouraged from the start of the second trimester and contraindicated in the third — ask your doctor or pharmacist."),
    (DrugClass.NSAID to "hypertension") to (
        "Votre profil indique une hypertension : les AINS peuvent élever la tension artérielle et réduire l'efficacité de certains traitements antihypertenseurs." to
        "Your profile indicates hypertension: NSAIDs can raise blood pressure and reduce the effectiveness of some blood-pressure medications."),
    (DrugClass.METFORMIN to "kidney_disease") to (
        "Votre profil indique une maladie rénale : la posologie de la metformine doit être adaptée à la fonction rénale — assurez-vous que votre médecin en a connaissance." to
        "Your profile indicates kidney disease: metformin dosing must be adjusted to kidney function — make sure your doctor is aware."),
    (DrugClass.ACE_INHIBITOR to "kidney_disease") to (
        "Votre profil indique une maladie rénale : les IEC nécessitent une surveillance de la fonction rénale et du potassium sanguin." to
        "Your profile indicates kidney disease: ACE inhibitors require monitoring of kidney function and blood potassium."),
    (DrugClass.SARTAN to "kidney_disease") to (
        "Votre profil indique une maladie rénale : les sartans nécessitent une surveillance de la fonction rénale et du potassium sanguin." to
        "Your profile indicates kidney disease: sartans require monitoring of kidney function and blood potassium."),
    // FDA/EMA pharmacovigilance data links long-term opioid use with an increased
    // risk of new-onset or worsening depression — a documented class effect, not
    // an assumption from the opioid caution text alone.
    (DrugClass.OPIOID to "depression") to (
        "Votre profil indique une dépression : l'usage prolongé d'opioïdes est associé à un risque accru de dépression (données de pharmacovigilance FDA/EMA) — signalez ce traitement à votre médecin." to
        "Your profile indicates depression: long-term opioid use is associated with an increased risk of depression (FDA/EMA pharmacovigilance data) — disclose this treatment to your doctor."),
)
