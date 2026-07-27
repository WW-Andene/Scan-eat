package fr.scanneat.domain.engine.medication

// ============================================================================
// DRUG CLASS CAUTIONS — split out of MedicationSubstanceDb.kt: general,
// well-established drug-class-level safety information (interactions/
// side-effect classes documented in every EU Summary of Product
// Characteristics for that class — e.g. "NSAIDs carry a GI bleeding risk"),
// grouped by pharmacological class rather than repeated per substance. This
// is deliberately NOT dosage, diagnosis, or treatment advice, and every
// caution ends by pointing back to a pharmacist/doctor rather than telling
// the user what to do — the same line MedicationLookupDb's own header
// already draws between "identify what was scanned" and "give medical
// advice".
// ============================================================================

internal enum class DrugClass { NSAID, STATIN, SARTAN, OPIOID, ACE_INHIBITOR, SSRI, ANTIPSYCHOTIC,
    GABAPENTINOID, PPI, PDE5_INHIBITOR, PARACETAMOL, METFORMIN, LEVOTHYROXINE, ANTICOAGULANT,
    BETA_BLOCKER, DIURETIC, CALCIUM_CHANNEL_BLOCKER, INHALED_CORTICOSTEROID, VITAMIN_D, PENICILLIN }

internal data class ClassCaution(
    val drugClass: DrugClass,
    val keys: List<String>,
    val cautionFr: String,
    val cautionEn: String,
)

