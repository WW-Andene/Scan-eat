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

    /** Synthetic Product so checkDiet()/checkUserAllergens() can run against an official recipe before it's even cloned/logged. */
    fun toCheckProduct(): fr.scanneat.domain.model.Product = fr.scanneat.domain.model.Product(
        name        = nameFr,
        category    = fr.scanneat.domain.model.ProductCategory.OTHER,
        novaClass   = fr.scanneat.domain.model.NovaClass.UNPROCESSED,
        ingredients = ingredients.map { ing ->
            fr.scanneat.domain.model.Ingredient(name = ing.foodName, category = fr.scanneat.domain.model.IngredientCategory.FOOD)
        },
        nutrition   = fr.scanneat.domain.model.NutritionPer100g(
            energyKcal    = totalKcal * 100.0 / totalGrams.coerceAtLeast(1.0),
            fatG          = totalFatG * 100.0 / totalGrams.coerceAtLeast(1.0),
            saturatedFatG = 0.0,
            carbsG        = totalCarbsG * 100.0 / totalGrams.coerceAtLeast(1.0),
            sugarsG       = 0.0,
            fiberG        = totalFiberG * 100.0 / totalGrams.coerceAtLeast(1.0),
            proteinG      = totalProteinG * 100.0 / totalGrams.coerceAtLeast(1.0),
            saltG         = 0.0,
        ),
    )
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

    // ------------------------------------------------------------------
    // User-requested expansion (2026-08): 13 starter recipes was too thin
    // a library to browse - every ingredient below is an existing FOOD_DB
    // name (see file header - still CIQUAL-sourced, nothing fabricated),
    // just new "assiette-type" combinations, plus a second wave pairing
    // FOOD_DB's already-composed dishes (ratatouille, paella, curry de
    // poulet, etc.) with a simple starch/veg side, the same way "Thon,
    // salade verte et tomate" above already does.
    // ------------------------------------------------------------------
    OfficialRecipe(
        "Dinde, riz complet et haricot vert", "Turkey, brown rice and green beans",
        listOf(
            OfficialRecipeIngredient("dinde", 120.0),
            OfficialRecipeIngredient("riz complet cuit", 150.0),
            OfficialRecipeIngredient("haricot vert", 200.0),
            OfficialRecipeIngredient("huile d'olive", 5.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Cabillaud, patate douce et brocoli", "Cod, sweet potato and broccoli",
        listOf(
            OfficialRecipeIngredient("cabillaud", 120.0),
            OfficialRecipeIngredient("patate douce", 150.0),
            OfficialRecipeIngredient("brocoli", 200.0),
            OfficialRecipeIngredient("huile d'olive", 5.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Crevettes, riz basmati et poivron", "Shrimp, basmati rice and pepper",
        listOf(
            OfficialRecipeIngredient("crevette", 120.0),
            OfficialRecipeIngredient("riz basmati cuit", 150.0),
            OfficialRecipeIngredient("poivron", 180.0),
            OfficialRecipeIngredient("huile de sésame", 5.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Porc, semoule et courgette", "Pork, semolina and zucchini",
        listOf(
            OfficialRecipeIngredient("porc", 120.0),
            OfficialRecipeIngredient("semoule cuite", 150.0),
            OfficialRecipeIngredient("courgette", 200.0),
            OfficialRecipeIngredient("huile d'olive", 5.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Tofu, quinoa et épinards", "Tofu, quinoa and spinach",
        listOf(
            OfficialRecipeIngredient("tofu", 150.0),
            OfficialRecipeIngredient("quinoa cuit", 150.0),
            OfficialRecipeIngredient("épinard", 180.0),
            OfficialRecipeIngredient("huile de sésame", 5.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Steak de boeuf, pomme de terre et haricot vert", "Beef steak, potatoes and green beans",
        listOf(
            OfficialRecipeIngredient("steak de boeuf", 120.0),
            OfficialRecipeIngredient("pomme de terre", 180.0),
            OfficialRecipeIngredient("haricot vert", 180.0),
            OfficialRecipeIngredient("huile d'olive", 5.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Truite, boulgour et asperges", "Trout, bulgur and asparagus",
        listOf(
            OfficialRecipeIngredient("truite", 120.0),
            OfficialRecipeIngredient("boulgour cuit", 150.0),
            OfficialRecipeIngredient("asperge", 180.0),
            OfficialRecipeIngredient("huile d'olive", 5.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Escalope de veau, pâtes et tomate", "Veal escalope, pasta and tomato",
        listOf(
            OfficialRecipeIngredient("escalope de veau", 120.0),
            OfficialRecipeIngredient("pâtes cuites", 150.0),
            OfficialRecipeIngredient("tomate", 180.0),
            OfficialRecipeIngredient("huile d'olive", 5.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Saucisse, lentilles et carotte", "Sausage, lentils and carrot",
        listOf(
            OfficialRecipeIngredient("saucisse", 100.0),
            OfficialRecipeIngredient("lentille cuite", 180.0),
            OfficialRecipeIngredient("carotte", 150.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Lapin, riz blanc et champignons", "Rabbit, white rice and mushrooms",
        listOf(
            OfficialRecipeIngredient("lapin", 120.0),
            OfficialRecipeIngredient("riz blanc cuit", 150.0),
            OfficialRecipeIngredient("champignon", 180.0),
            OfficialRecipeIngredient("huile d'olive", 5.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Agneau, semoule et aubergine", "Lamb, semolina and eggplant",
        listOf(
            OfficialRecipeIngredient("agneau", 120.0),
            OfficialRecipeIngredient("semoule cuite", 150.0),
            OfficialRecipeIngredient("aubergine", 180.0),
            OfficialRecipeIngredient("huile d'olive", 5.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Canard, pomme de terre et haricot vert", "Duck, potatoes and green beans",
        listOf(
            OfficialRecipeIngredient("canard", 110.0),
            OfficialRecipeIngredient("pomme de terre", 170.0),
            OfficialRecipeIngredient("haricot vert", 180.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Maquereau, riz complet et fenouil", "Mackerel, brown rice and fennel",
        listOf(
            OfficialRecipeIngredient("maquereau", 120.0),
            OfficialRecipeIngredient("riz complet cuit", 150.0),
            OfficialRecipeIngredient("fenouil", 180.0),
            OfficialRecipeIngredient("huile d'olive", 5.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Falafels, boulgour et concombre", "Falafel, bulgur and cucumber",
        listOf(
            OfficialRecipeIngredient("falafel", 150.0),
            OfficialRecipeIngredient("boulgour cuit", 150.0),
            OfficialRecipeIngredient("concombre", 150.0),
            OfficialRecipeIngredient("houmous", 30.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Seitan, riz basmati et brocoli", "Seitan, basmati rice and broccoli",
        listOf(
            OfficialRecipeIngredient("seitan", 130.0),
            OfficialRecipeIngredient("riz basmati cuit", 150.0),
            OfficialRecipeIngredient("brocoli", 180.0),
            OfficialRecipeIngredient("huile de sésame", 5.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Haricots rouges, riz complet et poivron", "Kidney beans, brown rice and pepper",
        listOf(
            OfficialRecipeIngredient("haricot rouge cuit", 180.0),
            OfficialRecipeIngredient("riz complet cuit", 130.0),
            OfficialRecipeIngredient("poivron", 150.0),
            OfficialRecipeIngredient("oignon", 40.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Pois chiches, patate douce et épinards", "Chickpeas, sweet potato and spinach",
        listOf(
            OfficialRecipeIngredient("pois chiche cuit", 180.0),
            OfficialRecipeIngredient("patate douce", 150.0),
            OfficialRecipeIngredient("épinard", 150.0),
            OfficialRecipeIngredient("huile d'olive", 5.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Edamame, riz blanc et carotte", "Edamame, white rice and carrot",
        listOf(
            OfficialRecipeIngredient("edamame", 150.0),
            OfficialRecipeIngredient("riz blanc cuit", 150.0),
            OfficialRecipeIngredient("carotte", 150.0),
            OfficialRecipeIngredient("huile de sésame", 5.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Poulpe, pomme de terre et poivron", "Octopus, potatoes and pepper",
        listOf(
            OfficialRecipeIngredient("poulpe", 120.0),
            OfficialRecipeIngredient("pomme de terre", 170.0),
            OfficialRecipeIngredient("poivron", 150.0),
            OfficialRecipeIngredient("huile d'olive", 5.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Calamars, riz blanc et poivron", "Squid, white rice and pepper",
        listOf(
            OfficialRecipeIngredient("calamar", 120.0),
            OfficialRecipeIngredient("riz blanc cuit", 150.0),
            OfficialRecipeIngredient("poivron", 150.0),
            OfficialRecipeIngredient("huile d'olive", 5.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Moules-frites et salade verte", "Mussels and fries with green salad",
        listOf(
            OfficialRecipeIngredient("moules", 250.0),
            OfficialRecipeIngredient("frites", 150.0),
            OfficialRecipeIngredient("salade verte", 80.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Ratatouille et riz complet", "Ratatouille with brown rice",
        listOf(
            OfficialRecipeIngredient("ratatouille", 250.0),
            OfficialRecipeIngredient("riz complet cuit", 150.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Curry de poulet et riz basmati", "Chicken curry with basmati rice",
        listOf(
            OfficialRecipeIngredient("curry de poulet", 250.0),
            OfficialRecipeIngredient("riz basmati cuit", 150.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Chili con carne et riz blanc", "Chili con carne with white rice",
        listOf(
            OfficialRecipeIngredient("chili con carne", 250.0),
            OfficialRecipeIngredient("riz blanc cuit", 150.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Paella et salade verte", "Paella with green salad",
        listOf(
            OfficialRecipeIngredient("paella", 300.0),
            OfficialRecipeIngredient("salade verte", 80.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Risotto et brocoli", "Risotto with broccoli",
        listOf(
            OfficialRecipeIngredient("risotto", 250.0),
            OfficialRecipeIngredient("brocoli", 150.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Lasagnes et salade verte", "Lasagna with green salad",
        listOf(
            OfficialRecipeIngredient("lasagne", 280.0),
            OfficialRecipeIngredient("salade verte", 80.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Gratin dauphinois et haricots verts", "Potato gratin with green beans",
        listOf(
            OfficialRecipeIngredient("gratin dauphinois", 250.0),
            OfficialRecipeIngredient("haricot vert", 180.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Quiche lorraine et salade verte", "Quiche lorraine with green salad",
        listOf(
            OfficialRecipeIngredient("quiche lorraine", 180.0),
            OfficialRecipeIngredient("salade verte", 100.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Pad thaï et edamame", "Pad thai with edamame",
        listOf(
            OfficialRecipeIngredient("pad thaï", 280.0),
            OfficialRecipeIngredient("edamame", 80.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Sushi saumon et edamame", "Salmon sushi with edamame",
        listOf(
            OfficialRecipeIngredient("sushi saumon", 220.0),
            OfficialRecipeIngredient("edamame", 80.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Taboulé et pois chiches", "Tabbouleh with chickpeas",
        listOf(
            OfficialRecipeIngredient("taboulé", 200.0),
            OfficialRecipeIngredient("pois chiche cuit", 100.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Soupe de légumes et pain complet", "Vegetable soup with wholegrain bread",
        listOf(
            OfficialRecipeIngredient("soupe de légumes", 300.0),
            OfficialRecipeIngredient("pain complet", 60.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Gaspacho et pain complet", "Gazpacho with wholegrain bread",
        listOf(
            OfficialRecipeIngredient("gaspacho", 300.0),
            OfficialRecipeIngredient("pain complet", 60.0),
        ),
        "lunch_dinner",
    ),
    OfficialRecipe(
        "Muesli, lait et banane", "Muesli, milk and banana",
        listOf(
            OfficialRecipeIngredient("muesli", 60.0),
            OfficialRecipeIngredient("lait demi-écrémé", 200.0),
            OfficialRecipeIngredient("banane", 100.0),
        ),
        "breakfast",
    ),
    OfficialRecipe(
        "Céréales et lait", "Breakfast cereal and milk",
        listOf(
            OfficialRecipeIngredient("céréales petit-déjeuner", 50.0),
            OfficialRecipeIngredient("lait entier", 200.0),
        ),
        "breakfast",
    ),
    OfficialRecipe(
        "Pain au levain, beurre et confiture", "Sourdough bread, butter and jam",
        listOf(
            OfficialRecipeIngredient("pain au levain", 70.0),
            OfficialRecipeIngredient("beurre", 10.0),
            OfficialRecipeIngredient("confiture", 20.0),
        ),
        "breakfast",
    ),
    OfficialRecipe(
        "Crêpe, miel et banane", "Pancake, honey and banana",
        listOf(
            OfficialRecipeIngredient("crêpe nature", 80.0),
            OfficialRecipeIngredient("miel", 15.0),
            OfficialRecipeIngredient("banane", 100.0),
        ),
        "breakfast",
    ),
    OfficialRecipe(
        "Gaufre et fruits rouges", "Waffle and berries",
        listOf(
            OfficialRecipeIngredient("gaufre", 80.0),
            OfficialRecipeIngredient("myrtille", 100.0),
        ),
        "breakfast",
    ),
    OfficialRecipe(
        "Oeufs, bacon et pain de mie", "Eggs, bacon and toast",
        listOf(
            OfficialRecipeIngredient("oeuf", 100.0),
            OfficialRecipeIngredient("bacon", 30.0),
            OfficialRecipeIngredient("pain de mie", 60.0),
        ),
        "breakfast",
    ),
    OfficialRecipe(
        "Yaourt grec, miel et noix", "Greek yogurt, honey and walnuts",
        listOf(
            OfficialRecipeIngredient("yaourt grec", 150.0),
            OfficialRecipeIngredient("miel", 15.0),
            OfficialRecipeIngredient("noix", 15.0),
        ),
        "breakfast",
    ),
    OfficialRecipe(
        "Smoothie et graines de chia", "Smoothie and chia seeds",
        listOf(
            OfficialRecipeIngredient("smoothie fruits", 250.0),
            OfficialRecipeIngredient("graine de chia", 15.0),
        ),
        "breakfast",
    ),
    OfficialRecipe(
        "Houmous et pain pita", "Hummus and pita bread",
        listOf(
            OfficialRecipeIngredient("houmous", 60.0),
            OfficialRecipeIngredient("pain pita", 40.0),
        ),
        "snack",
    ),
    OfficialRecipe(
        "Pomme et beurre de cacahuète", "Apple and peanut butter",
        listOf(
            OfficialRecipeIngredient("pomme", 150.0),
            OfficialRecipeIngredient("beurre de cacahuète", 20.0),
        ),
        "snack",
    ),
    OfficialRecipe(
        "Fruits secs et pomme", "Mixed dried fruit and apple",
        listOf(
            OfficialRecipeIngredient("fruits secs mélangés", 30.0),
            OfficialRecipeIngredient("pomme", 130.0),
        ),
        "snack",
    ),
    OfficialRecipe(
        "Yaourt et miel", "Yogurt and honey",
        listOf(
            OfficialRecipeIngredient("yaourt nature", 125.0),
            OfficialRecipeIngredient("miel", 10.0),
        ),
        "snack",
    ),
    OfficialRecipe(
        "Fromage de chèvre et pain complet", "Goat cheese and wholegrain bread",
        listOf(
            OfficialRecipeIngredient("fromage de chèvre", 30.0),
            OfficialRecipeIngredient("pain complet", 40.0),
        ),
        "snack",
    ),
    OfficialRecipe(
        "Fromage cottage et myrtilles", "Cottage cheese and blueberries",
        listOf(
            OfficialRecipeIngredient("fromage cottage", 150.0),
            OfficialRecipeIngredient("myrtille", 80.0),
        ),
        "snack",
    ),
    OfficialRecipe(
        "Kéfir et framboises", "Kefir and raspberries",
        listOf(
            OfficialRecipeIngredient("kéfir", 200.0),
            OfficialRecipeIngredient("framboise", 80.0),
        ),
        "snack",
    ),
)
