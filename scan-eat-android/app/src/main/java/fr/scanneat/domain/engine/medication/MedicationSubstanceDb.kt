package fr.scanneat.domain.engine.medication

import fr.scanneat.domain.engine.nutrition.matchNameDictionary

// ============================================================================
// MEDICATION SUBSTANCE DB — same table-plus-substring-match pattern used
// throughout domain.engine.nutrition (AdditivesDb / NamedSubstanceDb /
// IngredientFactsDb), applied here to a medication's active substance(s)
// (MedicationDbEntry.activeSubstances, itself real BDPM CIS_COMPO_bdpm.txt
// data — see MedicationLookupDb).
//
// Two separate tables, deliberately:
//  - DRUG_FACTS: one neutral, Wikipedia-sourced fact per substance (fr/en
//    article's own opening sentence, same bulk-fetch method as
//    IngredientFactsDb — fetched 2026-07-13, re-verifiable against the
//    live article).
//  - CLASS_CAUTIONS: general, well-established drug-class-level safety
//    information (interactions/side-effect classes documented in every
//    EU Summary of Product Characteristics for that class — e.g. "NSAIDs
//    carry a GI bleeding risk"), grouped by pharmacological class rather
//    than repeated per substance. This is deliberately NOT dosage,
//    diagnosis, or treatment advice, and every caution ends by pointing
//    back to a pharmacist/doctor rather than telling the user what to do
//    — the same line MedicationLookupDb's own header already draws
//    between "identify what was scanned" and "give medical advice".
//
// Covers the ~35 most common active substances by presentation count in
// the commercialized BDPM dataset (see the CIS_COMPO frequency count used
// to build this list) — not exhaustive, since a wrong drug-safety note is
// far worse than a missing one.
// ============================================================================

private data class DrugFact(
    val keys: List<String>,
    val factFr: String,
    val factEn: String,
)

