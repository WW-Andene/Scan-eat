package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*
import java.util.Locale

// ============================================================================
// DIET CHECKER — port of public/core/diets.js
//
// AUTHORITATIVE sources (preserved from original):
//   Vegan/Vegetarian: Vegan Society UK / Vegetarian Society UK definitions
//   Ketogenic: Volek & Phinney clinical ketosis; net carbs ≤10 g/100 g
//   Halal: Qur'anic prohibition (haram 5:3, 5:90)
//   Kosher: Torah (Leviticus 11); certification required for full compliance
//   Gluten-free: EU Commission Regulation (EC) 41/2009 ≤20 mg/kg gluten
//   Low-FODMAP: Monash University Low FODMAP Diet App reference lists
//
// EDITORIAL: detection patterns are heuristic — not a substitute for
// the manufacturer's own allergen/diet declaration.
// ============================================================================

enum class DietKey(val key: String, val labelFr: String, val labelEn: String) {
    NONE("none", "Aucun (score classique)", "None (classic only)"),
    VEGETARIAN("vegetarian", "Végétarien", "Vegetarian"),
    VEGAN("vegan", "Végan", "Vegan"),
    PESCATARIAN("pescatarian", "Pescétarien", "Pescatarian"),
    KETO("keto", "Cétogène", "Ketogenic"),
    HALAL("halal", "Halal", "Halal"),
    KOSHER("kosher", "Casher", "Kosher"),
    GLUTEN_FREE("gluten_free", "Sans gluten", "Gluten-free"),
    DAIRY_FREE("dairy_free", "Sans lactose", "Dairy-free"),
    PALEO("paleo", "Paléo", "Paleo"),
    LOW_FODMAP("low_fodmap", "Pauvre en FODMAP", "Low-FODMAP"),
    MEDITERRANEAN("mediterranean", "Méditerranéen", "Mediterranean"),
    CARNIVORE("carnivore", "Carnivore", "Carnivore");

    companion object {
        fun fromKey(k: String) = entries.firstOrNull { it.key == k } ?: NONE
    }
}

data class DietResult(
    val compliant: Boolean,
    val violations: List<String>,
    val preferredHits: List<String>,
    val certified: Boolean,
    val reason: String?,
)

// ============================================================================
// Word-boundary helper matching the JS b() helper — negative lookbehind/
// lookahead for Latin-1 accented letters so patterns work on accented FR text.
// ============================================================================

// Digits are excluded from the boundary too, not just letters — otherwise a
// numeric token like "E120" (carmine) matches as a substring inside a longer,
// unrelated E-number such as E1200 (polydextrose) or E1201 (PVP).
private fun b(inner: String): Regex =
    Regex("(?<![a-zà-ÿ0-9])(?:$inner)(?![a-zà-ÿ0-9])", RegexOption.IGNORE_CASE)

// ============================================================================
// Diet definitions
// ============================================================================

private data class DietDef(
    val forbidden: List<Regex> = emptyList(),
    val preferred: List<Regex> = emptyList(),
    val noteFr: String = "",
    val noteEn: String = "",
    // Keto macro constraints
    val maxNetCarbsG: Double? = null,
    val minFatFractionOfKcal: Double? = null,
)

