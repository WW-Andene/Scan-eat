package app.scaneat

class ExternalClients(private val catalog: NativeProductCatalog = NativeProductCatalog.default()) {
    fun productFromBarcode(barcode: String): ProductLookup {
        val digits = barcode.filter(Char::isDigit)
        if (digits.isBlank()) return ProductLookup(null, listOf("Code-barres vide"), "invalid_barcode")
        val product = catalog.findByBarcode(digits)
        return if (product != null) {
            ProductLookup(product, source = "native_catalog")
        } else {
            ProductLookup(
                null,
                listOf("Produit absent du catalogue Kotlin embarqué; aucun appel web externe n'a été effectué"),
                "native_catalog_not_found",
            )
        }
    }

    fun identifyMenu(rawJsonBody: String): Map<String, Any?> = nativeFeatureResponse(
        route = "identify-menu",
        rawJsonBody = rawJsonBody,
        title = "Analyse de menu native",
        suggestions = listOf(
            "Choisir une assiette avec légumes, protéine simple et féculent complet lorsque disponible",
            "Demander les sauces à part pour maîtriser sel, sucres et graisses saturées",
            "Éviter les plats frits ou panés si un objectif santé strict est actif",
        ),
    )

    fun identifyRecipe(rawJsonBody: String): Map<String, Any?> = nativeFeatureResponse(
        route = "identify-recipe",
        rawJsonBody = rawJsonBody,
        title = "Lecture de recette native",
        suggestions = listOf(
            "Privilégier les ingrédients bruts listés dans la recette",
            "Réduire le sel ajouté et remplacer une partie du sucre par des fruits lorsque pertinent",
        ),
    )

    fun suggestRecipes(rawJsonBody: String): Map<String, Any?> = nativeFeatureResponse(
        route = "suggest-recipes",
        rawJsonBody = rawJsonBody,
        title = "Suggestions recettes natives",
        suggestions = listOf(
            "Bol lentilles, légumes rôtis et yaourt citronné",
            "Omelette aux herbes avec salade de saison",
            "Porridge avoine, pomme et graines",
        ),
    )

    fun suggestFromPantry(rawJsonBody: String): Map<String, Any?> = nativeFeatureResponse(
        route = "suggest-from-pantry",
        rawJsonBody = rawJsonBody,
        title = "Cuisine du placard native",
        suggestions = listOf(
            "Assembler une base céréale complète + légumineuse + légume",
            "Ajouter une source de protéines et une sauce simple maison",
            "Planifier les restes en portions datées pour limiter le gaspillage",
        ),
    )

    fun fetchRecipe(rawJsonBody: String): Map<String, Any?> = nativeFeatureResponse(
        route = "fetch-recipe",
        rawJsonBody = rawJsonBody,
        title = "Import recette natif",
        suggestions = listOf(
            "Coller les ingrédients et étapes dans l'application native pour les conserver localement",
            "Vérifier manuellement portions, allergènes et valeurs nutritionnelles avant journalisation",
        ),
    )

    private fun nativeFeatureResponse(route: String, rawJsonBody: String, title: String, suggestions: List<String>): Map<String, Any?> = mapOf(
        "route" to route,
        "native" to true,
        "title" to title,
        "inputBytes" to rawJsonBody.encodeToByteArray().size,
        "suggestions" to suggestions,
        "warnings" to listOf("Réponse déterministe Kotlin native: aucun proxy web, fetch navigateur, Vercel ou LLM distant n'est utilisé"),
    )
}

class NativeProductCatalog(private val products: Map<String, ProductInput>) {
    fun findByBarcode(barcode: String): ProductInput? = products[barcode]

    companion object {
        fun default() = NativeProductCatalog(
            listOf(
                ProductInput(
                    name = "Yaourt nature",
                    category = "yogurt",
                    nova_class = 1,
                    ingredients = listOf(Ingredient("lait"), Ingredient("ferments lactiques")),
                    nutrition = NutritionPer100g(energy_kcal = 65.0, protein_g = 5.0, sugars_g = 4.0, saturated_fat_g = 1.0, salt_g = 0.1),
                    barcode = "3274080005003",
                ),
                ProductInput(
                    name = "Flocons d'avoine",
                    category = "breakfast_cereal",
                    nova_class = 1,
                    ingredients = listOf(Ingredient("avoine complète")),
                    nutrition = NutritionPer100g(energy_kcal = 370.0, protein_g = 13.0, carbs_g = 60.0, sugars_g = 1.0, fiber_g = 10.0, fat_g = 7.0, saturated_fat_g = 1.2, salt_g = 0.02),
                    barcode = "3017620422003",
                ),
                ProductInput(
                    name = "Boisson cola",
                    category = "beverage_soft",
                    nova_class = 4,
                    ingredients = listOf(Ingredient("eau gazéifiée"), Ingredient("sucre"), Ingredient("colorant caramel", e_number = "E150D", category = "additive")),
                    nutrition = NutritionPer100g(energy_kcal = 42.0, carbs_g = 10.6, sugars_g = 10.6, salt_g = 0.0),
                    barcode = "5449000000996",
                ),
            ).associateBy { requireNotNull(it.barcode) },
        )
    }
}

fun normalizeImages(req: ScoreRequest): List<ImagePayload> = when {
    req.images.isNotEmpty() -> req.images.filter { it.base64.isNotBlank() }
    !req.imageBase64.isNullOrBlank() -> listOf(ImagePayload(req.imageBase64, req.mime ?: "image/jpeg"))
    else -> emptyList()
}
