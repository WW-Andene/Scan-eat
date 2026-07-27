package fr.scanneat.domain.engine.scoring

// ============================================================================
// ADDITIVES DATABASE — Tier 3 (minor/contextual): common bread/bakery +
// packaging-gas E-numbers that were previously unrepresented despite
// appearing on a large share of scanned bakery and packaged-meat products
// (mould-inhibitor propionates, raising-agent carbonates, inert packaging
// gases). Split out of AdditivesDb.kt; concatenated into ADDITIVES_DB there.
// ============================================================================

internal val ADDITIVES_TIER3_BAKERY_AND_GASES: List<AdditiveInfo> = listOf(
    AdditiveInfo("E280", listOf("acide propionique", "propionic acid"), AdditiveTier.THREE, AdditiveCategory.PRESERVATIVE,
        "Mould/rope-spoilage inhibitor, common in packaged bread; EFSA reaffirmed safety within group ADI.",
        "EFSA 2019;17(1):5658 (propionic acid–propionates group)."),
    AdditiveInfo("E281", listOf("propionate de sodium", "sodium propionate"), AdditiveTier.THREE, AdditiveCategory.PRESERVATIVE,
        "Mould inhibitor, same group as E280.", "EFSA 2019;17(1):5658."),
    AdditiveInfo("E282", listOf("propionate de calcium", "calcium propionate"), AdditiveTier.THREE, AdditiveCategory.PRESERVATIVE,
        "Mould inhibitor, most common propionate in sliced bread.", "EFSA 2019;17(1):5658."),
    AdditiveInfo("E283", listOf("propionate de potassium", "potassium propionate"), AdditiveTier.THREE, AdditiveCategory.PRESERVATIVE,
        "Mould inhibitor, same group as E280.", "EFSA 2019;17(1):5658."),
    AdditiveInfo("E503", listOf("carbonate d'ammonium", "ammonium carbonate", "bicarbonate d'ammonium", "ammonium bicarbonate"), AdditiveTier.THREE, AdditiveCategory.RAISING_AGENT,
        "Raising agent (baker's ammonia); authorised without ADI.", "EU Regulation 1333/2008 (quantum satis)."),
    AdditiveInfo("E304", listOf("palmitate d'ascorbyle", "ascorbyl palmitate"), AdditiveTier.THREE, AdditiveCategory.ANTIOXIDANT,
        "Fat-soluble vitamin C ester antioxidant; no concern.", "EFSA 2015 (ascorbic acid family)."),
    AdditiveInfo("E306", listOf("extrait riche en tocophérol", "tocopherol-rich extract", "vitamine e naturelle"), AdditiveTier.THREE, AdditiveCategory.ANTIOXIDANT,
        "Natural-source vitamin E antioxidant; no concern.", "EFSA 2015;13(9):4247."),
    AdditiveInfo("E938", listOf("argon"), AdditiveTier.THREE, AdditiveCategory.PACKAGING_GAS,
        "Inert packaging gas; no concern.", "EU authorisation (quantum satis)."),
    AdditiveInfo("E941", listOf("azote", "nitrogen"), AdditiveTier.THREE, AdditiveCategory.PACKAGING_GAS,
        "Inert packaging gas, common in modified-atmosphere packaging; no concern.", "EU authorisation (quantum satis)."),
    AdditiveInfo("E942", listOf("protoxyde d'azote", "nitrous oxide"), AdditiveTier.THREE, AdditiveCategory.PACKAGING_GAS,
        "Propellant gas for whipped/aerosol products; no dietary concern at food-additive use.", "EFSA 2016;14(5):4491."),
)
