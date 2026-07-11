package fr.scanneat.shared

import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

// ============================================================================
// ADDITIVES DATABASE — port of scoring-engine.ts SECTION 2
// Source citations preserved verbatim from the original TS engine.
// ============================================================================

enum class AdditiveTier(val value: Int) { ONE(1), TWO(2), THREE(3) }

enum class AdditiveCategory(val key: String) {
    PRESERVATIVE("preservative"),
    EMULSIFIER("emulsifier"),
    ACIDULANT("acidulant"),
    STABILIZER("stabilizer"),
    COLORANT("colorant"),
    ANTIOXIDANT("antioxidant"),
    SWEETENER("sweetener"),
    FLAVOR_ENHANCER("flavor_enhancer"),
    THICKENER("thickener"),
    HUMECTANT("humectant"),
    GLAZING("glazing"),
    GLAZING_AGENT("glazing_agent"),
    ACIDITY_REGULATOR("acidity_regulator"),
    ANTICAKING("anticaking"),
    SOLVENT("solvent"),
    FLOUR_TREATMENT("flour_treatment"),
    SEQUESTRANT("sequestrant"),
}

data class AdditiveInfo(
    val eNumber: String,
    val names: List<String>,
    val tier: AdditiveTier,
    val category: AdditiveCategory,
    val concern: String,
    val source: String,
)

/** Additive categories that trigger cosmetic-processing penalty in Pillar 1. */
val COSMETIC_ADDITIVE_CATEGORIES = setOf(
    AdditiveCategory.COLORANT,
    AdditiveCategory.FLAVOR_ENHANCER,
    AdditiveCategory.SWEETENER,
)

