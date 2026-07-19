package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*
import org.junit.Assert.*
import org.junit.Test

// ============================================================================
// SCORING ENGINE UNIT TESTS
// Port of tests/engine.tests.ts — same fixtures, same expected ranges.
// ============================================================================

class ScoringEngineTest {

    // ---- Helpers ----

    private fun product(
        name: String,
        category: ProductCategory,
        novaClass: NovaClass,
        ingredients: List<Ingredient>,
        nutrition: NutritionPer100g,
        organic: Boolean = false,
        wholeGrainPrimary: Boolean = false,
        fermented: Boolean = false,
        hasHealthClaims: Boolean = false,
        hasMisleadingMarketing: Boolean = false,
        namedOils: Boolean? = null,
        origin: String? = null,
        originTransparent: Boolean = false,
    ) = Product(name, category, novaClass, ingredients, nutrition,
        organic = organic, wholeGrainPrimary = wholeGrainPrimary, fermented = fermented,
        hasHealthClaims = hasHealthClaims, hasMisleadingMarketing = hasMisleadingMarketing,
        namedOils = namedOils, origin = origin, originTransparent = originTransparent)

    private fun nutrition(
        kcal: Double, fat: Double, satFat: Double, carbs: Double,
        sugars: Double, fiber: Double, protein: Double, salt: Double,
        addedSugars: Double? = null, transFat: Double? = null,
    ) = NutritionPer100g(
        energyKcal = kcal, fatG = fat, saturatedFatG = satFat, carbsG = carbs,
        sugarsG = sugars, addedSugarsG = addedSugars, fiberG = fiber,
        proteinG = protein, saltG = salt, transFatG = transFat,
    )

    private fun ingredient(name: String, isWhole: Boolean? = null, eNumber: String? = null,
                           cat: IngredientCategory = IngredientCategory.FOOD, pct: Double? = null) =
        Ingredient(name = name, isWholeFood = isWhole, eNumber = eNumber, category = cat, percentage = pct)

    private fun expectGrade(product: Product, grades: List<Grade>, range: IntRange) {
        val audit = scoreProduct(product)
        assertTrue("${product.name}: grade ${audit.grade} not in $grades (score=${audit.score})",
            audit.grade in grades)
        assertTrue("${product.name}: score ${audit.score} outside $range",
            audit.score in range)
    }

    // ============================================================
    // Fixtures — real French supermarket products
    // ============================================================