internal val CLASS_CAUTIONS: List<ClassCaution> = listOf(
    ClassCaution(DrugClass.NSAID, listOf("ibuprofene"),
        "AINS (anti-inflammatoire non stéroïdien) : risque digestif (ulcère, saignement) en usage prolongé, déconseillé au 3e trimestre de grossesse — demandez conseil à votre pharmacien en cas de traitement au long cours.",
        "NSAID (non-steroidal anti-inflammatory drug): carries a gastrointestinal risk (ulcer, bleeding) with prolonged use, and is not recommended in the third trimester of pregnancy — ask your pharmacist about long-term use."),
    ClassCaution(DrugClass.STATIN, listOf("atorvastatine", "rosuvastatine", "simvastatine"),
        "Statine : peut provoquer des douleurs musculaires ; le jus de pamplemousse peut augmenter sa concentration dans le sang (interaction bien documentée, notamment avec la simvastatine).",
        "Statin: can cause muscle pain; grapefruit juice can raise its blood concentration (a well-documented interaction, particularly with simvastatin)."),
    ClassCaution(DrugClass.SARTAN, listOf("valsartan", "candesartan", "irbesartan", "losartan", "telmisartan"),
        "Sartan (antagoniste des récepteurs de l'angiotensine II) : contre-indiqué pendant la grossesse, risque d'hyperkaliémie — surveillance biologique généralement recommandée par le médecin traitant.",
        "Sartan (angiotensin II receptor blocker): contraindicated during pregnancy, carries a risk of high blood potassium — your doctor will typically recommend periodic blood monitoring."),
    ClassCaution(DrugClass.OPIOID, listOf("tramadol", "oxycodone", "fentanyl"),
        "Opioïde : risque de dépendance et de somnolence ; ne jamais associer à l'alcool ou à d'autres dépresseurs du système nerveux central sans avis médical.",
        "Opioid: carries a risk of dependence and drowsiness; never combine with alcohol or other central-nervous-system depressants without medical advice."),
    ClassCaution(DrugClass.ACE_INHIBITOR, listOf("ramipril", "perindopril"),
        "IEC (inhibiteur de l'enzyme de conversion) : toux sèche possible, contre-indiqué pendant la grossesse, risque d'hyperkaliémie.",
        "ACE inhibitor: may cause a dry cough, is contraindicated during pregnancy, and carries a risk of high blood potassium."),
    ClassCaution(DrugClass.SSRI, listOf("escitalopram"),
        "ISRS (inhibiteur sélectif de la recapture de la sérotonine) : ne jamais arrêter brutalement sans avis médical (syndrome de sevrage), risque de syndrome sérotoninergique en association avec d'autres médicaments sérotoninergiques.",
        "SSRI (selective serotonin reuptake inhibitor): never stop abruptly without medical advice (discontinuation syndrome); carries a serotonin-syndrome risk when combined with other serotonergic drugs."),
    ClassCaution(DrugClass.ANTIPSYCHOTIC, listOf("olanzapine", "risperidone", "rispéridone", "aripiprazole"),
        "Antipsychotique atypique : risque de prise de poids et de troubles métaboliques (glycémie, lipides) documenté sur le long terme — suivi médical régulier généralement recommandé.",
        "Atypical antipsychotic: carries a documented long-term risk of weight gain and metabolic changes (blood sugar, lipids) — regular medical follow-up is typically recommended."),
    ClassCaution(DrugClass.GABAPENTINOID, listOf("gabapentine", "pregabaline", "prégabaline"),
        "Gabapentinoïde : risque de somnolence et de dépendance reconnu (substances classées comme stupéfiants en France depuis 2021) — ne jamais associer à l'alcool.",
        "Gabapentinoid: recognized risk of drowsiness and dependence (reclassified as a controlled substance in France since 2021) — never combine with alcohol."),
    ClassCaution(DrugClass.PPI, listOf("pantoprazole"),
        "IPP (inhibiteur de la pompe à protons) : l'usage prolongé est associé à une baisse de l'absorption du calcium/vitamine B12 et à un risque accru de fracture (communications de sécurité EMA/FDA).",
        "PPI (proton pump inhibitor): long-term use is associated with reduced calcium/vitamin B12 absorption and an increased fracture risk (EMA/FDA safety communications)."),
    ClassCaution(DrugClass.PDE5_INHIBITOR, listOf("tadalafil", "sildenafil"),
        "Inhibiteur de la PDE5 : contre-indication formelle avec les dérivés nitrés (risque d'hypotension sévère) — à signaler systématiquement à tout professionnel de santé.",
        "PDE5 inhibitor: formally contraindicated with nitrate medications (risk of severe low blood pressure) — always disclose use to any healthcare professional."),
    ClassCaution(DrugClass.PARACETAMOL, listOf("paracetamol"),
        "Paracétamol : le surdosage (souvent par cumul involontaire de plusieurs produits qui en contiennent) est une cause reconnue d'atteinte hépatique grave — vérifiez toujours la dose totale journalière avec d'autres médicaments pris en parallèle.",
        "Paracetamol/acetaminophen: overdose (often from unknowingly combining several products that each contain it) is a recognized cause of serious liver injury — always check your total daily dose against any other medication taken alongside it."),
    ClassCaution(DrugClass.METFORMIN, listOf("metformine"),
        "Metformine : à interrompre temporairement avant un examen d'imagerie avec produit de contraste iodé et en cas d'insuffisance rénale sévère, sur avis médical (risque rare d'acidose lactique).",
        "Metformin: your doctor may have you temporarily stop it before an iodinated-contrast imaging exam or in severe kidney impairment (rare risk of lactic acidosis)."),
    ClassCaution(DrugClass.LEVOTHYROXINE, listOf("levothyroxine", "lévothyroxine"),
        "Lévothyroxine : marge thérapeutique étroite — à prendre à jeun, toujours au même moment de la journée, et sans changer de marque/génération sans avis médical.",
        "Levothyroxine: has a narrow therapeutic margin — take on an empty stomach, at the same time each day, and don't switch brands/formulations without medical advice."),
    ClassCaution(DrugClass.ANTICOAGULANT, listOf("rivaroxaban"),
        "Anticoagulant oral direct : risque hémorragique — ne jamais arrêter ni sauter une prise sans avis médical, et signaler systématiquement sa prise avant tout acte chirurgical ou dentaire.",
        "Direct oral anticoagulant: carries a bleeding risk — never stop or skip a dose without medical advice, and always disclose use before any surgical or dental procedure."),
    ClassCaution(DrugClass.BETA_BLOCKER, listOf("bisoprolol"),
        "Bêta-bloquant : ne jamais arrêter brutalement (risque de rebond : angine de poitrine, poussée tensionnelle) — prudence en cas d'asthme ou de BPCO.",
        "Beta-blocker: never stop abruptly (rebound risk: chest pain, blood pressure spike) — caution advised in asthma or COPD."),
    ClassCaution(DrugClass.DIURETIC, listOf("hydrochlorothiazide", "indapamide"),
        "Diurétique thiazidique : risque de déséquilibre électrolytique (baisse du potassium) et de photosensibilisation — surveillance biologique généralement recommandée.",
        "Thiazide diuretic: carries a risk of electrolyte imbalance (low potassium) and photosensitivity — periodic blood monitoring is typically recommended."),
    ClassCaution(DrugClass.CALCIUM_CHANNEL_BLOCKER, listOf("amlodipine"),
        "Inhibiteur calcique : peut provoquer des œdèmes des chevilles ; le jus de pamplemousse peut augmenter sa concentration dans le sang.",
        "Calcium channel blocker: may cause ankle swelling; grapefruit juice can raise its blood concentration."),
    ClassCaution(DrugClass.INHALED_CORTICOSTEROID, listOf("budesonide", "budésonide", "fluticasone"),
        "Corticoïde inhalé : se rincer la bouche après chaque utilisation pour limiter le risque de mycose buccale (candidose oropharyngée).",
        "Inhaled corticosteroid: rinse your mouth after each use to limit the risk of oral thrush (oropharyngeal candidiasis)."),
    ClassCaution(DrugClass.VITAMIN_D, listOf("cholecalciferol", "cholécalciférol"),
        "Vitamine D : une supplémentation prolongée à haute dose sans suivi médical peut entraîner un excès de calcium dans le sang (hypercalcémie).",
        "Vitamin D: prolonged high-dose supplementation without medical follow-up can lead to excess blood calcium (hypercalcemia)."),
    ClassCaution(DrugClass.PENICILLIN, listOf("amoxicilline"),
        "Pénicilline : famille d'antibiotiques la plus fréquemment associée à des réactions allergiques — signalez tout antécédent d'allergie aux pénicillines avant toute prise.",
        "Penicillin: the antibiotic family most frequently associated with allergic reactions — disclose any history of penicillin allergy before taking it."),
)
