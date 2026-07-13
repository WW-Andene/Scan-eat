package fr.scanneat.domain.engine.nutrition

// ============================================================================
// OFFICIAL RECIPE DATABASE
//
// PROVENANCE:
//   Every ingredient's nutrition value is looked up from FOOD_DB (see
//   FoodDb.kt), which is itself CIQUAL 2020 (ANSES) — the official French
//   food composition database. Nothing here is a fabricated nutrition
//   figure: each recipe's totals are computed by summing real, sourced
//   per-100g values × the listed gram amount.
//
//   The composition of each meal (portion split between vegetables,
//   starches and protein) follows the "assiette-type" model published by
//   Santé publique France / PNNS 2019 adult dietary guidelines
//   (mangerbouger.fr — "les recommandations adultes: alimentation, activité
//   physique et sédentarité"): roughly half the plate as vegetables, a
//   quarter as starches/whole grains, a quarter as protein, completed with
//   a dairy product and/or a piece of fruit. That portion-split principle
//   is the officially-sourced part; the specific ingredient pairings below
//   are ordinary, common combinations built to fit it — not individually
//   endorsed dishes from any external recipe catalog.
// ============================================================================

data class OfficialRecipeIngredient(val foodName: String, val grams: Double)

data class OfficialRecipe(
    val nameFr: String,
    val nameEn: String,
    val ingredients: List<OfficialRecipeIngredient>,
    val mealType: String, // "breakfast" | "lunch_dinner" | "snack"
) {
    private fun lookup(foodName: String): FoodEntry? =
        FOOD_DB.firstOrNull { it.name.equals(foodName, ignoreCase = true) }

    val totalGrams: Double get() = ingredients.sumOf { it.grams }

    private fun sum(select: (FoodEntry) -> Double): Double =
        ingredients.sumOf { ing -> lookup(ing.foodName)?.let { select(it) * ing.grams / 100.0 } ?: 0.0 }

    val totalKcal: Double get() = sum { it.kcal }
    val totalProteinG: Double get() = sum { it.proteinG }
    val totalCarbsG: Double get() = sum { it.carbsG }
    val totalFatG: Double get() = sum { it.fatG }
    val totalFiberG: Double get() = sum { it.fiberG }
}

/**
 * ~half vegetables, ~quarter starch, ~quarter protein per Santé publique
 * France's assiette-type — see file header for the citation.
 */
val OFFICIAL_RECIPE_DB: List<OfficialRecipe> = listOf(
    OfficialRecipe(
        "Poulet, riz et brocoli", "Chicken, rice and broccoli",
        listOf(
            OfficialRecipeIngredient("poulet rôti", 120.0),
            OfficialRecipeIngredient("riz blanc cuit", 150.0),
            OfficialRecipeIngredient("brocoli", 200.0),
            OfficialRecipeIngredient("huile d'olive", 5.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Saumon, quinoa et épinards", "Salmon, quinoa and spinach",
        listOf(
            OfficialRecipeIngredient("saumon", 120.0),
            OfficialRecipeIngredient("quinoa cuit", 150.0),
            OfficialRecipeIngredient("épinard", 180.0),
            OfficialRecipeIngredient("huile d'olive", 5.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Lentilles, carottes et pommes de terre", "Lentils, carrots and potatoes",
        listOf(
            OfficialRecipeIngredient("lentille cuite", 180.0),
            OfficialRecipeIngredient("carotte", 150.0),
            OfficialRecipeIngredient("pomme de terre", 120.0),
            OfficialRecipeIngredient("huile d'olive", 5.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Boeuf haché, pâtes et courgette", "Ground beef, pasta and zucchini",
        listOf(
            OfficialRecipeIngredient("boeuf haché 5%", 120.0),
            OfficialRecipeIngredient("pâtes cuites", 150.0),
            OfficialRecipeIngredient("courgette", 200.0),
            OfficialRecipeIngredient("huile d'olive", 5.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Pois chiches, poivron et riz", "Chickpeas, pepper and rice",
        listOf(
            OfficialRecipeIngredient("pois chiche cuit", 180.0),
            OfficialRecipeIngredient("poivron", 150.0),
            OfficialRecipeIngredient("riz blanc cuit", 120.0),
            OfficialRecipeIngredient("huile d'olive", 5.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Thon, salade verte et tomate", "Tuna, green salad and tomato",
        listOf(
            OfficialRecipeIngredient("thon", 100.0),
            OfficialRecipeIngredient("salade verte", 100.0),
            OfficialRecipeIngredient("tomate", 150.0),
            OfficialRecipeIngredient("pomme de terre", 100.0),
            OfficialRecipeIngredient("huile d'olive", 5.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Omelette, épinards et pain complet", "Omelette, spinach and wholegrain bread",
        listOf(
            OfficialRecipeIngredient("oeuf", 100.0),
            OfficialRecipeIngredient("épinard", 150.0),
            OfficialRecipeIngredient("pain complet", 60.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Avoine, yaourt et fruits rouges", "Oats, yogurt and berries",
        listOf(
            OfficialRecipeIngredient("avoine", 60.0),
            OfficialRecipeIngredient("lait demi-écrémé", 200.0),
            OfficialRecipeIngredient("yaourt nature", 125.0),
            OfficialRecipeIngredient("myrtille", 80.0),
        ),
        "breakfast",
    ),
    OfficialRecipe(
        "Pain complet, oeuf et fromage blanc", "Wholegrain bread, egg and fromage blanc",
        listOf(
            OfficialRecipeIngredient("pain complet", 60.0),
            OfficialRecipeIngredient("oeuf", 50.0),
            OfficialRecipeIngredient("fromage blanc 0%", 100.0),
            OfficialRecipeIngredient("orange", 130.0),
        ),
        "breakfast",
    ),
    OfficialRecipe(
        "Skyr, avoine et pomme", "Skyr, oats and apple",
        listOf(
            OfficialRecipeIngredient("skyr", 150.0),
            OfficialRecipeIngredient("avoine", 40.0),
            OfficialRecipeIngredient("pomme", 130.0),
            OfficialRecipeIngredient("amandes", 15.0),
        ),
        "breakfast",
    ),
    OfficialRecipe(
        "Yaourt, noix et fruits", "Yogurt, walnuts and fruit",
        listOf(
            OfficialRecipeIngredient("yaourt nature", 125.0),
            OfficialRecipeIngredient("noix", 20.0),
            OfficialRecipeIngredient("banane", 100.0),
        ),
        "snack",
    ),
    OfficialRecipe(
        "Fromage blanc et fruits secs", "Fromage blanc and dried fruit",
        listOf(
            OfficialRecipeIngredient("fromage blanc 0%", 150.0),
            OfficialRecipeIngredient("amandes", 20.0),
            OfficialRecipeIngredient("raisin", 60.0),
        ),
        "snack",
    ),
)
