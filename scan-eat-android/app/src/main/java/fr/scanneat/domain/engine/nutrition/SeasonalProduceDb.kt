package fr.scanneat.domain.engine.nutrition

// ============================================================================
// SEASONAL PRODUCE CALENDAR
//
// PROVENANCE:
//   Months reflect the standard French/temperate-climate outdoor growing
//   season commonly published by ADEME and Interfel's "calendrier des
//   fruits et légumes de saison" - the same reference most French seasonal-
//   eating guides use. This is a general reference, not a regional or
//   yearly-exact forecast: actual availability shifts a few weeks either
//   way with weather, and greenhouse-grown/stored produce (e.g. tomatoes,
//   potatoes, onions) can appear on shelves outside these windows. Excludes
//   tropical/imported-only fruit (banana, mango, pineapple, avocado, etc.)
//   that has no real outdoor French season.
//
// Used for: SeasonalProduceScreen (Dashboard > Produits de saison).
// ============================================================================

enum class SeasonalProduceKind { FRUIT, VEGETABLE }

/** [months] are 1 (January) .. 12 (December), the months this item is in its
 *  outdoor French growing season. */
data class SeasonalProduce(val nameFr: String, val nameEn: String, val kind: SeasonalProduceKind, val months: Set<Int>)

private fun range(vararg months: Int) = months.toSet()

/** Months m1..m2 inclusive, wrapping across the December→January boundary
 *  when m1 > m2 (e.g. wrap(11, 3) = Nov, Dec, Jan, Feb, Mar). */
private fun wrap(m1: Int, m2: Int): Set<Int> =
    if (m1 <= m2) (m1..m2).toSet() else ((m1..12) + (1..m2)).toSet()

