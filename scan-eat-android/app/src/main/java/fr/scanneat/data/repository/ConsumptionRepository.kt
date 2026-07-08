package fr.scanneat.data.repository

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import fr.scanneat.data.local.db.ConsumptionDao
import fr.scanneat.data.local.db.ConsumptionEntity
import fr.scanneat.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConsumptionRepository @Inject constructor(
    private val dao: ConsumptionDao,
    private val moshi: Moshi,
) {
    private val nutritionAdapter = moshi.adapter(NutritionPer100g::class.java)

    fun observeDay(date: LocalDate, profileId: String = "default"): Flow<DailySummary> =
        dao.observeByDate(date.toString(), profileId).map { entities ->
            val entries = entities.mapNotNull { it.toDomain() }
            DailySummary(
                date    = date,
                entries = entries,
                totals  = entries.fold(ConsumedNutrition.ZERO) { acc, e -> acc + e.consumed },
            )
        }

    suspend fun log(entry: DiaryEntry) {
        dao.insert(entry.toEntity())
    }

    suspend fun update(entry: DiaryEntry) {
        dao.update(entry.toEntity())
    }

    suspend fun delete(id: Long) = dao.delete(id)

    /** Flow-based range query — use in ViewModels instead of suspend getRange(). */
    fun observeRange(from: LocalDate, to: LocalDate, profileId: String = "default"): Flow<List<DiaryEntry>> =
        dao.observeRange(from.toString(), to.toString(), profileId)
            .map { list -> list.map { it.toDomain() } }

    suspend fun getRange(from: LocalDate, to: LocalDate, profileId: String = "default"): List<DiaryEntry> =
        dao.getRange(from.toString(), to.toString(), profileId).mapNotNull { it.toDomain() }

    // ---- Mapping ----

    private fun DiaryEntry.toEntity() = ConsumptionEntity(
        id          = id,
        date        = date.toString(),
        mealSlot    = mealSlot.name,
        loggedAt    = loggedAt.toEpochSecond(ZoneOffset.UTC) * 1000,
        productName = productName,
        barcode     = barcode,
        portionG    = portionG,
        nutritionJson = nutritionAdapter.toJson(nutrition),
        source      = source.name,
        profileId   = profileId,
    )

    private fun ConsumptionEntity.toDomain(): DiaryEntry? = runCatching {
        DiaryEntry(
            id          = id,
            date        = LocalDate.parse(date),
            mealSlot    = MealSlot.valueOf(mealSlot),
            loggedAt    = LocalDateTime.ofEpochSecond(loggedAt / 1000, 0, ZoneOffset.UTC),
            productName = productName,
            barcode     = barcode,
            portionG    = portionG,
            nutrition   = nutritionAdapter.fromJson(nutritionJson)!!,
            source      = ScanSource.valueOf(source),
            profileId   = profileId,
        )
    }.getOrNull()
}
