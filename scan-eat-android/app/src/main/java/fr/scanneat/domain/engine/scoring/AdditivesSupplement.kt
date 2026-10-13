package fr.scanneat.domain.engine.scoring

// ============================================================================
// ADDITIVES DATABASE — supplement (score-engine audit S6).
// Additives found among the 300 most-scanned French products on Open Food
// Facts (2026-10-13) that the DB did not know at all, so they were neither
// scored nor counted for the NOVA estimate. Tiers follow the S7 rule: NONE =
// EFSA re-evaluation found no safety concern; THREE = authorised with an ADI
// or a process-marker role, no specific concern.
// ============================================================================

internal val ADDITIVES_SUPPLEMENT: List<AdditiveInfo> = listOf(
    AdditiveInfo("E301", listOf("ascorbate de sodium", "sodium ascorbate"), AdditiveTier.NONE, AdditiveCategory.ANTIOXIDANT,
        "Sodium salt of vitamin C.", "EFSA re-evaluation of ascorbic acid and its salts (2015): no safety concern."),
    AdditiveInfo("E392", listOf("extrait de romarin", "extraits de romarin", "rosemary extract"), AdditiveTier.NONE, AdditiveCategory.ANTIOXIDANT,
        "Natural antioxidant from rosemary.", "EFSA opinion on rosemary extracts (2008): ADI set, no safety concern at authorised uses."),
    AdditiveInfo("E336", listOf("tartrate de potassium", "tartrates de potassium", "potassium tartrate", "crème de tartre"), AdditiveTier.NONE, AdditiveCategory.ACIDITY_REGULATOR,
        "Tartaric acid salt (cream of tartar).", "EFSA re-evaluation of L(+)-tartaric acid and tartrates (2020)."),
    AdditiveInfo("E501", listOf("carbonate de potassium", "carbonates de potassium", "potassium carbonate"), AdditiveTier.NONE, AdditiveCategory.RAISING_AGENT,
        "Raising agent / acidity regulator.", "Authorised quantum satis, Reg. (EC) 1333/2008, Annex II."),
    AdditiveInfo("E525", listOf("hydroxyde de potassium", "potassium hydroxide"), AdditiveTier.NONE, AdditiveCategory.ACIDITY_REGULATOR,
        "Acidity regulator (cocoa alkalisation).", "Authorised quantum satis, Reg. (EC) 1333/2008, Annex II."),
    AdditiveInfo("E404", listOf("alginate de calcium", "calcium alginate"), AdditiveTier.NONE, AdditiveCategory.THICKENER,
        "Seaweed-derived thickener.", "EFSA re-evaluation of alginic acid and alginates (2017): no need for a numerical ADI."),
    AdditiveInfo("E418", listOf("gomme gellane", "gellan gum"), AdditiveTier.NONE, AdditiveCategory.THICKENER,
        "Fermentation-derived gelling agent.", "EFSA re-evaluation of gellan gum (2018): no need for a numerical ADI."),
    AdditiveInfo("E461", listOf("méthylcellulose", "methylcellulose", "methyl cellulose"), AdditiveTier.THREE, AdditiveCategory.THICKENER,
        "Modified cellulose; typical of ultra-processed texture (meat analogues).", "EFSA re-evaluation of celluloses (2018): no need for a numerical ADI."),
    AdditiveInfo("E476", listOf("polyricinoléate de polyglycérol", "polyglycerol polyricinoleate", "pgpr"), AdditiveTier.THREE, AdditiveCategory.EMULSIFIER,
        "Emulsifier used to cut cocoa butter in chocolate; ADI set.", "EFSA re-evaluation of polyglycerol polyricinoleate (2017)."),
    AdditiveInfo("E481", listOf("stéaroyl-2-lactylate de sodium", "sodium stearoyl-2-lactylate", "stearoyl lactylate de sodium"), AdditiveTier.THREE, AdditiveCategory.EMULSIFIER,
        "Bakery emulsifier; ADI set.", "EFSA re-evaluation of sodium and calcium stearoyl-2-lactylate (2013)."),
    AdditiveInfo("E482", listOf("stéaroyl-2-lactylate de calcium", "calcium stearoyl-2-lactylate"), AdditiveTier.THREE, AdditiveCategory.EMULSIFIER,
        "Bakery emulsifier; ADI set.", "EFSA re-evaluation of sodium and calcium stearoyl-2-lactylate (2013)."),
    AdditiveInfo("E160b", listOf("rocou", "annatto", "bixine", "norbixine"), AdditiveTier.THREE, AdditiveCategory.COLORANT,
        "Natural colour (annatto); ADIs set for bixin and norbixin.", "EFSA opinion on annatto extracts (2016)."),
    AdditiveInfo("E14XX", listOf("amidon modifié", "amidons modifiés", "modified starch"), AdditiveTier.THREE, AdditiveCategory.THICKENER,
        "Modified starch, number not specified (E1404-E1452); NOVA ultra-processing marker.", "EFSA re-evaluation of modified starches (2017): no need for a numerical ADI."),
)
