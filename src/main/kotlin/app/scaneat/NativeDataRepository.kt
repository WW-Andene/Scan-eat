package app.scaneat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object NativeDataRepository {
    private val json = Json { ignoreUnknownKeys = true }

    val foodDatabase: JsonObject by lazy { resourceJson("food-db") }
    val activityMetTable: JsonObject by lazy { resourceJson("activity-met") }
    val profileDefaults: JsonObject by lazy { resourceJson("profile-defaults") }
    val pairings: JsonObject by lazy { resourceJson("pairings") }

    fun foodCount(): Int = foodItems().size

    fun searchFood(query: String, limit: Int = 6): List<NativeFoodItem> {
        val needle = normalize(query)
        if (needle.isBlank()) return emptyList()
        return foodItems()
            .mapNotNull { element ->
                val obj = element.jsonObject
                val name = obj.string("name") ?: return@mapNotNull null
                val aliases = obj["aliases"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNullCompat() }.orEmpty()
                val haystack = (listOf(name) + aliases).joinToString(" ") { normalize(it) }
                if (needle in haystack) {
                    NativeFoodItem(
                        name = name,
                        kcal = obj.double("kcal"),
                        protein_g = obj.double("protein_g"),
                        carbs_g = obj.double("carbs_g"),
                        fat_g = obj.double("fat_g"),
                        aliases = aliases,
                    )
                } else null
            }
            .take(limit.coerceAtLeast(0))
    }

    fun activityTypes(): Set<String> = activityMetTable["items"]?.jsonObject?.keys.orEmpty()

    fun macroPresetKeys(): Set<String> = profileDefaults["macroPresets"]?.jsonObject?.keys.orEmpty()

    fun pairingCount(): Int = pairings["pairings"]?.jsonObject?.size ?: 0

    fun pairingsFor(name: String, limit: Int = 6): List<String> {
        val pairs = pairings["pairings"]?.jsonObject ?: return emptyList()
        val normalized = normalize(name)
        val key = pairs.keys.firstOrNull { key ->
            normalize(key) == normalized || normalize(pairs[key]?.jsonObject?.string("fr").orEmpty()) == normalized
        } ?: normalizePairingName(name)
        return pairs[key]
            ?.jsonObject
            ?.get("pairs")
            ?.jsonArray
            ?.mapNotNull { entry -> entry.jsonObject.string("fr") ?: entry.jsonObject.string("b") }
            ?.take(limit.coerceAtLeast(0))
            .orEmpty()
    }

    private fun foodItems(): JsonArray = foodDatabase["items"]?.jsonArray ?: JsonArray(emptyList())

    private fun resourceJson(name: String): JsonObject {
        val path = "native-data/$name.json"
        val text = requireNotNull(NativeDataRepository::class.java.classLoader.getResource(path)) { "Missing native resource $path" }.readText()
        return json.parseToJsonElement(text).jsonObject
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNullCompat()
    private fun JsonObject.double(key: String): Double = this[key]?.jsonPrimitive?.doubleOrNull ?: 0.0

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullCompat(): String? = if (isString) content else null

    private fun normalize(value: String): String = value
        .lowercase()
        .replace('é', 'e')
        .replace('è', 'e')
        .replace('ê', 'e')
        .replace('ë', 'e')
        .replace('à', 'a')
        .replace('â', 'a')
        .replace('î', 'i')
        .replace('ï', 'i')
        .replace('ô', 'o')
        .replace('ù', 'u')
        .replace('û', 'u')
        .replace('ç', 'c')

    private fun normalizePairingName(value: String): String = normalize(value).replace(Regex("[^a-z0-9]+"), "_").trim('_')
}

@kotlinx.serialization.Serializable
data class NativeFoodItem(
    val name: String,
    val kcal: Double,
    val protein_g: Double,
    val carbs_g: Double,
    val fat_g: Double,
    val aliases: List<String> = emptyList(),
)