val SEASONAL_PRODUCE_DB: List<SeasonalProduce> = listOf(
    // ---- Fruits ----
    SeasonalProduce("Fraise", "Strawberry", SeasonalProduceKind.FRUIT, range(5, 6, 7, 8)),
    SeasonalProduce("Cerise", "Cherry", SeasonalProduceKind.FRUIT, range(6, 7)),
    SeasonalProduce("Abricot", "Apricot", SeasonalProduceKind.FRUIT, range(6, 7, 8)),
    SeasonalProduce("Pêche", "Peach", SeasonalProduceKind.FRUIT, range(6, 7, 8, 9)),
    SeasonalProduce("Nectarine", "Nectarine", SeasonalProduceKind.FRUIT, range(6, 7, 8, 9)),
    SeasonalProduce("Prune", "Plum", SeasonalProduceKind.FRUIT, range(7, 8, 9)),
    SeasonalProduce("Melon", "Melon", SeasonalProduceKind.FRUIT, range(6, 7, 8, 9)),
    SeasonalProduce("Pastèque", "Watermelon", SeasonalProduceKind.FRUIT, range(6, 7, 8, 9)),
    SeasonalProduce("Framboise", "Raspberry", SeasonalProduceKind.FRUIT, range(6, 7, 8, 9)),
    SeasonalProduce("Myrtille", "Blueberry", SeasonalProduceKind.FRUIT, range(6, 7, 8, 9)),
    SeasonalProduce("Groseille", "Redcurrant", SeasonalProduceKind.FRUIT, range(6, 7, 8)),
    SeasonalProduce("Mûre", "Blackberry", SeasonalProduceKind.FRUIT, range(8, 9, 10)),
    SeasonalProduce("Figue", "Fig", SeasonalProduceKind.FRUIT, range(8, 9, 10)),
    SeasonalProduce("Raisin", "Grape", SeasonalProduceKind.FRUIT, range(8, 9, 10)),
    SeasonalProduce("Rhubarbe", "Rhubarb", SeasonalProduceKind.FRUIT, range(4, 5, 6)),
    SeasonalProduce("Poire", "Pear", SeasonalProduceKind.FRUIT, wrap(8, 1)),
    SeasonalProduce("Pomme", "Apple", SeasonalProduceKind.FRUIT, wrap(9, 4)),
    SeasonalProduce("Datte", "Date", SeasonalProduceKind.FRUIT, range(10, 11, 12)),
    SeasonalProduce("Clémentine", "Clementine", SeasonalProduceKind.FRUIT, wrap(11, 1)),
    SeasonalProduce("Kiwi", "Kiwi", SeasonalProduceKind.FRUIT, wrap(11, 4)),
    SeasonalProduce("Orange", "Orange", SeasonalProduceKind.FRUIT, wrap(12, 3)),
    SeasonalProduce("Pamplemousse", "Grapefruit", SeasonalProduceKind.FRUIT, wrap(12, 3)),
    SeasonalProduce("Citron", "Lemon", SeasonalProduceKind.FRUIT, wrap(11, 3)),

    // ---- Légumes ----
    SeasonalProduce("Asperge", "Asparagus", SeasonalProduceKind.VEGETABLE, range(4, 5, 6)),
    SeasonalProduce("Radis", "Radish", SeasonalProduceKind.VEGETABLE, range(4, 5, 6, 7, 8, 9, 10)),
    SeasonalProduce("Petit pois", "Green pea", SeasonalProduceKind.VEGETABLE, range(5, 6, 7)),
    SeasonalProduce("Fève", "Broad bean", SeasonalProduceKind.VEGETABLE, range(5, 6, 7)),
    SeasonalProduce("Artichaut", "Artichoke", SeasonalProduceKind.VEGETABLE, range(5, 6, 7, 8, 9, 10, 11)),
    SeasonalProduce("Épinard", "Spinach", SeasonalProduceKind.VEGETABLE, range(3, 4, 5, 9, 10, 11)),
    SeasonalProduce("Salade verte", "Lettuce", SeasonalProduceKind.VEGETABLE, range(4, 5, 6, 7, 8, 9, 10)),
    SeasonalProduce("Roquette", "Arugula", SeasonalProduceKind.VEGETABLE, range(4, 5, 6, 7, 8, 9, 10)),
    SeasonalProduce("Haricot vert", "Green bean", SeasonalProduceKind.VEGETABLE, range(6, 7, 8, 9)),
    SeasonalProduce("Courgette", "Zucchini", SeasonalProduceKind.VEGETABLE, range(6, 7, 8, 9)),
    SeasonalProduce("Concombre", "Cucumber", SeasonalProduceKind.VEGETABLE, range(6, 7, 8, 9)),
    SeasonalProduce("Aubergine", "Eggplant", SeasonalProduceKind.VEGETABLE, range(7, 8, 9, 10)),
    SeasonalProduce("Poivron", "Pepper", SeasonalProduceKind.VEGETABLE, range(7, 8, 9, 10)),
    SeasonalProduce("Tomate", "Tomato", SeasonalProduceKind.VEGETABLE, range(6, 7, 8, 9, 10)),
    SeasonalProduce("Maïs", "Corn", SeasonalProduceKind.VEGETABLE, range(7, 8, 9)),
    SeasonalProduce("Brocoli", "Broccoli", SeasonalProduceKind.VEGETABLE, range(6, 7, 8, 9, 10, 11)),
    SeasonalProduce("Betterave", "Beetroot", SeasonalProduceKind.VEGETABLE, range(6, 7, 8, 9, 10, 11)),
    SeasonalProduce("Fenouil", "Fennel", SeasonalProduceKind.VEGETABLE, range(6, 7, 8, 9, 10, 11)),
    SeasonalProduce("Haricot blanc", "White bean", SeasonalProduceKind.VEGETABLE, range(7, 8, 9)),
    SeasonalProduce("Chou-fleur", "Cauliflower", SeasonalProduceKind.VEGETABLE, wrap(9, 4)),
    SeasonalProduce("Poireau", "Leek", SeasonalProduceKind.VEGETABLE, wrap(9, 4)),
    SeasonalProduce("Céleri", "Celery", SeasonalProduceKind.VEGETABLE, wrap(9, 3)),
    SeasonalProduce("Chou", "Cabbage", SeasonalProduceKind.VEGETABLE, wrap(9, 3)),
    SeasonalProduce("Potiron", "Pumpkin", SeasonalProduceKind.VEGETABLE, range(9, 10, 11, 12)),
    SeasonalProduce("Courge butternut", "Butternut squash", SeasonalProduceKind.VEGETABLE, range(9, 10, 11, 12)),
    SeasonalProduce("Ail", "Garlic", SeasonalProduceKind.VEGETABLE, range(7, 8, 9, 10, 11, 12)),
    SeasonalProduce("Chou de bruxelles", "Brussels sprouts", SeasonalProduceKind.VEGETABLE, range(10, 11, 12, 1, 2)),
    SeasonalProduce("Endive", "Endive", SeasonalProduceKind.VEGETABLE, wrap(10, 4)),
    SeasonalProduce("Topinambour", "Jerusalem artichoke", SeasonalProduceKind.VEGETABLE, wrap(10, 3)),
    SeasonalProduce("Panais", "Parsnip", SeasonalProduceKind.VEGETABLE, wrap(10, 3)),
    SeasonalProduce("Navet", "Turnip", SeasonalProduceKind.VEGETABLE, wrap(6, 3)),
    SeasonalProduce("Carotte", "Carrot", SeasonalProduceKind.VEGETABLE, (1..12).toSet()),
    SeasonalProduce("Pomme de terre", "Potato", SeasonalProduceKind.VEGETABLE, (1..12).toSet()),
    SeasonalProduce("Oignon", "Onion", SeasonalProduceKind.VEGETABLE, (1..12).toSet()),
)