private val DIET_DEFS: Map<DietKey, DietDef> = mapOf(

    DietKey.VEGETARIAN to DietDef(
        forbidden = listOf(
            b("viande|porc|b[oœ]euf|poulet|dinde|canard|agneau|veau|lard|lardon|jambon|saucisse|chorizo|merguez|bacon|boudin|confit|rillette|pat[eé]|pâté|foie gras|cro[uû]te de viande|g[eé]lati?ne(?! halal)|pr[eé]sure animale|collag[eè]ne|pepsine"),
            b("poisson|saumon|thon|cabillaud|sardine|maquereau|anchois|hareng|crustac[eé]|crevette|homard|crabe|hu[iî]tre|moule|calmar|poulpe"),
        ),
        noteFr = "Exclut viande, poisson, crustacés, mollusques, gélatine et présure animale.",
        noteEn = "Excludes meat, fish, shellfish, molluscs, animal gelatin and rennet.",
    ),

    DietKey.VEGAN to DietDef(
        forbidden = listOf(
            b("viande|porc|b[oœ]euf|poulet|dinde|canard|agneau|veau|lard|lardon|jambon|saucisse|chorizo|merguez|bacon|boudin|confit|rillette|pat[eé]|pâté|foie gras|g[eé]lati?ne(?! v[eé]g[eé]tale)|pr[eé]sure animale|collag[eè]ne|pepsine|isinglass|colle de poisson"),
            b("poisson|saumon|thon|cabillaud|sardine|maquereau|anchois|hareng|crustac[eé]|crevette|homard|crabe|hu[iî]tre|moule|calmar|poulpe"),
            b("lait(?! de (coco|soja|amande|avoine|riz))|lactos[eé]rum|petit[- ]lait|cr[eè]me(?! v[eé]g[eé]tale)|beurre(?! de cacahu[eè]te| d'arachide| de coco)|fromage|yaourt|yoghourt|skyr|k[eé]fir|cas[eé]ine|lactalbumine|whey|mati[eè]re grasse laiti[eè]re|poudre de lait|ghee|mascarpone|ricotta|mozzarella|parmesan|emmental"),
            b("oeufs?|œufs?|jaune d'?oeuf|blanc d'?oeuf|ovalbumine|lysozyme|ovomuco[iï]de"),
            b("miel|propolis|gel[eé]e royale|cire d'abeille|beeswax"),
            b("E90[14]"),   // E901 beeswax, E904 shellac
            b("E120"),      // carmine
            b("E542"),      // bone phosphate
            b("E631|E635"), // ribonucleotides — often animal-derived
            b("cochenille|carmin|phosphate osseux|lanoline"),
        ),
        preferred = listOf(
            b("v[eé]gan|v-label|vegan|plant-based|100% v[eé]g[eé]tal"),
        ),
        // B12 exists naturally only in animal foods (or fortified plant products the
        // scanner already treats as compliant) - a vegan diet has no other source in
        // scope, so this is a lifestyle-level adequacy note, not a per-product finding.
        noteFr = "Exclut tout produit animal : viande, poisson, œufs, lait, miel, gélatine, cire d'abeille E901, shellac E904, carmin E120, phosphate osseux E542. Pensez à une supplémentation en vitamine B12, absente du régime végan.",
        noteEn = "Excludes any animal product: meat, fish, eggs, dairy, honey, gelatin, beeswax E901, shellac E904, carmine E120, bone phosphate E542. Consider B12 supplementation — a vegan diet has no other reliable source.",
    ),

    DietKey.PESCATARIAN to DietDef(
        forbidden = listOf(
            b("viande|porc|b[oœ]euf|poulet|dinde|canard|agneau|veau|lard|lardon|jambon|saucisse|chorizo|merguez|bacon|boudin|confit|rillette|pat[eé]|pâté|foie gras"),
        ),
        noteFr = "Végétarien autorisant poisson et fruits de mer.",
        noteEn = "Vegetarian that still allows fish and seafood.",
    ),

    DietKey.KETO to DietDef(
        maxNetCarbsG          = 10.0,
        minFatFractionOfKcal  = 0.60,
        noteFr = "Très faible en glucides (<10 g nets/100 g), riche en lipides (>60 %E).",
        noteEn = "Very low carb (<10 g net carbs/100 g), high fat (>60 %E).",
    ),

    DietKey.HALAL to DietDef(
        forbidden = listOf(
            b("porc|cochon|lard|lardon|jambon|saucisson|bacon|salami|chorizo|pepperoni|couenne|boudin|saindoux|suif|andouille|pancetta|mortadelle|guanciale"),
            b("graisse animale(?! halal)|mati[eè]re grasse animale(?! halal)"),
            b("alcool|[eé]thanol|vin(?! blanc de cuisson sans alcool)|bi[eè]re|biere|liqueur|rhum|whisky|whiskey|gin|vodka|spiritueux|kirsch|cognac|armagnac|calvados|porto|champagne|eau[- ]de[- ]vie"),
            b("g[eé]lati?ne(?!\\s+(halal|v[eé]g[eé]tale|v[eé]g|de poisson))"),
            b("pr[eé]sure animale(?! halal)|pepsine(?! halal)"),
            b("mono[- ]et diglyc[eé]rides(?! d[''\\s]origine v[eé]g[eé]tale)"),
            b("cochenille|carmin|shellac|gomme[- ]laque"),
        ),
        preferred = listOf(
            b("halal|certifi[eé] halal|AVS|AFCAI|ARGML|HMC|halal[- ]certified"),
        ),
        noteFr = "Pas de porc ni dérivés, pas d'alcool, gélatine/présure doivent être halal ou végétales. Certification AVS/AFCAI/HMC donne un bonus.",
        noteEn = "No pork or derivatives, no alcohol; gelatin/rennet must be halal or vegetal. Recognized certification earns a bonus.",
    ),

    DietKey.KOSHER to DietDef(
        forbidden = listOf(
            b("porc|cochon|lard|lardon|jambon|saucisson|bacon|salami|chorizo|pepperoni|saindoux|suif|pancetta|couenne"),
            b("crustac[eé]|crevette|homard|crabe|langouste|langoustine|[eé]crevisse|hu[iî]tre|moule|calmar|encornet|poulpe|p[eé]toncle|palourde|bigorneau|bulot"),
            b("lapin|li[eè]vre|cheval|sanglier|chameau|autruche"),
            b("anguille|requin|esturgeon"),
        ),
        preferred = listOf(
            b("casher|kasher|kosher|\\bOU\\b|\\bOK\\b|Star-K|KOF-K|COR"),
        ),
        noteFr = "Pas de porc, crustacés, mollusques, poissons sans écailles, viande non ruminante. Certification OU/OK/Star-K donne un bonus.",
        noteEn = "No pork, shellfish, molluscs, scaleless fish, non-ruminant meat. Certification OU/OK/Star-K earns a bonus.",
    ),

    DietKey.GLUTEN_FREE to DietDef(
        forbidden = listOf(
            b("gluten|bl[eé]|froment|seigle|orge|avoine(?! sans gluten)|[eé]peautre|kamut|triticale|couscous|boulgour|bulgur|chapelure|semoule de bl[eé]|farine de bl[eé]|farine de seigle|farine d[e']orge|malt|malt d'orge"),
        ),
        noteFr = "Pas de blé, seigle, orge, avoine (sauf certifiée), épeautre, kamut, triticale. Règl. (CE) 41/2009 ≤20 mg/kg.",
        noteEn = "No wheat, rye, barley, oats (unless certified), spelt, kamut, triticale. EC Regulation 41/2009 ≤20 mg/kg gluten.",
    ),

    DietKey.DAIRY_FREE to DietDef(
        forbidden = listOf(
            b("lait(?! de (coco|soja|amande|avoine|riz))|lactos[eé]rum|petit[- ]lait|cr[eè]me(?! de coco| v[eé]g[eé]tale)|beurre(?! de cacahu[eè]te| d'arachide| de coco)|fromage|yaourt|yoghourt|skyr|k[eé]fir|cas[eé]ine|lactose|lactalbumine|whey|mati[eè]re grasse laiti[eè]re|poudre de lait|ghee|mascarpone|ricotta|mozzarella|parmesan|emmental"),
        ),
        noteFr = "Exclut tous produits laitiers.",
        noteEn = "Excludes all dairy.",
    ),

    DietKey.PALEO to DietDef(
        forbidden = listOf(
            b("bl[eé]|froment|seigle|orge|avoine|[eé]peautre|kamut|triticale|ma[iï]s|riz|farine de bl[eé]|farine de seigle|malt"),
            b("lait(?! de (coco|soja|amande|avoine|riz))|lactos[eé]rum|cr[eè]me(?! de coco| v[eé]g[eé]tale)|beurre(?! de cacahu[eè]te| d'arachide| de coco)|fromage|yaourt|cas[eé]ine|lactose|whey"),
            b("haricot|lentille|pois chiche|soja|arachide|cacahu[eè]te|f[eè]ve"),
            b("sucre raffin[eé]|sirop de glucose|sirop de ma[iï]s|maltodextrin"),
        ),
        noteFr = "Pas de céréales, légumineuses, produits laitiers, ni sucres raffinés.",
        noteEn = "No grains, legumes, dairy, or refined sugars.",
    ),

    DietKey.LOW_FODMAP to DietDef(
        forbidden = listOf(
            b("bl[eé]|froment|seigle|orge|oignon|ail|pomme|poire|mangue|past[eè]que|miel|sirop d'agave|sirop de ma[iï]s|fructose|inuline|chicor[eé]e|artichaut|lait(?! sans lactose)|lactose|fromage frais|yaourt|l[eé]gume sec|haricot|lentille|pois chiche|sorbitol|mannitol|xylitol|maltitol"),
        ),
        noteFr = "Référence Monash. Exclut oligosaccharides, disaccharides, monosaccharides et polyols mal absorbés.",
        noteEn = "Monash University reference. Excludes poorly absorbed oligos, disaccharides, monosaccharides and polyols.",
    ),

    DietKey.MEDITERRANEAN to DietDef(
        preferred = listOf(
            b("huile d'olive|olive|poisson|saumon|sardine|maquereau|thon|noix|amande|noisette|pistache|l[eé]gume|tomate|poivron|aubergine|courgette|l[eé]gumineuse|lentille|pois chiche|haricot|c[eé]r[eé]ale compl[eè]te|bl[eé] complet|avoine|feta|yaourt grec"),
        ),
        noteFr = "Priorise olive, poisson, légumes, légumineuses, céréales complètes. Pas d'interdits stricts.",
        noteEn = "Prioritises olive, fish, vegetables, legumes, whole grains. No hard bans.",
    ),

    DietKey.CARNIVORE to DietDef(
        forbidden = listOf(
            b("bl[eé]|farine|sucre|fruit|l[eé]gume|c[eé]r[eé]ale|riz|quinoa|ma[iï]s|haricot|lentille|pois|soja|arachide|huile v[eé]g[eé]tale|huile d'olive"),
        ),
        noteFr = "Exclut tout ingrédient végétal.",
        noteEn = "Excludes any plant ingredient.",
    ),
)

