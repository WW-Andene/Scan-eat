package fr.scanneat.data.repository.scan

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import fr.scanneat.data.local.db.scan.ScanHistoryDao
import fr.scanneat.data.local.db.scan.ScanHistoryEntity
import fr.scanneat.data.local.prefs.ApiMode
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.remote.api.*
import fr.scanneat.domain.engine.dashboard.*
import fr.scanneat.domain.engine.nutrition.*
import fr.scanneat.domain.engine.planning.*
import fr.scanneat.domain.engine.scoring.*
import fr.scanneat.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown when a barcode has no Open Food Facts entry and no photos were supplied to fall back on. */
class ProductNotFoundException(message: String) : Exception(message)

@Singleton
class ScanRepository @Inject constructor(
    private val offApi: OpenFoodFactsApi,
    private val groqApi: GroqApi,
    private val dao: ScanHistoryDao,
    private val prefs: UserPreferences,
    private val ocrParser: OcrParser,
    private val okHttpClient: OkHttpClient,   // injected directly — no unsafe cast
    private val moshi: Moshi,                  // singleton from AppModule
) {
    private val productAdapter = moshi.adapter(Product::class.java)
    private val auditAdapter   = moshi.adapter(ScoreAudit::class.java)

    @Volatile private var _serverApi: ServerScanApi? = null
    @Volatile private var _serverUrl: String = ""

    private fun serverApi(url: String): ServerScanApi {
        val normUrl = if (url.endsWith("/")) url else "$url/"
        if (_serverApi == null || _serverUrl != normUrl) {
            _serverApi = Retrofit.Builder()
                .baseUrl(normUrl)
                .client(okHttpClient)          // safe: directly injected OkHttpClient
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(ServerScanApi::class.java)
            _serverUrl = normUrl
        }
        return _serverApi!!
    }

    // ---- History ----

    fun observeHistory(limit: Int = 50): Flow<List<ScanResult>> =
        dao.observeRecent(limit = limit).map { entities ->
            entities.mapNotNull { it.toDomain() }
        }

    suspend fun getById(id: Long): ScanResult? = dao.findById(id)?.toDomain()

    suspend fun getCachedByBarcode(barcode: String): ScanResult? =
        dao.findByBarcode(barcode)?.toDomain()

    suspend fun persist(result: ScanResult): Long =
        dao.insert(ScanHistoryEntity(
            barcode     = result.barcode,
            productName = result.product.name,
            score       = result.audit.score,
            grade       = result.audit.grade.label,
            category    = result.product.category.key,
            sourceJson  = result.source.name,
            productJson = productAdapter.toJson(result.product),
            auditJson   = auditAdapter.toJson(result.audit),
            scannedAt   = System.currentTimeMillis(),
        ))

    // ---- Score from barcode ----

    suspend fun scoreBarcode(
        barcode: String,
        images: List<ImagePayload> = emptyList(),
        lang: String = "fr",
    ): Result<Pair<ScanResult, Long>> = runCatching {
        val apiMode   = prefs.apiMode.first()
        val apiKey    = prefs.groqApiKey.first()
        val serverUrl = prefs.serverUrl.first()
        val model     = prefs.groqModel.first().ifBlank { DEFAULT_MODEL }

        getCachedByBarcode(barcode)?.let { cached ->
            return@runCatching Pair(cached, persist(cached))
        }

        val result = when (apiMode) {
            ApiMode.SERVER -> scoreViaServer(serverUrl, apiKey, images, barcode)
            ApiMode.DIRECT -> scoreDirectBarcode(barcode, images, apiKey, lang, model)
        }
        Pair(result, persist(result))
    }

    suspend fun scoreFromImages(
        images: List<ImagePayload>,
        lang: String = "fr",
    ): Result<Pair<ScanResult, Long>> = runCatching {
        val apiMode   = prefs.apiMode.first()
        val apiKey    = prefs.groqApiKey.first()
        val serverUrl = prefs.serverUrl.first()
        val model     = prefs.groqModel.first().ifBlank { DEFAULT_MODEL }

        val result = when (apiMode) {
            ApiMode.SERVER -> scoreViaServer(serverUrl, apiKey, images, barcode = null)
            ApiMode.DIRECT -> {
                if (apiKey.isBlank()) error("Groq API key not configured")
                val parsed = ocrParser.parseLabel(images, apiKey, model = model, lang = lang)
                ScanResult(
                    product  = parsed.product,
                    audit    = scoreProduct(parsed.product),
                    warnings = parsed.warnings,
                    source   = ScanSource.LLM,
                    barcode  = parsed.barcode,
                )
            }
        }
        Pair(result, persist(result))
    }

    // ---- Server mode ----

    private suspend fun scoreViaServer(
        serverUrl: String,
        apiKey: String,
        images: List<ImagePayload>,
        barcode: String?,
    ): ScanResult {
        if (serverUrl.isBlank()) error("Server URL not configured")
        val resp = serverApi(serverUrl).score(
            groqKey = apiKey.takeIf { it.isNotBlank() },
            request = ServerScoreRequest(
                images  = images.map { ServerImageDto(it.base64, it.mime) },
                barcode = barcode,
            ),
        )
        return resp.toDomain()
    }

    // ---- Direct mode ----

    private suspend fun scoreDirectBarcode(
        barcode: String,
        images: List<ImagePayload>,
        apiKey: String,
        lang: String,
        model: String,
    ): ScanResult {
        val offResponse = try {
            offApi.getProduct(barcode)
        } catch (e: HttpException) {
            if (e.code() == 404) null else throw e
        }
        val offProduct  = offResponse?.product?.let { dto ->
            mapOffProduct(OffProductResponse(
                productName       = dto.productName,
                productNameFr     = dto.productNameFr,
                genericNameFr     = dto.genericNameFr,
                brands            = dto.brands,
                categoriesTags    = dto.categoriesTags,
                ingredientsTextFr = dto.ingredientsTextFr,
                ingredientsText   = dto.ingredientsText,
                novaGroup         = dto.novaGroup,
                nutriments        = dto.nutriments?.mapValues { it.value },
                labelsTags        = dto.labelsTags,
                origins           = dto.origins,
                countriesTags     = dto.countriesTags,
                quantity          = dto.quantity,
                ecoscoreGrade     = dto.ecoscoreGrade,
                ecoscoreScore     = dto.ecoscoreScore,
                nutritionGrades   = dto.nutritionGrades,
            ))
        }

        val (finalProduct, source, warnings) = when {
            offProduct != null && isOffSparse(offProduct) && images.isNotEmpty() && apiKey.isNotBlank() -> {
                val parsed    = ocrParser.parseLabel(images, apiKey, model = model, lang = lang)
                val merged    = mergeOffWithLlm(offProduct, parsed.product)
                val conflicts = detectSourceConflicts(offProduct, parsed.product)
                Triple(merged, ScanSource.MERGED,
                    parsed.warnings + conflicts.map { "Conflict: ${it.field} OFF=${it.offValue} LLM=${it.llmValue}" })
            }
            offProduct != null -> Triple(offProduct, ScanSource.OPEN_FOOD_FACTS, emptyList())
            images.isNotEmpty() && apiKey.isNotBlank() -> {
                val parsed = ocrParser.parseLabel(images, apiKey, model = model, lang = lang)
                Triple(parsed.product, ScanSource.LLM, parsed.warnings)
            }
            else -> throw ProductNotFoundException("Produit introuvable dans Open Food Facts — ajoutez une photo pour continuer")
        }

        val audit = scoreProduct(finalProduct)
        return ScanResult(product = finalProduct, audit = audit, warnings = warnings, source = source, barcode = barcode)
    }

    // ---- Server response → domain ----

    private fun ServerScoreResponse.toDomain(): ScanResult {
        val p = product
        val nutrition = NutritionPer100g(
            energyKcal    = p.nutrition.energyKcal,
            fatG          = p.nutrition.fatG,
            saturatedFatG = p.nutrition.saturatedFatG,
            carbsG        = p.nutrition.carbsG,
            sugarsG       = p.nutrition.sugarsG,
            addedSugarsG  = p.nutrition.addedSugarsG,
            fiberG        = p.nutrition.fiberG,
            proteinG      = p.nutrition.proteinG,
            saltG         = p.nutrition.saltG,
            transFatG     = p.nutrition.transFatG,
            ironMg        = p.nutrition.ironMg,
            calciumMg     = p.nutrition.calciumMg,
            magnesiumMg   = p.nutrition.magnesiumMg,
            potassiumMg   = p.nutrition.potassiumMg,
            zincMg        = p.nutrition.zincMg,
            vitCMg        = p.nutrition.vitCMg,
            vitDUg        = p.nutrition.vitDUg,
            b12Ug         = p.nutrition.b12Ug,
        )
        val product = Product(
            name          = p.name,
            category      = ProductCategory.fromKey(p.category),
            novaClass     = NovaClass.fromInt(p.novaClass),
            ingredients   = p.ingredients.map { i ->
                Ingredient(name = i.name, percentage = i.percentage, eNumber = i.eNumber,
                    category = when (i.category?.lowercase()) {
                        "additive"       -> IngredientCategory.ADDITIVE
                        "processing_aid" -> IngredientCategory.PROCESSING_AID
                        else             -> IngredientCategory.FOOD
                    })
            },
            nutrition     = nutrition,
            organic       = p.organic, wholeGrainPrimary = p.wholeGrainPrimary,
            fermented     = p.fermented, hasHealthClaims = p.hasHealthClaims,
            hasMisleadingMarketing = p.hasMisleadingMarketing,
            namedOils     = p.namedOils, origin = p.origin, weightG = p.weightG,
            ecoscoreGrade = p.ecoscoreGrade, ecoscoreValue = p.ecoscoreValue,
            nutriscoreGrade = p.nutriscoreGrade,
        )
        val fullAudit = scoreProduct(product).copy(
            redFlags = audit.redFlags, greenFlags = audit.greenFlags)
        return ScanResult(product = product, audit = fullAudit, warnings = warnings,
            source = when (source) {
                "openfoodfacts" -> ScanSource.OPEN_FOOD_FACTS
                "merged"        -> ScanSource.MERGED
                else            -> ScanSource.LLM
            }, barcode = barcode)
    }

    // ---- Entity → domain ----

    private fun ScanHistoryEntity.toDomain(): ScanResult? = runCatching {
        ScanResult(
            product  = productAdapter.fromJson(productJson)!!,
            audit    = auditAdapter.fromJson(auditJson)!!,
            warnings = emptyList(),
            source   = ScanSource.valueOf(sourceJson),
            barcode  = barcode,
            dbId     = id,
        )
    }.getOrNull()
}
