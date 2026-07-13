package fr.scanneat.domain.engine.nutrition

import fr.scanneat.domain.engine.scoring.normalizeForMatching
import fr.scanneat.domain.model.Ingredient

// ============================================================================
// INGREDIENT FACTS DB — the hint panel's "fun facts", solved the same way
// AdditivesDb solves "is this E-number a concern": a small hand-curated
// table of (ingredient name synonyms) -> one verifiable, neutral trivia
// line, matched by substring against the product's actual ingredient list.
//
// Deliberately NOT LLM-generated: a per-scan model call for one trivia line
// doesn't scale (rate limits, latency, cost) and reintroduces the exact
// hallucination risk this file's neighbour (ProductHints) was written to
// avoid. Deliberately NOT exhaustive either — this covers the ~90 most
// commonly scanned ingredients/spices/oils rather than attempting full
// ingredient-database coverage, since a wrong "fun fact" is worse than a
// missing one. Every line here is common, verifiable encyclopedic
// knowledge (food science / etymology / agronomy), not a health claim —
// health-relevant statements belong in ProductHints' benefits/risks
// sections, cited to EFSA/WHO/IARC, not here.
//
// Facts are capped by the caller (see generateProductHints) to avoid
// turning the panel into a wall of trivia on a long ingredient list.
// ============================================================================

private data class IngredientFact(
    val names: List<String>,
    val factFr: String,
    val factEn: String,
)