    @Test fun `Evian mineral water scores A or A+`() {
        expectGrade(
            product(
                name = "Evian eau minérale",
                category = ProductCategory.BEVERAGE_WATER,
                novaClass = NovaClass.UNPROCESSED,
                ingredients = listOf(ingredient("eau minérale naturelle", isWhole = true)),
                nutrition = nutrition(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                origin = "France", namedOils = true, originTransparent = true,
            ),
            grades = listOf(Grade.A_PLUS, Grade.A),
            range = 70..100,
        )
    }

    @Test fun `Skyr nature scores A or A+`() {
        expectGrade(
            product(
                name = "Skyr nature",
                category = ProductCategory.YOGURT,
                novaClass = NovaClass.PROCESSED,
                ingredients = listOf(
                    ingredient("lait écrémé", isWhole = true),
                    ingredient("ferments lactiques"),
                ),
                nutrition = nutrition(60.0, 0.2, 0.1, 4.0, 4.0, 0.0, 11.0, 0.1, addedSugars = 0.0),
                fermented = true, origin = "France", namedOils = true, originTransparent = true,
            ),
            grades = listOf(Grade.A, Grade.A_PLUS, Grade.B),
            range = 55..100,
        )
    }

    @Test fun `Coca-Cola scores D or F`() {
        expectGrade(
            product(
                name = "Coca-Cola",
                category = ProductCategory.BEVERAGE_SOFT,
                novaClass = NovaClass.ULTRA_PROCESSED,
                ingredients = listOf(
                    ingredient("eau gazéifiée", isWhole = true),
                    ingredient("sucre"),
                    ingredient("colorant: caramel", eNumber = "E150", cat = IngredientCategory.ADDITIVE),
                    ingredient("acide phosphorique", eNumber = "E338", cat = IngredientCategory.ADDITIVE),
                    ingredient("arômes naturels", cat = IngredientCategory.ADDITIVE),
                    ingredient("caféine", cat = IngredientCategory.ADDITIVE),
                ),
                nutrition = nutrition(42.0, 0.0, 0.0, 10.6, 10.6, 0.0, 0.0, 0.0, addedSugars = 10.6),
                namedOils = true,
            ),
            grades = listOf(Grade.D, Grade.F),
            range = 0..40,
        )
    }

    @Test fun `Nutella has critical sugar deduction and grade C, D, or F`() {
        val audit = scoreProduct(product(
            name = "Nutella",
            category = ProductCategory.SNACK_SWEET,
            novaClass = NovaClass.ULTRA_PROCESSED,
            ingredients = listOf(
                ingredient("sucre"),
                ingredient("huile de palme"),
                ingredient("noisettes", isWhole = true),
                ingredient("cacao maigre"),
                ingredient("lait écrémé en poudre"),
                ingredient("émulsifiants: lécithines", eNumber = "E322", cat = IngredientCategory.ADDITIVE),
                ingredient("vanilline", cat = IngredientCategory.ADDITIVE),
            ),
            nutrition = nutrition(539.0, 30.9, 10.6, 57.5, 56.3, 0.0, 6.3, 0.107, addedSugars = 50.0),
            namedOils = true,
        ))
        assertTrue("Nutella grade ${audit.grade} should be C/D/F", audit.grade in listOf(Grade.C, Grade.D, Grade.F))
        val sugarDeduction = audit.pillars.negativeNutrients.deductions
            .firstOrNull { it.reason.contains("sugar", ignoreCase = true) && it.severity == Severity.CRITICAL }
        assertNotNull("Nutella should trigger a critical sugar deduction", sugarDeduction)
    }

    @Test fun `Jambon blanc sans nitrite scores A or B`() {
        expectGrade(
            product(
                name = "Jambon blanc sans nitrite",
                category = ProductCategory.PROCESSED_MEAT,
                novaClass = NovaClass.PROCESSED,
                ingredients = listOf(
                    ingredient("jambon de porc 95%", isWhole = true, pct = 95.0),
                    ingredient("sel"),
                    ingredient("bouillon"),
                    ingredient("antioxydant: ascorbate de sodium", eNumber = "E301", cat = IngredientCategory.ADDITIVE),
                ),
                nutrition = nutrition(105.0, 2.5, 0.9, 0.5, 0.5, 0.0, 21.0, 1.8, addedSugars = 0.0),
                origin = "France", namedOils = true, originTransparent = true,
            ),
            grades = listOf(Grade.A, Grade.B),
            range = 55..85,
        )
    }

    @Test fun `Camembert scores A or B with category-aware sat fat`() {
        val audit = scoreProduct(product(
            name = "Camembert",
            category = ProductCategory.CHEESE,
            novaClass = NovaClass.PROCESSED,
            ingredients = listOf(
                ingredient("lait cru de vache", isWhole = true),
                ingredient("sel"),
                ingredient("ferments lactiques"),
                ingredient("présure"),
            ),
            nutrition = nutrition(290.0, 23.0, 14.0, 0.5, 0.5, 0.0, 20.0, 1.4, addedSugars = 0.0),
            origin = "Normandie", namedOils = true, originTransparent = true, fermented = true,
        ))
        assertTrue("Camembert grade ${audit.grade} should be A or B (got score ${audit.score})",
            audit.grade in listOf(Grade.A, Grade.B))
        // Sat fat must NOT be critical — cheese threshold is [12,20,30] not [5,10,15]
        val critSatFat = audit.pillars.negativeNutrients.deductions
            .firstOrNull { it.reason.contains("saturated", ignoreCase = true) && it.severity == Severity.CRITICAL }
        assertNull("Camembert sat fat should not be critical (category-aware threshold)", critSatFat)
    }

    @Test fun `Huile d'olive scores A (oil sat-fat tolerance)`() {
        expectGrade(
            product(
                name = "Huile d'olive vierge extra",
                category = ProductCategory.OIL_FAT,
                novaClass = NovaClass.UNPROCESSED,
                ingredients = listOf(ingredient("huile d'olive", isWhole = true)),
                nutrition = nutrition(824.0, 91.0, 13.5, 0.0, 0.0, 0.0, 0.0, 0.0),
                namedOils = true, origin = "Espagne", originTransparent = true,
            ),
            grades = listOf(Grade.A_PLUS, Grade.A, Grade.B),
            range = 50..100,
        )
    }

    @Test fun `Pain complet bio scores A or B`() {
        expectGrade(
            product(
                name = "Pain complet bio",
                category = ProductCategory.BREAD,
                novaClass = NovaClass.PROCESSED,
                ingredients = listOf(
                    ingredient("farine de blé complète 95%", isWhole = true, pct = 95.0),
                    ingredient("eau"),
                    ingredient("sel"),
                    ingredient("levure"),
                ),
                nutrition = nutrition(248.0, 1.5, 0.3, 46.0, 2.5, 7.0, 9.0, 1.1, addedSugars = 0.0),
                organic = true, wholeGrainPrimary = true, origin = "France",
                namedOils = true, originTransparent = true,
            ),
            grades = listOf(Grade.A, Grade.B, Grade.A_PLUS),
            range = 55..100,
        )
    }

    @Test fun `Cereales chocolatees enfants scores D or F`() {
        expectGrade(
            product(
                name = "Céréales chocolatées enfants",
                category = ProductCategory.BREAKFAST_CEREAL,
                novaClass = NovaClass.ULTRA_PROCESSED,
                ingredients = listOf(
                    ingredient("sucre", cat = IngredientCategory.FOOD),
                    ingredient("farine de blé"),
                    ingredient("cacao maigre"),
                    ingredient("arômes", cat = IngredientCategory.ADDITIVE),
                    ingredient("émulsifiant: lécithine de soja", eNumber = "E322", cat = IngredientCategory.ADDITIVE),
                    ingredient("colorant: caramel", eNumber = "E150d", cat = IngredientCategory.ADDITIVE),
                ),
                nutrition = nutrition(385.0, 3.0, 0.5, 82.0, 40.0, 3.5, 6.0, 0.3, addedSugars = 40.0),
                hasHealthClaims = true,
            ),
            grades = listOf(Grade.D, Grade.F),
            range = 0..39,
        )
    }

    @Test fun `Saumon frais scores A+`() {
        expectGrade(
            product(
                name = "Saumon frais",
                category = ProductCategory.FISH,
                novaClass = NovaClass.UNPROCESSED,
                ingredients = listOf(ingredient("saumon atlantique", isWhole = true)),
                nutrition = nutrition(208.0, 13.0, 3.0, 0.0, 0.0, 0.0, 20.0, 0.07),
                origin = "Norvège", namedOils = true, originTransparent = true,
            ),
            grades = listOf(Grade.A_PLUS, Grade.A),
            range = 70..100,
        )
    }

    // ============================================================
    // Veto conditions
    // ============================================================

    @Test fun `Trans fat above 0_1 caps score at 40`() {
        val audit = scoreProduct(product(
            name = "Margarine industrielle",
            category = ProductCategory.OIL_FAT,
            novaClass = NovaClass.ULTRA_PROCESSED,
            ingredients = listOf(
                ingredient("huiles partiellement hydrogénées"),
                ingredient("sel"),
            ),
            nutrition = nutrition(700.0, 80.0, 30.0, 0.0, 0.0, 0.0, 0.0, 1.2, transFat = 2.5),
        ))
        assertTrue("Expected score ≤40 (trans-fat veto), got ${audit.score}", audit.score <= 40)
        assertTrue("Veto should be triggered", audit.veto.triggered)
        assertTrue("Veto reason should mention trans fat", audit.veto.reason.contains("trans", ignoreCase = true))
    }

    @Test fun `Trans fat exactly at 0_1 does NOT trigger veto`() {
        val audit = scoreProduct(product(
            name = "Margarine légère",
            category = ProductCategory.OIL_FAT,
            novaClass = NovaClass.PROCESSED,
            ingredients = listOf(ingredient("huile de tournesol")),
            nutrition = nutrition(700.0, 80.0, 8.0, 0.0, 0.0, 0.0, 0.0, 0.5, transFat = 0.1),
        ))
        assertFalse("No veto expected at exactly 0.1g (threshold is strictly > 0.1)", audit.veto.triggered)
    }

    // Regression: checkVeto() used to return the FIRST triggered condition in
    // declaration order, not the most restrictive one. A product that's both a
    // sugar-sweetened beverage (cap 30) and contains industrial trans fat (cap
    // 40) used to get capped at the looser 40, since the trans-fat check runs
    // first, even though the SSB cap is stricter. It must pick the tightest
    // cap regardless of which condition happens to be checked first.
    @Test fun `Product triggering two veto conditions gets the stricter (lower) cap`() {
        val audit = scoreProduct(product(
            name = "Soda trans-gras (test)",
            category = ProductCategory.BEVERAGE_SOFT,
            novaClass = NovaClass.ULTRA_PROCESSED,
            ingredients = listOf(ingredient("huiles partiellement hydrogénées"), ingredient("sucre")),
            // Trans fat >0.1g triggers the cap-40 veto; sugar>5g + protein<1g +
            // fiber<1g on a BEVERAGE_SOFT triggers the stricter cap-30 SSB veto.
            nutrition = nutrition(150.0, 5.0, 1.0, 10.0, 10.0, 0.0, 0.0, 0.0, transFat = 2.5),
        ))
        assertTrue("Veto should be triggered", audit.veto.triggered)
        assertEquals("Most restrictive cap (30, SSB) should win over the looser trans-fat cap (40)",
            30, audit.veto.cap)
        assertTrue("Score should respect the stricter 30 cap, got ${audit.score}", audit.score <= 30)
        assertTrue("Veto reason should mention the beverage (the stricter condition), not trans fat",
            audit.veto.reason.contains("beverage", ignoreCase = true) || audit.veto.reason.contains("boisson", ignoreCase = true))
    }

    // Regression: checkVeto()'s flat ">30g added sugar in non-confectionery"
    // rule ignored CategoryThresholds.kt's own, deliberately wider CONDIMENT
    // sugar band (30g is only that category's "major" tier — critical starts
    // at 45g, since condiments are eaten by the tablespoon). A chutney-style
    // condiment at 35g used to get hard-capped to grade C by this veto even
    // though the pillar itself only scored it MAJOR, not CRITICAL.
    @Test fun `Condiment with 35g sugar is not vetoed - only SNACK_SWEET-style hard cap is exempt`() {
        val audit = scoreProduct(product(
            name = "Chutney mangue (test)",
            category = ProductCategory.CONDIMENT,
            novaClass = NovaClass.PROCESSED,
            ingredients = listOf(ingredient("mangue"), ingredient("sucre"), ingredient("vinaigre")),
            nutrition = nutrition(150.0, 0.5, 0.1, 38.0, 35.0, 1.0, 1.0, 0.8),
        ))
        assertFalse("35g sugar condiment should not hit the >30g flat veto", audit.veto.triggered)
    }

    @Test fun `Condiment with 50g sugar (above its own 45g critical tier) is still fine on the flat veto`() {
        // CONDIMENT is fully exempt from this particular flat rule (matching
        // SNACK_SWEET) - its own pillar-level critical deduction at 45g is the
        // mechanism that penalizes this, not this cross-category veto.
        val audit = scoreProduct(product(
            name = "Confiture forte (test)",
            category = ProductCategory.CONDIMENT,
            novaClass = NovaClass.PROCESSED,
            ingredients = listOf(ingredient("fruits"), ingredient("sucre")),
            nutrition = nutrition(250.0, 0.2, 0.0, 55.0, 50.0, 1.0, 0.5, 0.1),
        ))
        assertFalse(audit.veto.triggered)
    }

    // ============================================================
    // NOVA fresh-produce exception
    // ============================================================

    @Test fun `Banana with empty ingredients infers NOVA 1`() {
        val product = product(
            name = "Banane", category = ProductCategory.OTHER,
            novaClass = NovaClass.ULTRA_PROCESSED, ingredients = emptyList(),
            nutrition = nutrition(89.0, 0.3, 0.1, 23.0, 12.0, 2.6, 1.1, 0.0),
        )
        val result = inferNovaClassWithConfidence(product)
        assertEquals(NovaClass.UNPROCESSED, result.nova)
    }

    @Test fun `Pomme infers NOVA 1 via FR lexicon`() {
        val product = product(
            name = "Pomme", category = ProductCategory.OTHER,
            novaClass = NovaClass.ULTRA_PROCESSED, ingredients = emptyList(),
            nutrition = nutrition(52.0, 0.2, 0.0, 14.0, 10.0, 2.4, 0.3, 0.0),
        )
        assertEquals(NovaClass.UNPROCESSED, inferNovaClassWithConfidence(product).nova)
    }

    @Test fun `FR plural forms resolve as fresh produce NOVA 1`() {
        for (name in listOf("pommes", "bananes", "courgettes", "carottes", "fraises")) {
            val product = product(
                name = name, category = ProductCategory.OTHER,
                novaClass = NovaClass.ULTRA_PROCESSED, ingredients = emptyList(),
                nutrition = nutrition(50.0, 0.0, 0.0, 12.0, 10.0, 2.0, 1.0, 0.0),
            )
            assertEquals("\"$name\" should infer NOVA 1",
                NovaClass.UNPROCESSED, inferNovaClassWithConfidence(product).nova)
        }
    }

    @Test fun `Unknown branded product with empty ingredients falls back to NOVA 4`() {
        val product = product(
            name = "Mystery Branded Snack Co.",
            category = ProductCategory.OTHER,
            novaClass = NovaClass.ULTRA_PROCESSED, ingredients = emptyList(),
            nutrition = nutrition(500.0, 25.0, 10.0, 50.0, 20.0, 2.0, 8.0, 1.0),
        )
        assertEquals(NovaClass.ULTRA_PROCESSED, inferNovaClassWithConfidence(product).nova)
    }

    // ============================================================
    // Engine version pin
    // ============================================================

    @Test fun `Engine version is 2_3_0`() {
        assertEquals("2.3.0", ENGINE_VERSION)
    }

    // ============================================================
    // Additive tier lookup
    // ============================================================

    @Test fun `E250 sodium nitrite is Tier 1`() {
        val result = findAdditive("E250", "nitrite de sodium", IngredientCategory.ADDITIVE)
        assertNotNull(result)
        assertEquals(AdditiveTier.ONE, result!!.tier)
    }

    @Test fun `E951 aspartame is Tier 2`() {
        val result = findAdditive("E951", "aspartame", IngredientCategory.ADDITIVE)
        assertNotNull(result)
        assertEquals(AdditiveTier.TWO, result!!.tier)
    }

    @Test fun `E330 citric acid is Tier 3`() {
        val result = findAdditive(null, "acide citrique", IngredientCategory.ADDITIVE)
        assertNotNull(result)
        assertEquals(AdditiveTier.THREE, result!!.tier)
    }

    @Test fun `Unknown additive returns null`() {
        assertNull(findAdditive(null, "eau minérale", IngredientCategory.FOOD))
    }

    // ============================================================
    // Additive name-matching — longest-synonym-wins (not first-match-wins)
    // ============================================================
    // Regression for a real Tier-classification bug: E150's own synonym
    // "caramel" is a substring of E150a's "caramel ordinaire" and E150b's
    // "caramel caustique" - a first-match-wins scan over ADDITIVES_DB (in
    // declaration order) silently resolved both of those genuinely Tier-3
    // ("no 4-MEI concern") colorants to generic E150 (Tier 2, "commercial
    // caramel in dark sodas... 4-MEI concern"), applying the harsher
    // deduction to a plain/caustic caramel that doesn't carry that concern.

    @Test fun `Plain caramel resolves to E150a Tier 3, not generic E150 Tier 2`() {
        val result = findAdditive(null, "caramel ordinaire", IngredientCategory.ADDITIVE)
        assertNotNull(result)
        assertEquals("E150a", result!!.eNumber)
        assertEquals(AdditiveTier.THREE, result.tier)
    }

    @Test fun `Caustic caramel resolves to E150b Tier 3, not generic E150 Tier 2`() {
        val result = findAdditive(null, "caramel caustique", IngredientCategory.ADDITIVE)
        assertNotNull(result)
        assertEquals("E150b", result!!.eNumber)
        assertEquals(AdditiveTier.THREE, result.tier)
    }

    @Test fun `Ammonia caramel still resolves to E150c Tier 2 by name alone`() {
        val result = findAdditive(null, "caramel ammoniacal", IngredientCategory.ADDITIVE)
        assertNotNull(result)
        assertEquals("E150c", result!!.eNumber)
        assertEquals(AdditiveTier.TWO, result.tier)
    }

    @Test fun `Unqualified caramel still resolves to generic E150`() {
        val result = findAdditive(null, "colorant caramel", IngredientCategory.ADDITIVE)
        assertNotNull(result)
        assertEquals("E150", result!!.eNumber)
        assertEquals(AdditiveTier.TWO, result.tier)
    }

    // Regression: E150's synonym list used to include bare "caramel" (with no
    // category gate on the name-match loop), so a plain dessert ingredient
    // like "crème caramel" - parsed as ordinary FOOD, no eNumber - was
    // misclassified as containing a Tier-2 colorant additive. Bare "caramel"
    // is only a real colorant signal when the ingredient is already tagged
    // ADDITIVE (the natural-colorants fallback still covers that case below).
    @Test fun `Plain dessert ingredient containing 'caramel' is not misclassified as an additive`() {
        assertNull(findAdditive(null, "crème caramel", IngredientCategory.FOOD))
        assertNull(findAdditive(null, "caramel", null))
    }

    @Test fun `Bare caramel tagged as an additive still resolves to E150 via the natural-colorant fallback`() {
        val result = findAdditive(null, "caramel", IngredientCategory.ADDITIVE)
        assertNotNull(result)
        assertEquals("E150", result!!.eNumber)
    }

    @Test fun `Sodium metabisulfite resolves to E223, not the shorter E221 substring match`() {
        // "sulfite de sodium" (E221's own synonym) is a literal substring of
        // "métabisulfite de sodium" (E223's name) once accents are stripped -
        // same shadowing pattern as the caramel family, verified across the
        // whole additive DB when the longest-match-wins fix was made.
        val result = findAdditive(null, "métabisulfite de sodium", IngredientCategory.ADDITIVE)
        assertNotNull(result)
        assertEquals("E223", result!!.eNumber)
    }

    @Test fun `Nitrous oxide resolves to E942, not the shorter E941 substring match`() {
        // "azote" (E941's synonym) is a literal substring of "protoxyde d'azote"
        // (E942's name) - same shadowing pattern as the caramel/sulfite families.
        val result = findAdditive(null, "protoxyde d'azote", IngredientCategory.ADDITIVE)
        assertNotNull(result)
        assertEquals("E942", result!!.eNumber)
    }

    // ============================================================
    // Ingredient integrity — whole-food keyword matching
    // ============================================================

    @Test fun `New whole-food keywords earn the first-3-ingredients bonus`() {
        // Regression for a gap where a real ingredient like "crevette" (shrimp)
        // never earned the whole-food bonus because it was simply missing from
        // WHOLE_FOOD_KEYWORDS, despite being just as much a whole food as the
        // saumon/thon/haricot/lentille entries already in that list. isWhole is
        // left null (not explicitly true) so this exercises the WHOLE_FOOD_KEYWORDS
        // heuristic itself, not a caller-supplied flag.
        for (name in listOf("crevette", "tofu", "champignon", "cabillaud", "artichaut", "maïs")) {
            val p = product(
                name = "Test $name",
                category = ProductCategory.OTHER,
                novaClass = NovaClass.PROCESSED,
                ingredients = listOf(ingredient(name)),
                nutrition = nutrition(100.0, 1.0, 0.2, 5.0, 1.0, 1.0, 10.0, 0.2),
            )
            val pillar = scoreIngredientIntegrity(p)
            val bonus = pillar.bonuses.firstOrNull { it.reason.contains("whole food", ignoreCase = true) }
            assertNotNull("\"$name\" should be recognized as a whole food (WHOLE_FOOD_KEYWORDS)", bonus)
        }
    }

    // ============================================================
    // Category inference from name
    // ============================================================

    @Test fun `inferCategoryFromName — water`() {
        assertEquals(ProductCategory.BEVERAGE_WATER, inferCategoryFromName("Eau Évian"))
    }

    @Test fun `inferCategoryFromName — saumon is fish`() {
        assertEquals(ProductCategory.FISH, inferCategoryFromName("Filet de saumon"))
    }

    @Test fun `inferCategoryFromName — pâte à tartiner is snack_sweet not processed_meat`() {
        assertEquals(ProductCategory.SNACK_SWEET, inferCategoryFromName("Pâte à tartiner noisette"))
    }

    @Test fun `inferCategoryFromName — unknown returns OTHER`() {
        assertEquals(ProductCategory.OTHER, inferCategoryFromName("Produit inconnu XYZ123"))
    }

    // ============================================================
    // Nutritional density — omega-3 ingredient regex word boundaries
    // ============================================================

    // Regression: the omega-3 bonus regex had no \b around "lin"/"saumon",
    // so it matched as a substring inside unrelated words - "colin" (hake, a
    // completely different fish with no special omega-3 recognition here) via
    // "lin", and "saumonette" (dogfish, a cheap salmon substitute) via "saumon".
    @Test fun `Colin (hake) does not falsely earn the omega-3 bonus via the 'lin' substring`() {
        val p = product(
            name = "Colin sauce citron (test)",
            category = ProductCategory.FISH,
            novaClass = NovaClass.PROCESSED,
            ingredients = listOf(ingredient("colin"), ingredient("citron")),
            nutrition = nutrition(90.0, 1.0, 0.2, 1.0, 0.5, 0.0, 18.0, 0.3),
        )
        val pillar = scoreNutritionalDensity(p)
        assertNull("Colin should not trigger the omega-3 bonus", pillar.bonuses.firstOrNull { it.reason.contains("omega", ignoreCase = true) })
    }

    @Test fun `Saumonette does not falsely earn the omega-3 bonus via the 'saumon' substring`() {
        val p = product(
            name = "Saumonette (test)",
            category = ProductCategory.FISH,
            novaClass = NovaClass.PROCESSED,
            ingredients = listOf(ingredient("saumonette")),
            nutrition = nutrition(90.0, 1.0, 0.2, 1.0, 0.5, 0.0, 18.0, 0.3),
        )
        val pillar = scoreNutritionalDensity(p)
        assertNull("Saumonette should not trigger the omega-3 bonus", pillar.bonuses.firstOrNull { it.reason.contains("omega", ignoreCase = true) })
    }

    @Test fun `Real saumon and graine de lin still earn the omega-3 bonus`() {
        for (name in listOf("filet de saumon", "graine de lin", "chia", "sardine")) {
            val p = product(
                name = "Test $name",
                category = ProductCategory.OTHER,
                novaClass = NovaClass.PROCESSED,
                ingredients = listOf(ingredient(name)),
                nutrition = nutrition(90.0, 1.0, 0.2, 1.0, 0.5, 0.0, 18.0, 0.3),
            )
            val pillar = scoreNutritionalDensity(p)
            assertNotNull("\"$name\" should still trigger the omega-3 bonus", pillar.bonuses.firstOrNull { it.reason.contains("omega", ignoreCase = true) })
        }
    }

    // ============================================================
    // Ingredient integrity — hidden-sugar alias double-counting
    // ============================================================

    // Regression: HIDDEN_SUGAR_NAMES has nested substrings ("glucose" and
    // "sirop" both sit inside "sirop de glucose"), and the matching loop used
    // to add every containing alias independently - a single ingredient like
    // "sirop de glucose" inflated into 3 "distinct sugar sources", wrongly
    // triggering the >=2-sources deduction for what is really one ingredient.
    @Test fun `A single 'sirop de glucose' ingredient counts as one sugar source, not three`() {
        val p = product(
            name = "Test sirop de glucose",
            category = ProductCategory.OTHER,
            novaClass = NovaClass.PROCESSED,
            ingredients = listOf(ingredient("sirop de glucose"), ingredient("farine")),
            nutrition = nutrition(300.0, 1.0, 0.2, 60.0, 30.0, 1.0, 3.0, 0.5),
        )
        val pillar = scoreIngredientIntegrity(p)
        assertNull("A single hidden-sugar ingredient must not trigger the multi-source deduction",
            pillar.deductions.firstOrNull { it.reason.contains("sugar source", ignoreCase = true) || it.reason.contains("sources de sucre", ignoreCase = true) })
        assertNotNull("A single hidden-sugar ingredient should earn the single-source bonus instead",
            pillar.bonuses.firstOrNull { it.reason.contains("sugar", ignoreCase = true) || it.reason.contains("sucre", ignoreCase = true) })
    }

    @Test fun `Two genuinely distinct sugar ingredients still trigger the multi-source deduction`() {
        val p = product(
            name = "Test deux sucres",
            category = ProductCategory.OTHER,
            novaClass = NovaClass.PROCESSED,
            ingredients = listOf(ingredient("sirop de glucose"), ingredient("dextrose"), ingredient("caramel")),
            nutrition = nutrition(300.0, 1.0, 0.2, 60.0, 30.0, 1.0, 3.0, 0.5),
        )
        val pillar = scoreIngredientIntegrity(p)
        assertNotNull("Two genuinely distinct hidden-sugar aliases should still trigger the deduction",
            pillar.deductions.firstOrNull { it.reason.contains("sugar source", ignoreCase = true) || it.reason.contains("sources de sucre", ignoreCase = true) })
    }
}