private val DRUG_FACTS: List<DrugFact> = listOf(
    DrugFact(listOf("paracetamol"),
        "Le paracétamol, aussi appelé acétaminophène, est un composé chimique utilisé comme antalgique (anti-douleur) et antipyrétique (anti-fièvre), qui figure depuis le début des années 1950 parmi les médicaments les plus communément utilisés et prescrits au monde.",
        "Paracetamol, or acetaminophen, is an analgesic and antipyretic agent used to treat fever and mild to moderate pain."),
    DrugFact(listOf("ibuprofene"),
        "Ibuprofène est la dénomination commune internationale de l'acide 2-[4-(2-méthyl)propyl]phénylpropanoïque.",
        "Ibuprofen is a nonsteroidal anti-inflammatory drug (NSAID) that is used to relieve pain, fever, and inflammation."),
    DrugFact(listOf("hydrochlorothiazide"),
        "L'hydrochlorothiazide est une molécule utilisée comme médicament diurétique de la classe des thiazidiques.",
        "Hydrochlorothiazide, sold under the brand name Hydrodiuril among others, is a diuretic medication used to treat hypertension and swelling due to fluid build-up."),
    DrugFact(listOf("amlodipine"),
        "Le bésylate d'amlodipine est un antagoniste des canaux calciques de type L à longue action, utilisé comme antihypertenseur et pour le traitement de l'angine de poitrine.",
        "Amlodipine, sold under the brand name Norvasc, Copine among others, is a calcium channel blocker medication used to treat high blood pressure, coronary artery disease (CAD) and variant angina."),
    DrugFact(listOf("atorvastatine", "atorvastatin"),
        "L'atorvastatine est un médicament de type statine utilisé pour son action hypocholestérolémiante.",
        "Atorvastatin, sold under the brand name Lipitor among others, is a statin medication used to prevent cardiovascular disease in those at high risk and to treat abnormal lipid levels."),
    DrugFact(listOf("rosuvastatine"),
        "La rosuvastatine appartient au groupes des médicaments appelés statines, utilisées dans les dyslipidémies pour modifier les concentrations de choléstérol dans le sang.",
        "Rosuvastatin, sold under the brand name Crestor among others, is a statin medication, used to prevent cardiovascular disease in those at high risk and treat dyslipidemia or hyperlipidemia."),
    DrugFact(listOf("simvastatine"),
        "La simvastatine est une statine, c’est-à-dire un inhibiteur puissant de l'enzyme hydroxyméthylglutaryl CoA réductase — ou HMG-CoA réductase —.",
        "Simvastatin, sold under the brand name Zocor among others, is a statin, a type of lipid-lowering medication."),
    DrugFact(listOf("ezetimibe", "ézétimibe"),
        "L’ézétimibe est un médicament utilisé dans le traitement des hypercholestérolémies et agissant en diminuant l'absorption du cholestérol ingéré par le tube digestif.",
        "Ezetimibe, sold under the brand name Zetia among others, is a medication used to treat high blood cholesterol and certain other lipid abnormalities."),
    DrugFact(listOf("valsartan"),
        "Le valsartan est une molécule utilisée comme médicament par voie orale, antagoniste des récepteurs de l'angiotensine II.",
        "Valsartan, sold under the brand name Diovan among others, is a medication used to treat high blood pressure, heart failure, and diabetic kidney disease."),
    DrugFact(listOf("candesartan", "candésartan"),
        "Le candésartan est un antihypertenseur qui appartient à la famille des antagonistes des récepteurs de l'angiotensine II.",
        "Candesartan is an angiotensin receptor blocker (ARB) primarily used to treat high blood pressure and congestive heart failure."),
    DrugFact(listOf("irbesartan", "irbésartan"),
        "L'irbésartan est un antihypertenseur qui appartient à la famille des antagonistes des récepteurs de l'angiotensine II.",
        "Irbesartan, sold under the brand name Aprovel among others, is a medication used to treat hypertension, heart failure, and diabetic kidney disease."),
    DrugFact(listOf("losartan"),
        "Le losartan est un antihypertenseur qui appartient à la famille des antagonistes des récepteurs de l'angiotensine II.",
        "Losartan, sold under the brand name Cozaar among others, is a medication used to treat high blood pressure (hypertension)."),
    DrugFact(listOf("telmisartan"),
        "Le telmisartan est un des antagonistes des récepteurs de l'angiotensine II et est utilisé comme antihypertenseur.",
        "Telmisartan, sold under the brand name Micardis among others, is a medication used to treat high blood pressure and heart failure."),
    DrugFact(listOf("tramadol"),
        "Le tramadol est un analgésique central développé par la firme allemande Grünenthal GmbH (en) dans les années 1970.",
        "Tramadol, sold under the brand name Tramal among others, is an opioid pain medication and a serotonin–norepinephrine reuptake inhibitor (SNRI) used to treat moderate to severe pain."),
    DrugFact(listOf("oxycodone"),
        "L'oxycodone, dihydrohydroxycodéinone, ou dihydro-oxycodéinone, est un antalgique stupéfiant très puissant dérivé de la thébaïne.",
        "Oxycodone, sold under the brand names Roxicodone and OxyContin among others, is a semi-synthetic opioid used medically for the treatment of moderate to severe pain."),
    DrugFact(listOf("fentanyl"),
        "Le fentanyl, appelé aussi apache ou dance fever, est une drogue à propriété analgésique opioïde, synthétisée pour la première fois par le docteur Paul Janssen en Belgique vers la fin des années 1950.",
        "Fentanyl is a highly potent synthetic opioid of the piperidine family, used primarily as pain medication."),
    DrugFact(listOf("metformine"),
        "La metformine est un antidiabétique oral de la famille des biguanides normoglycémiants utilisé en première intention dans le traitement du diabète de type 2. Son rôle est de diminuer l'insulino-résistance de l'organisme intolérant aux glucides et de diminuer la néoglucogenèse hépatique.",
        "Metformin, sold under the brand name Glucophage, among others, is the main first-line medication for the treatment of type 2 diabetes, particularly in people who are overweight."),
    DrugFact(listOf("ramipril"),
        "Le ramipril est une molécule utilisée comme antihypertenseur de la famille des inhibiteurs de l'enzyme de conversion.",
        "Ramipril, sold under the brand name Altace among others, is an ACE inhibitor type medication used to treat high blood pressure, heart failure, and diabetic kidney disease."),
    DrugFact(listOf("perindopril", "périndopril"),
        "Le périndopril est un inhibiteur de l'enzyme de conversion de l’angiotensine (IEC).",
        "Perindopril is a medication used to treat high blood pressure, heart failure, or stable coronary artery disease."),
    DrugFact(listOf("amoxicilline"),
        "L'amoxicilline est un antibiotique β-lactamine bactéricide de la famille des aminopénicillines indiqué dans le traitement des infections bactériennes à germes sensibles.",
        "Amoxicillin is an antibiotic medication belonging to the aminopenicillin class of the penicillin family."),
    DrugFact(listOf("levothyroxine", "lévothyroxine"),
        "La lévothyroxine, aussi connue sous le nom de L-thyroxine, T4 synthétique ou 3,5,3',5'-tetraiodo-L-thyronine, est une forme synthétique de la thyroxine, également utilisée comme médicament.",
        "Levothyroxine, also known as L-thyroxine, is a synthetic form of the thyroid hormone thyroxine (T4)."),
    DrugFact(listOf("escitalopram"),
        "L'escitalopram, commercialisé dans divers pays sous les noms de Lexapro, Cipralex, Sipralexa et Seroplex, est un antidépresseur inhibiteur sélectif de la recapture de la sérotonine.",
        "Escitalopram, sold under the brand names Lexapro and Cipralex, among others, is an antidepressant medication of the selective serotonin reuptake inhibitor (SSRI) class."),
    DrugFact(listOf("olanzapine"),
        "L'olanzapine (DCI) est un antipsychotique de seconde génération appartenant à la classe des thiénobenzodiazépines employé principalement pour traiter la schizophrénie et le trouble bipolaire.",
        "Olanzapine, sold under the brand name Zyprexa among others, is an atypical antipsychotic primarily used to treat schizophrenia and bipolar disorder."),
    DrugFact(listOf("risperidone", "rispéridone"),
        "La rispéridone est un antipsychotique de seconde génération, appartenant à la classe des benzisoxazoles, commercialisé principalement sous le nom Risperdal.",
        "Risperidone, sold under the brand name Risperdal among others, is an atypical antipsychotic used to treat schizophrenia and bipolar disorder, as well as aggressive and self-injurious behaviors associated with autism spectrum disorder."),
    DrugFact(listOf("aripiprazole"),
        "L'aripiprazole (DCI) est un médicament antipsychotique de troisième génération.",
        "Aripiprazole, sold under the brand name Abilify, among others, is a unique atypical antipsychotic primarily used in the treatment of schizophrenia, mania in bipolar disorder, and irritability associated with autism spectrum disorder; other uses include as an add-on treatment for major depressive disorder, obsessive–compulsive disorder, and tic disorders."),
    DrugFact(listOf("gabapentine"),
        "La gabapentine, molécule dérivé de l'acide γ-aminobutyrique, est un médicament commercialisé sous le nom commercial de Neurontin ou génériques.",
        "Gabapentin, sold under the brand name Neurontin among others, is an anticonvulsant medication used to treat neuropathic pain and partial seizures of epilepsy."),
    DrugFact(listOf("pregabaline", "prégabaline"),
        "La prégabaline est un médicament utilisé, de manière principale, dans le traitement des douleurs neuropathiques et des crises d'épilepsie partielles.",
        "Pregabalin, sold under the brand names Axalid and Lyrica among others, is an anticonvulsant, analgesic, and anxiolytic amino acid medication used to treat epilepsy, neuropathic pain, fibromyalgia, restless legs syndrome, opioid withdrawal, generalized anxiety disorder (GAD), and postherpetic neuralgia."),
    DrugFact(listOf("rivaroxaban"),
        "Le rivaroxaban est un médicament anticoagulant oral direct, inhibiteur du facteur Xa de la famille des oxazolidinones.",
        "Rivaroxaban, sold under the brand name Xarelto among others, is an anticoagulant medication used to treat and reduce the risk of blood clots."),
    DrugFact(listOf("budesonide", "budésonide"),
        "Le budésonide est un médicament anti-inflammatoire (glucocorticoïde) utilisé entre autres pour le traitement de l'asthme, la rhinite non infectieuse et pour le traitement et la prévention des polypes nasaux.",
        "Budesonide, sold under the brand name Pulmicort, among others, is a steroid medication."),
    DrugFact(listOf("fluticasone"),
        "La fluticasone est un anti-inflammatoire stéroïdien de la famille des glucocorticoïdes.",
        "Fluticasone propionate, sold under the brand names Flovent and Flonase among others, is a glucocorticoid steroid medication."),
    DrugFact(listOf("pantoprazole"),
        "Le pantoprazole est un médicament gastro-résistant qui réduit la quantité d'acide sécrétée par l'estomac.",
        "Pantoprazole, sold under the brand name Protonix, among others, is a medication used for the treatment of stomach ulcers, short-term treatment of erosive esophagitis due to gastroesophageal reflux disease (GERD), maintenance of healing of erosive esophagitis, and pathological hypersecretory conditions including Zollinger–Ellison syndrome."),
    DrugFact(listOf("tadalafil"),
        "Le tadalafil est une molécule indolique, dérivée de base azotée de la famille des inhibiteurs de la phosphodiestérase de type 5 (PDE5), utilisée dans le traitement des troubles de l'érection.",
        "Tadalafil, sold under the brand name Cialis among others, is a medication used to treat erectile dysfunction, benign prostatic hyperplasia, and pulmonary arterial hypertension."),
    DrugFact(listOf("sildenafil", "sildénafil"),
        "Le citrate de sildénafil est un médicament de la classe des inhibiteurs de la phosphodiestérase de type 5 (PDE5) développé par la firme pharmaceutique Pfizer.",
        "Sildenafil, sold under the brand name Viagra among others, is a medication used to treat erectile dysfunction and pulmonary arterial hypertension."),
    DrugFact(listOf("cholecalciferol", "cholécalciférol"),
        "Le cholécalciférol est une forme de vitamine D, également appelée vitamine D3. Son nom systématique est « (3β,5Z,7E)-9,10-sécocholesta-5,7,10(19)-triène-3-ol ». La vitamine D3 est un sécostéroïde plutôt parent de la testostérone, du cholestérol et du cortisol.",
        "Cholecalciferol, also known as vitamin D3, colecalciferol or calciol, is a skin-made vitamin D that is found in certain foods and used as a dietary supplement."),
    DrugFact(listOf("aciclovir"),
        "L'aciclovir (DCI) ou acyclovir commercialisé sous les noms de Zovirax, Activir ou Aciclovir, est l'un des principaux médicaments antiviraux.",
        "Aciclovir, also known as acyclovir, is an antiviral medication."),
    DrugFact(listOf("bisoprolol"),
        "Le bisoprolol est une molécule de la classe des bêta-bloquants, utilisée pour traiter l'arythmie cardiaque, la tachycardie, l'angine de poitrine, etc.",
        "Bisoprolol, sold under the brand name Zebeta among others, is a beta blocker which is selective for the beta-1 receptor and used for cardiovascular diseases, including tachyarrhythmias, high blood pressure, angina, and heart failure."),
    DrugFact(listOf("indapamide"),
        "L'indapamide est un médicament, de type diurétique, utilisé[Où ?] pour traiter l'hypertension artérielle ou certains œdèmes[évasif].",
        "Indapamide is a thiazide-like diuretic drug used in the treatment of hypertension, as well as decompensated heart failure."),)

