package fr.scanneat.domain.engine.scoring

// ============================================================================
// ADDITIVES DATABASE — Tier 1 (serious concern) entries.
// Split out of AdditivesDb.kt; concatenated into ADDITIVES_DB there.
// Source citations preserved verbatim from the original TS engine.
// ============================================================================

internal val ADDITIVES_TIER1: List<AdditiveInfo> = listOf(
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
    // Moved from Tier 2 — previously grouped with generic "moderate concern"
    // colorants despite the FDA banning it outright in January 2025 under the
    // Delaney Clause specifically for carcinogenicity (thyroid tumors in rat
    // studies), the same regulatory severity class as E171's EU ban above.
    AdditiveInfo("E127", listOf("érythrosine", "erythrosine", "red 3", "rouge 3"), AdditiveTier.ONE, AdditiveCategory.COLORANT,
        "Iodine-containing dye. Banned as a food additive in the US (FDA, Jan 2025) under the Delaney Clause for carcinogenicity (thyroid tumors in male rats); thyroid concerns at high intakes generally.",
        "FDA final rule, 90 FR 4695 (Jan 2025); EFSA Re-evaluation 2011;9(1):1854."),
    // Previously absent from all three tiers entirely - products containing
    // either scored as if additive-free.
    AdditiveInfo("E924", listOf("bromate de potassium", "potassium bromate"), AdditiveTier.ONE, AdditiveCategory.STABILIZER,
        "Flour treatment agent. IARC Group 2B (possibly carcinogenic); banned as a food additive in the EU, UK, Canada, and most other markets.",
        "IARC Monograph Vol 73 (1999); EU Regulation 1333/2008 (not authorised)."),
    AdditiveInfo("E216", listOf("propylparabène", "propylparaben", "e216"), AdditiveTier.ONE, AdditiveCategory.PRESERVATIVE,
        "Paraben preservative. Endocrine-disruption concerns (estrogenic activity); banned as a food additive in the EU since 2006.",
        "EFSA Opinion 2004;83; Commission Regulation (EC) No 1004/2006."),
    AdditiveInfo("E217", listOf("propylparabène de sodium", "sodium propylparaben", "e217"), AdditiveTier.ONE, AdditiveCategory.PRESERVATIVE,
        "Sodium salt of E216, same endocrine-disruption concerns; banned as a food additive in the EU since 2006.",
        "EFSA Opinion 2004;83; Commission Regulation (EC) No 1004/2006."),
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
    // Previously absent from the DB entirely despite being referenced by name
    // in AdditiveRiskPillar.kt's SULFITE_E_NUMBERS set - a wine/cider using
    // any of these five instead of E220/E221/E223/E224 was invisible to
    // findAdditive() and scored as if additive-free.
    AdditiveInfo("E222", listOf("bisulfite de sodium", "sodium bisulfite", "sodium hydrogen sulfite"), AdditiveTier.ONE, AdditiveCategory.PRESERVATIVE,
        "Sulfite — mandatory EU allergen.",
        "EU Regulation 1169/2011 Annex II; EFSA Re-evaluation 2016."),
    AdditiveInfo("E225", listOf("sulfite de potassium", "potassium sulfite"), AdditiveTier.ONE, AdditiveCategory.PRESERVATIVE,
        "Sulfite — mandatory EU allergen.",
        "EU Regulation 1169/2011 Annex II; EFSA Re-evaluation 2016."),
    AdditiveInfo("E226", listOf("sulfite de calcium", "calcium sulfite"), AdditiveTier.ONE, AdditiveCategory.PRESERVATIVE,
        "Sulfite — mandatory EU allergen.",
        "EU Regulation 1169/2011 Annex II; EFSA Re-evaluation 2016."),
    AdditiveInfo("E227", listOf("bisulfite de calcium", "calcium bisulfite", "calcium hydrogen sulfite"), AdditiveTier.ONE, AdditiveCategory.PRESERVATIVE,
        "Sulfite — mandatory EU allergen.",
        "EU Regulation 1169/2011 Annex II; EFSA Re-evaluation 2016."),
    AdditiveInfo("E228", listOf("bisulfite de potassium", "potassium bisulfite", "potassium hydrogen sulfite"), AdditiveTier.ONE, AdditiveCategory.PRESERVATIVE,
        "Sulfite — mandatory EU allergen.",
        "EU Regulation 1169/2011 Annex II; EFSA Re-evaluation 2016."),
    AdditiveInfo("E385", listOf("edta", "calcium disodium edta"), AdditiveTier.ONE, AdditiveCategory.SEQUESTRANT,
        "Metal chelator. High chronic intake can affect mineral bioavailability.",
        "EFSA Scientific Opinion 2018;16(11):5007 (ADI 1.9 mg/kg bw/day)."),
)