// Diets where a certification mark overrides detected violations
private val CERTIFICATION_OVERRIDE_DIETS = setOf(DietKey.HALAL, DietKey.KOSHER, DietKey.VEGAN, DietKey.GLUTEN_FREE)
private val UNVERIFIABLE_DIETS = setOf(DietKey.HALAL, DietKey.KOSHER)

/**
 * Check a product against a diet definition.
 * Port of checkDiet() from diets.js.
 */
/** The diet's own description/adequacy note, e.g. for display next to a diet picker. */
fun dietNote(dietKey: DietKey, lang: String = "fr"): String? =
    DIET_DEFS[dietKey]?.let { if (lang == "en") it.noteEn else it.noteFr }

fun checkDiet(product: Product, dietKey: DietKey, lang: String = "fr"): DietResult {
    if (dietKey == DietKey.NONE) {
        return DietResult(true, emptyList(), emptyList(), false, null)
    }

    val def = DIET_DEFS[dietKey] ?: return DietResult(true, emptyList(), emptyList(), false, null)

    val haystacks: List<String> = listOf(product.name) + product.ingredients.map { it.name }

    fun testAny(re: Regex): String? = haystacks.firstOrNull { re.containsMatchIn(it) }

    val violations    = mutableListOf<String>()
    val preferredHits = mutableListOf<String>()

    for (re in def.forbidden) {
        testAny(re)?.let { violations += it }
    }
    for (re in def.preferred) {
        testAny(re)?.let { preferredHits += it }
    }

    // Keto: macro-based check
    if (dietKey == DietKey.KETO) {
        val netCarbs = (product.nutrition.carbsG - product.nutrition.fiberG).coerceAtLeast(0.0)
        val maxNet   = def.maxNetCarbsG ?: 10.0
        if (netCarbs > maxNet) {
            // %.1f with no explicit Locale renders "15,0" on comma-decimal devices,
            // which then glued onto an English/French unit suffix looked mixed-up
            // twice over - Locale.US keeps the number format independent of that.
            val netCarbsStr = String.format(Locale.US, "%.1f", netCarbs)
            violations += if (lang == "en") "$netCarbsStr g net carbs/100 g" else "$netCarbsStr g glucides nets/100 g"
        }

        val kcal    = product.nutrition.energyKcal
        val fatKcal = product.nutrition.fatG * 9.0
        val fatFrac = if (kcal > 0) fatKcal / kcal else 0.0
        val minFat  = def.minFatFractionOfKcal ?: 0.60
        if (kcal > 50 && fatFrac < minFat) {
            val fatPct = (fatFrac * 100).toInt()
            violations += if (lang == "en") "only $fatPct% from fat" else "seulement $fatPct % de lipides"
        }
    }

    val certified              = preferredHits.isNotEmpty()
    val certificationOverride  = certified && dietKey in CERTIFICATION_OVERRIDE_DIETS
    val compliant              = certificationOverride || violations.isEmpty()
    val effectiveViolations    = if (certificationOverride) emptyList() else violations

    val reason = if (compliant) null else {
        val unverifiableNote = if (dietKey in UNVERIFIABLE_DIETS) {
            val note = if (lang == "en") def.noteEn else def.noteFr
            " — Note: $note"
        } else ""
        val labelStr = if (lang == "en") dietKey.labelEn else dietKey.labelFr
        val prefix   = if (lang == "en") "Not" else "Non"
        "$prefix $labelStr : ${effectiveViolations.take(3).joinToString()}$unverifiableNote"
    }

    return DietResult(
        compliant      = compliant,
        violations     = effectiveViolations,
        preferredHits  = preferredHits,
        certified      = certified,
        reason         = reason,
    )
}
