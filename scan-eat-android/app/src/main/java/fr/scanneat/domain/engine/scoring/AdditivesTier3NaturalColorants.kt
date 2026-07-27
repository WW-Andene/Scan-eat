package fr.scanneat.domain.engine.scoring

// ============================================================================
// ADDITIVES DATABASE — Tier 3 (minor/contextual): natural colorants and
// other common thickeners/preservatives previously referenced by
// findAdditive()'s natural-colorant lookup but missing their own entries —
// the lookup silently returned null for every beetroot-red or carmine
// ingredient. Also adds other very common E-numbers not previously
// represented (alginates, gum arabic, sorbitol, chlorophyll, etc.).
// Split out of AdditivesDb.kt; concatenated into ADDITIVES_DB there.
// ============================================================================

internal val ADDITIVES_TIER3_NATURAL_COLORANTS: List<AdditiveInfo> = listOf(
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
