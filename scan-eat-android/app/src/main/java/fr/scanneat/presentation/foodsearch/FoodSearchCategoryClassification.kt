package fr.scanneat.presentation.foodsearch

import fr.scanneat.domain.engine.nutrition.FoodEntry
import fr.scanneat.domain.model.ScanResult

// Explicit per-item classification, built once at class-init (not per search) -
// unlike the old per-source-file assoc, this actually reflects each item's real
// food group rather than which of the four FoodDb*.kt files it happened to be
// declared in.
internal val FOOD_DB_CATEGORY_BY_NAME: Map<String, FoodSearchCategory> = buildMap {
    listOf(
        "pomme", "banane", "orange", "fraise", "myrtille", "avocat", "kiwi", "raisin",
        "pêche", "poire", "ananas", "mangue", "pastèque", "melon", "cerise", "framboise",
        "mûre", "abricot", "prune", "pamplemousse", "citron", "clémentine", "figue",
        "datte", "noix de coco",
        "fruit de la passion", "litchi", "nectarine", "rhubarbe", "groseille", "goyave",
        "papaye", "fruit du dragon",
    ).forEach { put(it, FoodSearchCategory.FRUITS) }

    listOf(
        "tomate", "carotte", "brocoli", "épinard", "concombre", "courgette", "poivron",
        "oignon", "salade verte", "pomme de terre", "chou-fleur", "chou",
        "chou de bruxelles", "aubergine", "haricot vert", "petit pois", "asperge",
        "champignon", "betterave", "radis", "céleri", "poireau", "artichaut",
        "patate douce", "maïs", "ail",
        "fenouil", "panais", "navet", "endive", "roquette", "courge butternut",
        "potiron", "germe de soja", "topinambour",
    ).forEach { put(it, FoodSearchCategory.VEGETABLES) }

    listOf(
        "riz blanc cuit", "pâtes cuites", "pain blanc", "pain complet", "baguette",
        "croissant", "avoine", "quinoa cuit", "riz complet cuit", "semoule cuite",
        "boulgour cuit", "sarrasin cuit", "pain de mie", "tortilla de blé",
        "riz basmati cuit", "pain au levain", "galette de sarrasin", "muesli",
        "céréales petit-déjeuner", "pain pita", "polenta cuite", "millet cuit",
    ).forEach { put(it, FoodSearchCategory.GRAINS_STARCHES) }

    listOf(
        "poulet rôti", "boeuf haché 5%", "boeuf haché 15%", "saumon", "thon", "oeuf",
        "jambon blanc", "dinde", "porc", "agneau", "canard", "crevette", "moules",
        "cabillaud", "maquereau", "sardine", "tofu", "jambon cru", "saucisse", "bacon",
        "steak de boeuf", "escalope de veau", "foie de veau", "lapin", "pintade",
        "lieu noir", "truite", "merlan", "calamar", "poulpe", "seitan", "oeuf de caille",
    ).forEach { put(it, FoodSearchCategory.PROTEINS) }

    listOf(
        "lentille cuite", "pois chiche cuit", "amandes", "noix", "haricot rouge cuit",
        "haricot blanc cuit", "edamame", "noisette", "noix de cajou", "pistache",
        "graine de chia", "graine de lin", "beurre de cacahuète", "cacahuète",
        "fève cuite", "soja cuit", "noix du brésil", "noix de pécan",
        "graine de tournesol", "graine de courge", "beurre d'amande",
    ).forEach { put(it, FoodSearchCategory.LEGUMES_NUTS_SEEDS) }

    listOf(
        "lait demi-écrémé", "yaourt nature", "skyr", "fromage blanc 0%", "emmental",
        "camembert", "fromage de chèvre", "mozzarella", "feta", "parmesan",
        "lait entier", "crème fraîche", "lait de soja", "lait d'amande",
        "fromage cottage", "ricotta", "fromage à raclette", "kéfir", "lait d'avoine",
        "yaourt grec",
    ).forEach { put(it, FoodSearchCategory.DAIRY) }

    listOf(
        "huile d'olive", "beurre", "huile de colza", "huile de coco", "margarine", "mayonnaise",
        "huile de tournesol", "huile de sésame", "huile de lin", "saindoux", "beurre demi-sel",
    ).forEach { put(it, FoodSearchCategory.FATS_OILS) }

    listOf(
        "chocolat noir 70%", "chocolat au lait", "biscuit", "miel", "pâte à tartiner",
        "confiture", "chips", "pop-corn", "glace", "crêpe nature",
        "barre chocolatée", "bonbon", "sirop d'érable", "sucre blanc", "gaufre",
        "madeleine", "pain d'épices", "fruits secs mélangés",
    ).forEach { put(it, FoodSearchCategory.SWEETS_SNACKS) }

    listOf(
        "café noir", "thé", "jus d'orange", "coca-cola", "bière", "vin rouge",
        "jus de pomme", "eau gazeuse", "lait chocolaté",
        "kombucha", "jus de raisin", "smoothie fruits", "boisson énergisante",
        "champagne", "whisky", "lait de riz",
    ).forEach { put(it, FoodSearchCategory.BEVERAGES) }

    listOf(
        "pizza margherita", "hamburger", "frites", "sushi saumon", "houmous",
        "falafel", "quiche lorraine", "lasagne",
        "ratatouille", "couscous royal", "chili con carne", "curry de poulet",
        "risotto", "paella", "gratin dauphinois", "soupe de légumes",
        "sandwich jambon-beurre", "burrito", "ramen", "pad thaï", "kebab",
        "taboulé", "gaspacho",
    ).forEach { put(it, FoodSearchCategory.PREPARED_MEALS) }
}

internal fun FoodEntry.toItem(isCustom: Boolean) = FoodSearchItem(
    name = name, kcal = kcal, proteinG = proteinG, carbsG = carbsG, fatG = fatG,
    fiberG = fiberG, saltG = saltG, ironMg = ironMg, calciumMg = calciumMg, vitDUg = vitDUg, b12Ug = b12Ug,
    category = if (isCustom) FoodSearchCategory.CUSTOM
               else FOOD_DB_CATEGORY_BY_NAME[name] ?: FoodSearchCategory.OTHER,
)

internal fun ScanResult.toItem(): FoodSearchItem {
    val n = product.nutrition
    return FoodSearchItem(
        name = product.name, kcal = n.energyKcal, proteinG = n.proteinG, carbsG = n.carbsG, fatG = n.fatG,
        fiberG = n.fiberG, saltG = n.saltG,
        ironMg = n.ironMg ?: 0.0, calciumMg = n.calciumMg ?: 0.0, vitDUg = n.vitDUg ?: 0.0, b12Ug = n.b12Ug ?: 0.0,
        category = FoodSearchCategory.SCANNED, grade = audit.grade, scanId = dbId,
    )
}

internal fun FoodSearchItem.matches(filter: FoodSearchFilter): Boolean = when (filter) {
    FoodSearchFilter.ALL            -> true
    FoodSearchFilter.HIGH_PROTEIN   -> proteinG >= 15.0
    FoodSearchFilter.LOW_CARB       -> carbsG <= 10.0
    FoodSearchFilter.HIGH_FIBER     -> fiberG >= 3.0
    FoodSearchFilter.IRON_SOURCE    -> ironMg >= 2.0
    FoodSearchFilter.CALCIUM_SOURCE -> calciumMg >= 100.0
}