val ADDITIVES_DB: List<AdditiveInfo> = listOf(
    // ===== TIER 1: Serious concern =====
    AdditiveInfo("E249", listOf("nitrite de potassium", "potassium nitrite"), AdditiveTier.ONE, AdditiveCategory.PRESERVATIVE,
        "Curing agent. IARC Group 1 (processed meat, carcinogenic to humans).",
        "IARC Monograph Vol 114 (2015); EFSA Re-evaluation 2017."),
    AdditiveInfo("E250", listOf("nitrite de sodium", "sodium nitrite"), AdditiveTier.ONE, AdditiveCategory.PRESERVATIVE,
        "Curing agent. IARC Group 1 via N-nitroso compound formation.",
        "IARC Monograph Vol 114 (2015); IARC Vol 94 (2010); EFSA Re-evaluation 2017."),
    AdditiveInfo("E251", listOf("nitrate de sodium", "sodium nitrate"), AdditiveTier.ONE, AdditiveCategory.PRESERVATIVE,
        "Converts to nitrite in the gut. Same N-nitroso pathway.",
        "IARC Monograph Vol 94 (2010); EFSA Re-evaluation 2017."),
    AdditiveInfo("E252", listOf("nitrate de potassium", "potassium nitrate"), AdditiveTier.ONE, AdditiveCategory.PRESERVATIVE,
        "Converts to nitrite in the gut. Same N-nitroso pathway.",
        "IARC Monograph Vol 94 (2010); EFSA Re-evaluation 2017."),
    AdditiveInfo("E433", listOf("polysorbate 80", "polysorbate-80"), AdditiveTier.ONE, AdditiveCategory.EMULSIFIER,
        "Detergent-class emulsifier. Microbiome shifts + mucus-layer erosion in mice and limited human data.",
        "Chassaing et al., Nature 2015; Gastroenterology 2022."),
    AdditiveInfo("E466", listOf("carboxymethylcellulose", "cmc", "cellulose gum"), AdditiveTier.ONE, AdditiveCategory.EMULSIFIER,
        "Detergent-class emulsifier. Mouse microbiome disruption replicated in human feeding trial.",
        "Chassaing et al., Nature 2015; Gastroenterology 2022 (FRESH trial, CMC-specific)."),
    AdditiveInfo("E171", listOf("dioxyde de titane", "titanium dioxide"), AdditiveTier.ONE, AdditiveCategory.COLORANT,
        "Banned as food additive in EU since August 2022 (genotoxicity concerns, nanoparticulate fraction).",
        "EFSA Scientific Opinion 2021;19(5):6585; Commission Regulation (EU) 2022/63."),
    AdditiveInfo("E220", listOf("anhydride sulfureux", "dioxyde de soufre", "sulfur dioxide"), AdditiveTier.ONE, AdditiveCategory.PRESERVATIVE,
        "Sulfite — mandatory EU allergen. Triggers asthma and sulfite sensitivity.",
        "EU Regulation 1169/2011 Annex II; EFSA Re-evaluation 2016;14(4):4438."),
    AdditiveInfo("E221", listOf("sulfite de sodium", "sodium sulfite"), AdditiveTier.ONE, AdditiveCategory.PRESERVATIVE,
        "Sulfite — mandatory EU allergen.",
        "EU Regulation 1169/2011 Annex II; EFSA Re-evaluation 2016."),
    AdditiveInfo("E223", listOf("métabisulfite de sodium", "sodium metabisulfite"), AdditiveTier.ONE, AdditiveCategory.PRESERVATIVE,
        "Sulfite — mandatory EU allergen.",
        "EU Regulation 1169/2011 Annex II; EFSA Re-evaluation 2016."),
    AdditiveInfo("E224", listOf("métabisulfite de potassium", "potassium metabisulfite"), AdditiveTier.ONE, AdditiveCategory.PRESERVATIVE,
        "Sulfite — mandatory EU allergen.",
        "EU Regulation 1169/2011 Annex II; EFSA Re-evaluation 2016."),
    AdditiveInfo("E385", listOf("edta", "calcium disodium edta"), AdditiveTier.ONE, AdditiveCategory.SEQUESTRANT,
        "Metal chelator. High chronic intake can affect mineral bioavailability.",
        "EFSA Scientific Opinion 2018;16(11):5007 (ADI 1.9 mg/kg bw/day)."),

    // ===== TIER 2: Moderate concern =====
    AdditiveInfo("E338", listOf("acide phosphorique", "phosphoric acid"), AdditiveTier.TWO, AdditiveCategory.ACIDULANT,
        "Phosphate additive. Epidemiologic associations with cardiovascular/renal outcomes at high chronic intakes.",
        "EFSA Scientific Opinion on phosphates 2019;17(6):5674."),
    AdditiveInfo("E450", listOf("diphosphate", "pyrophosphate"), AdditiveTier.TWO, AdditiveCategory.STABILIZER,
        "Phosphate additive (same group as E338).", "EFSA 2019;17(6):5674."),
    AdditiveInfo("E451", listOf("triphosphate", "tripolyphosphate"), AdditiveTier.TWO, AdditiveCategory.STABILIZER,
        "Phosphate additive.", "EFSA 2019;17(6):5674."),
    AdditiveInfo("E452", listOf("polyphosphate"), AdditiveTier.TWO, AdditiveCategory.STABILIZER,
        "Phosphate additive.", "EFSA 2019;17(6):5674."),
    AdditiveInfo("E339", listOf("phosphate de sodium", "sodium phosphate"), AdditiveTier.TWO, AdditiveCategory.STABILIZER,
        "Phosphate additive.", "EFSA 2019;17(6):5674."),
    AdditiveInfo("E340", listOf("phosphate de potassium", "potassium phosphate"), AdditiveTier.TWO, AdditiveCategory.STABILIZER,
        "Phosphate additive.", "EFSA 2019;17(6):5674."),
    AdditiveInfo("E341", listOf("phosphate de calcium", "calcium phosphate"), AdditiveTier.TWO, AdditiveCategory.STABILIZER,
        "Phosphate additive.", "EFSA 2019;17(6):5674."),
    AdditiveInfo("E102", listOf("tartrazine", "jaune de tartrazine"), AdditiveTier.TWO, AdditiveCategory.COLORANT,
        "Azo dye. Hyperactivity/attention association in children; EU warning label required.",
        "McCann et al., The Lancet 370:1560 (2007); EU Regulation 1333/2008 Annex V."),
    AdditiveInfo("E110", listOf("jaune orangé s", "sunset yellow"), AdditiveTier.TWO, AdditiveCategory.COLORANT,
        "Azo dye. Southampton-study association; EU warning label.", "McCann et al. 2007; EU 1333/2008 Annex V."),
    AdditiveInfo("E122", listOf("azorubine", "carmoisine"), AdditiveTier.TWO, AdditiveCategory.COLORANT,
        "Azo dye. EU warning label.", "McCann et al. 2007; EU 1333/2008 Annex V."),
    AdditiveInfo("E124", listOf("ponceau 4r", "rouge cochenille a"), AdditiveTier.TWO, AdditiveCategory.COLORANT,
        "Azo dye. EU warning label.", "McCann et al. 2007; EU 1333/2008 Annex V."),
    AdditiveInfo("E129", listOf("rouge allura", "allura red"), AdditiveTier.TWO, AdditiveCategory.COLORANT,
        "Azo dye. EU warning label.", "McCann et al. 2007; EU 1333/2008 Annex V."),
    AdditiveInfo("E104", listOf("jaune de quinoléine", "quinoline yellow"), AdditiveTier.TWO, AdditiveCategory.COLORANT,
        "Quinophthalone dye; EU warning label.", "McCann et al. 2007; EU 1333/2008 Annex V."),
    AdditiveInfo("E127", listOf("érythrosine", "erythrosine"), AdditiveTier.TWO, AdditiveCategory.COLORANT,
        "Iodine-containing dye; thyroid concerns at high intakes.", "EFSA Re-evaluation 2011;9(1):1854."),
    AdditiveInfo("E173", listOf("aluminium"), AdditiveTier.TWO, AdditiveCategory.COLORANT,
        "Aluminium metallic colorant; neurotoxicity TWI.", "EFSA 2008;754 (TWI 1 mg/kg bw/week)."),
    AdditiveInfo("E150", listOf("caramel", "colorant caramel"), AdditiveTier.TWO, AdditiveCategory.COLORANT,
        "Caramel, sub-class unspecified; most commercial caramel in dark sodas is E150c/d (4-MEI, IARC 2B).",
        "IARC Vol 101 (2013); EFSA 2011;9(3):2004."),
    AdditiveInfo("E150c", listOf("e150c", "caramel ammoniacal", "ammonia caramel"), AdditiveTier.TWO, AdditiveCategory.COLORANT,
        "4-methylimidazole (4-MEI) formation; IARC Group 2B.", "IARC Vol 101 (2013); EFSA 2011;9(3):2004."),
    AdditiveInfo("E150d", listOf("e150d", "caramel sulfite-ammoniacal", "sulfite-ammonia caramel"), AdditiveTier.TWO, AdditiveCategory.COLORANT,
        "Same 4-MEI concern as E150c; most common in dark sodas.", "IARC Vol 101 (2013); EFSA 2011;9(3):2004."),
    AdditiveInfo("E320", listOf("bha", "butylhydroxyanisol"), AdditiveTier.TWO, AdditiveCategory.ANTIOXIDANT,
        "IARC Group 2B (possibly carcinogenic, rodent forestomach tumours).",
        "IARC Monograph Vol 40 (1986); EFSA ADI 1 mg/kg bw/day reaffirmed 2011."),
    AdditiveInfo("E321", listOf("bht", "butylhydroxytoluène"), AdditiveTier.TWO, AdditiveCategory.ANTIOXIDANT,
        "Synthetic antioxidant; low EFSA ADI on developmental/reproductive grounds.",
        "EFSA Scientific Opinion 2012;10(3):2588."),
    AdditiveInfo("E951", listOf("aspartame"), AdditiveTier.TWO, AdditiveCategory.SWEETENER,
        "IARC Group 2B (possibly carcinogenic, 2023). Contraindicated in phenylketonuria.",
        "IARC Monograph Vol 134 (2023); JECFA 2023; EFSA ADI 40 mg/kg bw/day (2013)."),
    AdditiveInfo("E950", listOf("acésulfame-k", "acesulfame de potassium", "acesulfame potassium"), AdditiveTier.TWO, AdditiveCategory.SWEETENER,
        "Non-nutritive sweetener; mixed long-term metabolic evidence.", "EFSA Re-evaluation 2000 ADI 9 mg/kg bw/day."),
    AdditiveInfo("E955", listOf("sucralose"), AdditiveTier.TWO, AdditiveCategory.SWEETENER,
        "Decomposes at high temperatures; microbiome/glucose-response changes.", "EFSA 2000; Suez et al., Cell 2022."),
    AdditiveInfo("E954", listOf("saccharine", "saccharin"), AdditiveTier.TWO, AdditiveCategory.SWEETENER,
        "Legacy rat bladder-tumour data; IARC Group 3 (1999).", "IARC Vol 73 (1999); EFSA Re-evaluation 2018."),
    AdditiveInfo("E952", listOf("cyclamate", "cyclamate de sodium"), AdditiveTier.TWO, AdditiveCategory.SWEETENER,
        "Banned in the US (1969); authorised in EU within ADI.", "EFSA Re-evaluation 2000; FDA ban 21 CFR 189.135."),
    AdditiveInfo("E968", listOf("érythritol", "erythritol"), AdditiveTier.TWO, AdditiveCategory.SWEETENER,
        "Higher plasma erythritol linked to major adverse cardiovascular events (prospective cohort); causality debated.",
        "Witkowski et al., Nature Medicine 29:710 (2023); EFSA 2023;21(12):8430."),
    AdditiveInfo("E211", listOf("benzoate de sodium", "sodium benzoate"), AdditiveTier.TWO, AdditiveCategory.PRESERVATIVE,
        "Can react with vitamin C to form trace benzene.", "Gardner & Lawrence, J Food Prot 2007; EFSA 2016;14(3):4433."),
    AdditiveInfo("E212", listOf("benzoate de potassium", "potassium benzoate"), AdditiveTier.TWO, AdditiveCategory.PRESERVATIVE,
        "Same benzene-formation pathway as E211.", "EFSA 2016;14(3):4433."),
    AdditiveInfo("E210", listOf("acide benzoïque", "benzoic acid"), AdditiveTier.TWO, AdditiveCategory.PRESERVATIVE,
        "Parent of E211/E212; same benzene-formation pathway.", "Gardner & Lawrence 2007; EFSA 2016;14(3):4433."),
    AdditiveInfo("E475", listOf("esters polyglycériques d'acides gras", "polyglycerol esters of fatty acids"), AdditiveTier.TWO, AdditiveCategory.EMULSIFIER,
        "Synthetic emulsifier; class-level microbiome concern (extrapolated caution).", "EFSA 2017;15(12):5089."),
    AdditiveInfo("E905", listOf("cire microcristalline", "microcrystalline wax", "petroleum wax"), AdditiveTier.TWO, AdditiveCategory.GLAZING,
        "Petroleum-derived; MOSH/MOAH migration concern.", "EFSA 2012;10(6):2704; EFSA update 2023;21(9):8215."),
    AdditiveInfo("E1520", listOf("propylène glycol", "propylene glycol"), AdditiveTier.TWO, AdditiveCategory.SOLVENT,
        "Humectant/solvent; low EFSA ADI; narrowly restricted.", "EFSA 2018;16(4):5235."),

    // ===== TIER 3: Minor / contextual =====
    AdditiveInfo("E407", listOf("carraghénane", "carrageenan"), AdditiveTier.THREE, AdditiveCategory.THICKENER,
        "EFSA reaffirmed safety at use levels; animal inflammation signals not replicated at dietary doses.", "EFSA 2018;16(4):5238."),
    AdditiveInfo("E471", listOf("mono- et diglycérides d'acides gras"), AdditiveTier.THREE, AdditiveCategory.EMULSIFIER,
        "Ubiquitous emulsifier; tier reflects UPF-class marker, not direct concern.", "EFSA 2017;15(11):5045."),
    AdditiveInfo("E472", listOf("esters de mono- et diglycérides", "esters of monoglycerides"), AdditiveTier.THREE, AdditiveCategory.EMULSIFIER,
        "Family of emulsifiers; no numerical ADI.", "EFSA 2017;15(11):5045 (group with E471)."),
    AdditiveInfo("E621", listOf("glutamate monosodique", "monosodium glutamate", "msg"), AdditiveTier.THREE, AdditiveCategory.FLAVOR_ENHANCER,
        "EFSA derived group ADI 2017 out of caution; JECFA maintains safe.", "EFSA 2017;15(7):4910."),
    AdditiveInfo("E627", listOf("guanylate disodique", "disodium guanylate"), AdditiveTier.THREE, AdditiveCategory.FLAVOR_ENHANCER,
        "Nucleotide flavour enhancer; UPF flavour engineering signal.", "EFSA 2017;15(7):4910."),
    AdditiveInfo("E631", listOf("inosinate disodique", "disodium inosinate"), AdditiveTier.THREE, AdditiveCategory.FLAVOR_ENHANCER,
        "Nucleotide flavour enhancer; often animal-derived (relevant for vegetarians).", "EFSA 2017;15(7):4910."),
    AdditiveInfo("E635", listOf("ribonucléotides disodiques", "disodium ribonucleotides"), AdditiveTier.THREE, AdditiveCategory.FLAVOR_ENHANCER,
        "Mix of E631/E627; same context.", "EFSA 2017;15(7):4910."),
    AdditiveInfo("E316", listOf("érythorbate de sodium", "sodium erythorbate"), AdditiveTier.THREE, AdditiveCategory.ANTIOXIDANT,
        "Vitamin C isomer; often co-signals nitrite curing system.", "EFSA 2015 (ascorbic acid family)."),
    AdditiveInfo("E330", listOf("acide citrique", "citric acid"), AdditiveTier.THREE, AdditiveCategory.ACIDULANT,
        "Ubiquitous acidulant; no authoritative concern.", "EU authorisation without ADI."),
    AdditiveInfo("E300", listOf("acide ascorbique", "ascorbic acid", "vitamine c"), AdditiveTier.THREE, AdditiveCategory.ANTIOXIDANT,
        "Vitamin C antioxidant; no concern.", "EU authorisation without ADI."),
    AdditiveInfo("E322", listOf("lécithine", "lecithines", "lecithin", "lécithine de soja", "lécithine de tournesol"), AdditiveTier.THREE, AdditiveCategory.EMULSIFIER,
        "Phospholipid emulsifier; tier reflects formulation marker role.", "EFSA 2017;15(4):4742."),
    AdditiveInfo("E415", listOf("gomme xanthane", "xanthan gum"), AdditiveTier.THREE, AdditiveCategory.THICKENER,
        "Microbial polysaccharide; EFSA confirmed no concern.", "EFSA 2017;15(2):4712."),
    AdditiveInfo("E412", listOf("gomme guar", "guar gum"), AdditiveTier.THREE, AdditiveCategory.THICKENER,
        "Legume-derived soluble fibre; no concern.", "EFSA 2017;15(2):4669."),
    AdditiveInfo("E440", listOf("pectine", "pectines", "pectin"), AdditiveTier.THREE, AdditiveCategory.THICKENER,
        "Natural plant fibre; no concern.", "EFSA 2017;15(7):4874."),
    AdditiveInfo("E202", listOf("sorbate de potassium", "potassium sorbate"), AdditiveTier.THREE, AdditiveCategory.PRESERVATIVE,
        "Mould/yeast preservative; low concern.", "EFSA 2019;17(3):5626."),
    AdditiveInfo("E270", listOf("acide lactique", "lactic acid"), AdditiveTier.THREE, AdditiveCategory.ACIDULANT,
        "Naturally occurring metabolite; no concern.", "EU authorisation without ADI."),
    AdditiveInfo("E296", listOf("acide malique", "malic acid"), AdditiveTier.THREE, AdditiveCategory.ACIDULANT,
        "Naturally occurring fruit acid; no concern.", "EU authorisation without ADI."),
    AdditiveInfo("E500", listOf("bicarbonate de sodium", "sodium bicarbonate", "carbonate de sodium"), AdditiveTier.THREE, AdditiveCategory.ACIDITY_REGULATOR,
        "Leavening agent; no concern.", "EU authorisation without ADI."),
    AdditiveInfo("E551", listOf("dioxyde de silicium", "silicon dioxide", "silice"), AdditiveTier.THREE, AdditiveCategory.ANTICAKING,
        "Anti-caking; EFSA 2018 requested more data on nanoparticulate forms.", "EFSA 2018;16(1):5088."),
    AdditiveInfo("E422", listOf("glycérol", "glycerol", "glycérine"), AdditiveTier.THREE, AdditiveCategory.HUMECTANT,
        "Metabolised like a carbohydrate; no ADI specified.", "EU authorisation without ADI."),
    AdditiveInfo("E960", listOf("glycosides de stéviol", "steviol glycosides", "stévia", "stevia"), AdditiveTier.THREE, AdditiveCategory.SWEETENER,
        "Plant-derived non-nutritive sweetener; lower concern than artificial.", "EFSA 2010;8(4):1537."),
    AdditiveInfo("E100", listOf("curcumine", "curcuma (colorant)"), AdditiveTier.THREE, AdditiveCategory.COLORANT,
        "Natural colorant; cosmetic-processing signal.", "EFSA 2010;8(9):1679."),
    AdditiveInfo("E160a", listOf("caroténoïdes", "beta-carotène", "beta carotene", "carotenes"), AdditiveTier.THREE, AdditiveCategory.COLORANT,
        "Carotenoid; EFSA caution at very high supplemental intakes, not at food-additive use.", "EFSA 2012;10(3):2593."),
    AdditiveInfo("E160c", listOf("paprika (colorant)", "extrait de paprika", "oléorésine de paprika"), AdditiveTier.THREE, AdditiveCategory.COLORANT,
        "Natural colorant; cosmetic-processing signal.", "EFSA 2015;13(12):4320."),
    AdditiveInfo("E163", listOf("anthocyanes", "anthocyanins", "extrait de peau de raisin"), AdditiveTier.THREE, AdditiveCategory.COLORANT,
        "Anthocyanin colorants; generally safe.", "EFSA 2013;11(4):3145."),
    AdditiveInfo("E170", listOf("carbonate de calcium", "calcium carbonate"), AdditiveTier.THREE, AdditiveCategory.COLORANT,
        "White colorant + acidity regulator + calcium carrier; no concern.", "EFSA 2011;9(7):2318."),
    AdditiveInfo("E101", listOf("riboflavine", "riboflavin", "vitamine b2"), AdditiveTier.THREE, AdditiveCategory.COLORANT,
        "Vitamin B2 colorant; tier reflects cosmetic-processing signal.", "EFSA 2013;11(10):3357."),
    AdditiveInfo("E150a", listOf("e150a", "caramel ordinaire", "plain caramel"), AdditiveTier.THREE, AdditiveCategory.COLORANT,
        "Plain caramel; no 4-MEI concern.", "EFSA 2011;9(3):2004."),
    AdditiveInfo("E150b", listOf("e150b", "caramel caustique", "caustic-sulfite caramel"), AdditiveTier.THREE, AdditiveCategory.COLORANT,
        "Caustic-sulfite caramel; no 4-MEI concern.", "EFSA 2011;9(3):2004."),
    AdditiveInfo("E260", listOf("acide acétique", "acetic acid"), AdditiveTier.THREE, AdditiveCategory.ACIDULANT,
        "Acetic acid (vinegar); long history of safe use.", "EU authorisation (quantum satis)."),
    AdditiveInfo("E325", listOf("lactate de sodium", "sodium lactate"), AdditiveTier.THREE, AdditiveCategory.PRESERVATIVE,
        "Common in deli meats; authorised without ADI.", "EU Regulation 1333/2008 Annex II."),
    AdditiveInfo("E327", listOf("lactate de calcium", "calcium lactate"), AdditiveTier.THREE, AdditiveCategory.PRESERVATIVE,
        "Acidity regulator + calcium fortifier.", "EU Regulation 1333/2008 Annex II."),
    AdditiveInfo("E331", listOf("citrate de sodium", "sodium citrate"), AdditiveTier.THREE, AdditiveCategory.ACIDITY_REGULATOR,
        "Citrate buffer; no concern.", "EU authorisation without ADI."),
    AdditiveInfo("E332", listOf("citrate de potassium", "potassium citrate"), AdditiveTier.THREE, AdditiveCategory.ACIDULANT,
        "Citrate buffer; no concern.", "EU authorisation without ADI."),
    AdditiveInfo("E333", listOf("citrate de calcium", "calcium citrate"), AdditiveTier.THREE, AdditiveCategory.ACIDITY_REGULATOR,
        "Citrate buffer; no concern.", "EU authorisation without ADI."),
    AdditiveInfo("E460", listOf("cellulose", "cellulose microcristalline", "microcrystalline cellulose"), AdditiveTier.THREE, AdditiveCategory.THICKENER,
        "Plant-fibre derivative; EFSA reaffirmed safety.", "EFSA 2018;16(1):5047."),
    AdditiveInfo("E509", listOf("chlorure de calcium", "calcium chloride"), AdditiveTier.THREE, AdditiveCategory.STABILIZER,
        "Firming agent; no concern.", "EU Regulation 1333/2008 (quantum satis)."),
    AdditiveInfo("E575", listOf("glucono-delta-lactone", "glucono delta lactone", "gdl"), AdditiveTier.THREE, AdditiveCategory.ACIDULANT,
        "Slow-release acidulant used in baked goods.", "EU authorisation without ADI."),
    AdditiveInfo("E422_glycerol", listOf("glycérol", "glycerol", "glycérine"), AdditiveTier.THREE, AdditiveCategory.HUMECTANT,
        "Humectant; no ADI.", "EU authorisation without ADI."),
    AdditiveInfo("E965", listOf("maltitol", "sirop de maltitol"), AdditiveTier.THREE, AdditiveCategory.SWEETENER,
        "Sugar alcohol; moderate glycemic impact; laxative above tolerance.", "EFSA 2011 polyol opinion."),
    AdditiveInfo("E967", listOf("xylitol"), AdditiveTier.THREE, AdditiveCategory.SWEETENER,
        "Sugar alcohol; laxative above ~50 g.", "EFSA 2011 polyol opinion."),
    AdditiveInfo("E1422", listOf("amidon acétylé", "acetylated distarch adipate", "amidon modifié"), AdditiveTier.THREE, AdditiveCategory.THICKENER,
        "Modified starch; authorised without ADI.", "EU Regulation 1333/2008."),
    AdditiveInfo("E1442", listOf("phosphate de distarch hydroxypropyle", "hydroxypropyl distarch phosphate"), AdditiveTier.THREE, AdditiveCategory.THICKENER,
        "Modified starch; authorised without ADI.", "EU Regulation 1333/2008."),
    AdditiveInfo("E307", listOf("alpha-tocophérol", "alpha tocopherol", "tocopherols", "vitamine e (additif)"), AdditiveTier.THREE, AdditiveCategory.ANTIOXIDANT,
        "Vitamin E antioxidant; no concern.", "EFSA 2015;13(9):4247."),
    AdditiveInfo("E901", listOf("cire d'abeille", "beeswax"), AdditiveTier.THREE, AdditiveCategory.GLAZING_AGENT,
        "Glazing agent; not vegan.", "EFSA re-evaluated; no ADI specified."),
    AdditiveInfo("E920", listOf("l-cystéine", "l cysteine", "cystéine", "cysteine"), AdditiveTier.THREE, AdditiveCategory.FLOUR_TREATMENT,
        "Dough conditioner; often animal-derived (relevant for vegetarians/vegans).", "EU Regulation 1333/2008."),

    // ===== TIER 3 (cont.): previously referenced by findAdditive()'s natural-colorant
    // lookup but missing their own entries — the lookup silently returned null for
    // every beetroot-red or carmine ingredient. Also adds other very common E-numbers
    // not previously represented (alginates, gum arabic, sorbitol, chlorophyll, etc.).
    AdditiveInfo("E120", listOf("carmin", "cochenille", "carmine", "cochineal", "acide carminique"), AdditiveTier.THREE, AdditiveCategory.COLORANT,
        "Insect-derived red colorant (Dactylopius coccus); not vegan/vegetarian, rare allergen reports.",
        "EFSA 2015;13(11):4288."),
    AdditiveInfo("E162", listOf("rouge de betterave", "betterave rouge", "betanine", "beetroot red", "betanin"), AdditiveTier.THREE, AdditiveCategory.COLORANT,
        "Natural betalain colorant from beetroot; no concern.", "EU authorisation without ADI."),
    AdditiveInfo("E140", listOf("chlorophylles", "chlorophylle", "chlorophyll", "chlorophylls"), AdditiveTier.THREE, AdditiveCategory.COLORANT,
        "Natural green plant pigment; no concern.", "EU authorisation without ADI."),
    AdditiveInfo("E153", listOf("charbon végétal", "vegetable carbon", "carbon black (vegetal)"), AdditiveTier.THREE, AdditiveCategory.COLORANT,
        "Vegetable-carbon black colorant; EFSA re-evaluation found no safety concern at authorised levels.",
        "EFSA 2012;10(4):2592."),
    AdditiveInfo("E200", listOf("acide sorbique", "sorbic acid"), AdditiveTier.THREE, AdditiveCategory.PRESERVATIVE,
        "Widely used mould/yeast inhibitor; EFSA reaffirmed safety.", "EFSA 2015;13(6):4144."),
    AdditiveInfo("E262", listOf("acétate de sodium", "sodium acetate", "diacétate de sodium", "sodium diacetate"), AdditiveTier.THREE, AdditiveCategory.PRESERVATIVE,
        "Acetate preservative/acidity regulator; no concern.", "EU authorisation without ADI."),
    AdditiveInfo("E290", listOf("dioxyde de carbone", "carbon dioxide", "gaz carbonique"), AdditiveTier.THREE, AdditiveCategory.ACIDITY_REGULATOR,
        "Carbonation gas / packaging gas; no concern.", "EU authorisation (quantum satis)."),
    AdditiveInfo("E401", listOf("alginate de sodium", "sodium alginate"), AdditiveTier.THREE, AdditiveCategory.THICKENER,
        "Seaweed-derived thickener; EFSA confirmed no concern.", "EFSA 2017;15(11):5049 (alginate group)."),
    AdditiveInfo("E406", listOf("agar", "agar-agar"), AdditiveTier.THREE, AdditiveCategory.THICKENER,
        "Seaweed-derived gelling agent; no concern.", "EFSA 2016;14(5):4488."),
    AdditiveInfo("E410", listOf("gomme de caroube", "locust bean gum", "farine de graines de caroube"), AdditiveTier.THREE, AdditiveCategory.THICKENER,
        "Legume-derived thickener; no concern.", "EFSA 2017;15(11):5049."),
    AdditiveInfo("E414", listOf("gomme arabique", "gum arabic", "gomme d'acacia"), AdditiveTier.THREE, AdditiveCategory.THICKENER,
        "Acacia-tree exudate thickener/stabiliser; no concern.", "EFSA 2017;15(4):4741."),
    AdditiveInfo("E420", listOf("sorbitol", "sirop de sorbitol"), AdditiveTier.THREE, AdditiveCategory.SWEETENER,
        "Sugar alcohol; laxative effect above tolerance, moderate glycemic impact.", "EFSA 2011 polyol opinion."),
)