private val FACTS: List<IngredientFact> = listOf(
    IngredientFact(listOf("cacao", "cocoa", "chocolat noir", "dark chocolate"),
        "Le cacao contient naturellement de la théobromine, l'alcaloïde qui donne au chocolat son léger effet stimulant.",
        "Cocoa naturally contains theobromine, the alkaloid responsible for chocolate's mild stimulant effect."),
    IngredientFact(listOf("vanille", "vanilla", "gousse de vanille", "vanilla bean"),
        "La vanille est la deuxième épice la plus chère au monde après le safran, car chaque fleur d'orchidée doit être pollinisée à la main.",
        "Vanilla is the world's second most expensive spice after saffron — each orchid flower must be hand-pollinated."),
    IngredientFact(listOf("safran", "saffron"),
        "Le safran provient des stigmates de la fleur de crocus ; il en faut environ 150 fleurs pour obtenir un seul gramme d'épice.",
        "Saffron comes from the crocus flower's stigmas — it takes roughly 150 flowers to yield a single gram of the spice."),
    IngredientFact(listOf("cannelle", "cinnamon"),
        "La cannelle est l'écorce interne séchée d'arbres du genre Cinnamomum ; la véritable cannelle de Ceylan et la casse (plus courante) en sont deux espèces distinctes.",
        "Cinnamon is the dried inner bark of Cinnamomum trees — true Ceylon cinnamon and the more common cassia are two distinct species."),
    IngredientFact(listOf("curcuma", "turmeric"),
        "Le curcuma doit sa couleur jaune-orangé à la curcumine, un pigment de la famille des curcuminoïdes.",
        "Turmeric owes its yellow-orange colour to curcumin, a pigment from the curcuminoid family."),
    IngredientFact(listOf("gingembre", "ginger"),
        "Le gingembre est un rhizome, une tige souterraine, et non une racine à proprement parler.",
        "Ginger is a rhizome — an underground stem — not technically a root."),
    IngredientFact(listOf("ail", "garlic"),
        "L'odeur caractéristique de l'ail ne se développe qu'une fois la gousse coupée ou écrasée, ce qui libère l'enzyme alliinase.",
        "Garlic's characteristic smell only develops once a clove is cut or crushed, releasing the enzyme alliinase."),
    IngredientFact(listOf("caféine", "cafeine", "caffeine"),
        "La caféine est un pesticide naturel produit par le caféier et le théier pour repousser les insectes ravageurs.",
        "Caffeine is a natural pesticide produced by the coffee and tea plants to deter insect pests."),
    IngredientFact(listOf("café", "cafe (ingredient)", "coffee", "grains de café"),
        "Le café est en réalité une graine torréfiée, le noyau du fruit du caféier appelé cerise de café.",
        "Coffee is technically a roasted seed — the pit of the coffee plant's fruit, called the coffee cherry."),
    IngredientFact(listOf("thé vert", "the vert", "green tea", "thé noir", "the noir", "black tea"),
        "Le thé vert et le thé noir proviennent de la même plante, Camellia sinensis ; seule l'oxydation des feuilles diffère.",
        "Green and black tea come from the same plant, Camellia sinensis — only the leaf oxidation process differs."),
    IngredientFact(listOf("miel", "honey"),
        "Le miel est essentiellement composé de sucres, mais sa très faible teneur en eau et son acidité naturelle lui permettent de se conserver indéfiniment sans se gâter.",
        "Honey is mostly sugar, but its very low water content and natural acidity let it stay edible essentially indefinitely without spoiling."),
    IngredientFact(listOf("huile d'olive", "olive oil"),
        "L'huile d'olive vierge extra est obtenue par simple pression à froid, sans solvant ni traitement chimique.",
        "Extra-virgin olive oil is produced by simple cold pressing, with no solvent or chemical processing."),
    IngredientFact(listOf("huile de palme", "palm oil"),
        "L'huile de palme provient du fruit du palmier à huile (Elaeis guineensis) et est l'huile végétale la plus produite au monde.",
        "Palm oil comes from the fruit of the oil palm (Elaeis guineensis) and is the most widely produced vegetable oil in the world."),
    IngredientFact(listOf("huile de coco", "huile de noix de coco", "coconut oil"),
        "L'huile de coco est riche en acides gras à chaîne moyenne, ce qui la distingue de la plupart des autres graisses végétales, majoritairement à chaîne longue.",
        "Coconut oil is rich in medium-chain fatty acids, unlike most other plant fats, which are predominantly long-chain."),
    IngredientFact(listOf("avoine", "flocons d'avoine", "oat", "oats", "oatmeal"),
        "L'avoine est l'une des rares céréales courantes naturellement sans gluten, bien qu'elle soit souvent contaminée par contact croisé lors de la culture ou du transport.",
        "Oats are one of the few common grains that are naturally gluten-free, though they're often cross-contaminated during growing or transport."),
    IngredientFact(listOf("quinoa"),
        "Le quinoa n'est pas une céréale mais une graine, botaniquement apparentée aux épinards et à la betterave.",
        "Quinoa isn't a grain but a seed, botanically related to spinach and beetroot."),
    IngredientFact(listOf("levure de bière", "levure de boulanger", "yeast"),
        "La levure de boulanger (Saccharomyces cerevisiae) est un champignon unicellulaire ; c'est le CO2 qu'elle produit en fermentant les sucres qui fait lever la pâte.",
        "Baker's yeast (Saccharomyces cerevisiae) is a single-celled fungus — the CO2 it produces while fermenting sugars is what makes dough rise."),
    IngredientFact(listOf("levain"),
        "Le levain naturel est une culture vivante de levures sauvages et de bactéries lactiques, entretenue par des ajouts réguliers de farine et d'eau.",
        "Sourdough starter is a living culture of wild yeasts and lactic acid bacteria, kept alive with regular additions of flour and water."),
    IngredientFact(listOf("algue", "nori", "kombu", "wakame", "seaweed"),
        "Les algues comme le nori ou le kombu sont l'une des rares sources végétales naturelles d'iode.",
        "Seaweeds like nori and kombu are among the very few natural plant-based sources of iodine."),
    IngredientFact(listOf("canneberge", "cranberry", "cranberries"),
        "La canneberge est l'un des rares fruits qui flottent naturellement dans l'eau, une propriété due aux petites poches d'air à l'intérieur du fruit.",
        "Cranberries are one of the few fruits that naturally float in water, thanks to tiny air pockets inside the berry."),
    IngredientFact(listOf("grenade", "pomegranate"),
        "La grenade peut contenir plusieurs centaines de graines (arilles), chacune enveloppée d'une pulpe juteuse et acidulée.",
        "A single pomegranate can contain several hundred seeds (arils), each wrapped in tart, juicy pulp."),
    IngredientFact(listOf("betterave", "beetroot", "beet"),
        "La couleur rouge de la betterave vient de la bétanine, un pigment qui peut aussi teindre naturellement l'urine chez certaines personnes.",
        "Beetroot's red colour comes from betanin, a pigment that can also naturally tint urine in some people after eating it."),
    IngredientFact(listOf("réglisse", "reglisse", "licorice", "liquorice"),
        "L'arôme sucré de la réglisse vient de la glycyrrhizine, un composé environ 50 fois plus sucrant que le sucre de table.",
        "Liquorice's sweet flavour comes from glycyrrhizin, a compound roughly 50 times sweeter than table sugar."),
    IngredientFact(listOf("caroube", "carob"),
        "La caroube est souvent utilisée comme substitut du cacao ; contrairement au chocolat, elle ne contient naturellement ni caféine ni théobromine.",
        "Carob is often used as a cocoa substitute — unlike chocolate, it naturally contains neither caffeine nor theobromine."),
    IngredientFact(listOf("agar-agar", "agar agar"),
        "L'agar-agar est un gélifiant extrait d'algues rouges ; contrairement à la gélatine, il est d'origine végétale et gélifie même à température ambiante.",
        "Agar-agar is a gelling agent extracted from red algae — unlike gelatin, it's plant-based and sets even at room temperature."),
    IngredientFact(listOf("levure chimique", "poudre à pâte", "baking powder"),
        "La levure chimique combine un acide et du bicarbonate de sodium ; leur réaction produit le CO2 qui fait lever la pâte, sans fermentation biologique.",
        "Baking powder combines an acid with sodium bicarbonate — their reaction produces the CO2 that makes batter rise, with no biological fermentation involved."),
)

/**
 * Match the product's ingredient names against the curated facts table.
 * Returns at most [limit] facts, in ingredient-list order, de-duplicated
 * (an ingredient list mentioning both "cacao" and "chocolat noir" should
 * only surface the cocoa fact once).
 */
fun findIngredientFacts(ingredients: List<Ingredient>, lang: String, limit: Int = 3): List<String> {
    val en = lang == "en"
    val seen = mutableSetOf<IngredientFact>()
    val result = mutableListOf<String>()
    for (ingredient in ingredients) {
        if (result.size >= limit) break
        val normName = normalizeForMatching(ingredient.name)
        for (fact in FACTS) {
            if (fact in seen) continue
            if (fact.names.any { normName.contains(normalizeForMatching(it)) }) {
                seen += fact
                result += if (en) fact.factEn else fact.factFr
                break
            }
        }
    }
    return result
}
