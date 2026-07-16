package fr.scanneat.data.repository.nutrition

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import fr.scanneat.data.local.db.consumption.ConsumptionDao
import fr.scanneat.data.local.db.consumption.ConsumptionEntity
import fr.scanneat.data.local.db.toEpochMillisUtc
import fr.scanneat.data.local.db.toIsoString
import fr.scanneat.data.local.db.toLocalDate
import fr.scanneat.data.local.db.toLocalDateTimeUtc
import fr.scanneat.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
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

    /** Atomic multi-entry write — use when logging a template or recipe that expands to several entries. */
    suspend fun logAll(entries: List<DiaryEntry>) {
        dao.insertAll(entries.map { it.toEntity() })
    }

    suspend fun update(entry: DiaryEntry) {
        dao.update(entry.toEntity())
    }

    suspend fun delete(id: Long) = dao.delete(id)

    fun observeRange(from: LocalDate, to: LocalDate, profileId: String = "default"): Flow<List<DiaryEntry>> =
        dao.observeRange(from.toString(), to.toString(), profileId)
            .map { list -> list.mapNotNull { it.toDomain() } }

    // ---- Mapping ----

    private fun DiaryEntry.toEntity() = ConsumptionEntity(
        id          = id,
        date        = date.toIsoString(),
        mealSlot    = mealSlot.name,
        loggedAt    = loggedAt.toEpochMillisUtc(),
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
            date        = date.toLocalDate(),
            mealSlot    = MealSlot.valueOf(mealSlot),
            loggedAt    = loggedAt.toLocalDateTimeUtc(),
            productName = productName,
            barcode     = barcode,
            portionG    = portionG,
            nutrition   = nutritionAdapter.fromJson(nutritionJson)!!,
            source      = ScanSource.valueOf(source),
            profileId   = profileId,
        )
    }.onFailure {
        // A parse failure here silently drops this row from every diary/dashboard
        // total (mapNotNull filters it out) with zero user-visible indication -
        // the row itself isn't deleted, but the user's calorie tracking would be
        // silently wrong. Logging at least makes it discoverable during QA
        // instead of a completely invisible failure.
        android.util.Log.w("ConsumptionRepository", "Failed to parse consumption row id=$id date=$date", it)
    }.getOrNull()
}