// ============================================================================
// Lookup helpers
// ============================================================================

/** Normalize for matching: lowercase, strip accents, collapse spaces. */
fun normalizeForMatching(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("[\\u0300-\\u036f]"), "")
        .replace(Regex("[^a-z0-9\\s-]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

/**
 * Find additive info from an ingredient E-number or name. Null if unknown.
 *
 * Memoized: ProcessingPillar, AdditiveRiskPillar, and IngredientIntegrityPillar
 * each independently call this for the same ingredient during a single
 * scoreProduct() run, so without a cache the same O(n) linear + synonym-
 * substring scan over ADDITIVES_DB runs up to 3x per ingredient. The cache is
 * local to this function (no call-site or signature changes needed in any
 * pillar) and keyed on the exact 3 inputs, which fully determine the result -
 * a Ingredient-shaped sentinel (NOT_FOUND) distinguishes a cached miss from
 * "not yet looked up" since ConcurrentHashMap can't store null values.
 */
private val NOT_FOUND = AdditiveInfo("", emptyList(), AdditiveTier.THREE, AdditiveCategory.ANTICAKING, "", "")
private val additiveLookupCache = ConcurrentHashMap<Triple<String?, String, IngredientCategory?>, AdditiveInfo>()

fun findAdditive(eNumber: String?, name: String, category: IngredientCategory?): AdditiveInfo? {
    val key = Triple(eNumber, name, category)
    additiveLookupCache[key]?.let { return if (it === NOT_FOUND) null else it }
    val result = computeFindAdditive(eNumber, name, category)
    additiveLookupCache[key] = result ?: NOT_FOUND
    return result
}

private fun computeFindAdditive(eNumber: String?, name: String, category: IngredientCategory?): AdditiveInfo? {
    // Direct E-number match
    if (eNumber != null) {
        val norm = eNumber.uppercase().replace("\\s".toRegex(), "")
        ADDITIVES_DB.find { it.eNumber == norm }?.let { return it }
    }

    // Name-based match
    val normName = normalizeForMatching(name)
    for (additive in ADDITIVES_DB) {
        for (synonym in additive.names) {
            if (normName.contains(normalizeForMatching(synonym))) return additive
        }
    }

    // Context-aware match for natural colorants
    if (category == IngredientCategory.ADDITIVE) {
        val naturalColorants = mapOf(
            "curcuma" to "E100", "curcumin" to "E100",
            "paprika" to "E160c", "betterave" to "E162",
            "carmin" to "E120", "cochenille" to "E120",
            "caramel" to "E150",
        )
        for ((keyword, eNum) in naturalColorants) {
            if (normName.contains(keyword)) {
                ADDITIVES_DB.find { it.eNumber == eNum }?.let { return it }
            }
        }
    }
    return null
}