private enum class DrugClass { NSAID, STATIN, SARTAN, OPIOID, ACE_INHIBITOR, SSRI, ANTIPSYCHOTIC,
    GABAPENTINOID, PPI, PDE5_INHIBITOR, PARACETAMOL, METFORMIN, LEVOTHYROXINE, ANTICOAGULANT,
    BETA_BLOCKER, DIURETIC, CALCIUM_CHANNEL_BLOCKER, INHALED_CORTICOSTEROID, VITAMIN_D, PENICILLIN }

private data class ClassCaution(
    val drugClass: DrugClass,
    val keys: List<String>,
    val cautionFr: String,
    val cautionEn: String,
)

private val CLASS_CAUTIONS: List<ClassCaution> = listOf(
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

data class MedicationHints(
    val facts: List<String>,
    val cautions: List<String>,
)

// Condition-specific amplifications on top of the generic class caution above —
// closes the same PersonalScoreEngine-vs-hint-panel gap ProductHints closes for
// food (see HealthConditionGuidanceDb). These are the well-known, SPC-documented
// condition/class interactions (e.g. ACE inhibitors and sartans are formally
// contraindicated in pregnancy; NSAIDs can both raise blood pressure and blunt
// antihypertensive drugs) — not a general "ask your doctor" restatement.
private val CONDITION_AMPLIFICATIONS: Map<Pair<DrugClass, String>, Pair<String, String>> = mapOf(
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
