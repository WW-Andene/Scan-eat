package fr.scanneat.data.repository.nutrition

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.scanneat.data.local.db.consumption.ConsumptionDao
import fr.scanneat.data.local.db.consumption.ConsumptionEntity
import fr.scanneat.data.local.db.toEpochMillisUtc
import fr.scanneat.data.local.db.toIsoString
import fr.scanneat.data.local.db.toLocalDate
import fr.scanneat.data.local.db.toLocalDateTimeUtc
import fr.scanneat.data.repository.expense.PriceRepository
import fr.scanneat.data.repository.health.FastingRepository
import fr.scanneat.data.repository.health.HealthConnectRepository
import fr.scanneat.data.repository.health.HydrationRepository
import fr.scanneat.data.repository.health.writeNutrition
import fr.scanneat.domain.model.*
import fr.scanneat.presentation.widget.TodayWidget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConsumptionRepository @Inject constructor(
    private val dao: ConsumptionDao,
    private val moshi: Moshi,
    private val healthConnect: HealthConnectRepository,
    private val priceRepo: PriceRepository,
    private val hydrationRepo: HydrationRepository,
    private val fastingRepo: FastingRepository,
    @ApplicationContext private val context: Context,
) {
    private val nutritionAdapter = moshi.adapter(NutritionPer100g::class.java)
    private val ingredientsAdapter = moshi.adapter<List<Ingredient>>(
        com.squareup.moshi.Types.newParameterizedType(List::class.java, Ingredient::class.java)
    )

    fun observeDay(date: LocalDate, profileId: String = "default"): Flow<DailySummary> =
        dao.observeByDate(date.toString(), profileId).map { entities ->
            val entries = entities.mapNotNull { it.toDomain() }
            DailySummary(
                date    = date,
                entries = entries,
                totals  = entries.fold(ConsumedNutrition.ZERO) { acc, e -> acc + e.consumed },
            )
        }

    /** Returns true if an active fast was running at the moment this was logged -
     *  Fasting and the Diary previously had zero cross-reference (R&D audit
     *  finding), so logging food mid-fast never surfaced any signal at all,
     *  not even an informational one. Non-blocking by design: unlike the
     *  pregnancy alcohol veto elsewhere in this app, breaking a fast is the
     *  user's own call, not something to gate - the caller decides whether/how
     *  to show this (see ResultViewModel/FoodSearchViewModel/DiaryViewModel). */
    suspend fun log(entry: DiaryEntry): Boolean {
        dao.insert(entry.toEntity())
        dao.trim(MAX_HISTORY_ROWS, entry.profileId)
        priceRepo.deductStock(entry.barcode, entry.portionG, entry.profileId)
        mirrorToHealthConnect(entry)
        mirrorToHydration(entry)
        refreshWidget()
        return fastingRepo.state.first()?.isActive == true
    }

    /** Atomic multi-entry write — use when logging a template or recipe that expands to several entries. */
    suspend fun logAll(entries: List<DiaryEntry>): Boolean {
        dao.insertAll(entries.map { it.toEntity() })
        entries.map { it.profileId }.distinct().forEach { dao.trim(MAX_HISTORY_ROWS, it) }
        entries.forEach { priceRepo.deductStock(it.barcode, it.portionG, it.profileId) }
        entries.forEach { mirrorToHealthConnect(it) }
        entries.forEach { mirrorToHydration(it) }
        refreshWidget()
        return fastingRepo.state.first()?.isActive == true
    }

    // User-reported: a bottled water product logged via barcode scan or the
    // journal never showed up in the dedicated Hydration tab - the two
    // systems (this Room table vs. HydrationRepository's own DataStore) had
    // zero cross-wiring. portionG is used as mL directly (water's density is
    // ~1 g/mL, close enough for a self-reported intake estimate the same way
    // HYD_GLASS_ML's own 250 mL is an estimate, not a lab measurement).
    private suspend fun mirrorToHydration(entry: DiaryEntry) {
        if (entry.category != ProductCategory.BEVERAGE_WATER) return
        hydrationRepo.add(entry.date, entry.portionG.toInt())
    }

    companion object {
        /** Same retention rationale as ScanRepository.MAX_HISTORY_ROWS - unbounded until
         *  this audit fix, unlike scan_history/scan_score_history which already trim. */
        const val MAX_HISTORY_ROWS = 5000
    }

    // TodayWidget's kcal/streak/macro figures previously only refreshed on the platform's
    // own 30-minute updatePeriodMillis (see today_widget_info.xml) or an explicit resize/
    // re-add - logging food from anywhere in the app (Diary, Result, Recipes, Templates,
    // Dashboard's gap suggestions) left the home-screen widget showing stale numbers for up
    // to half an hour. Every logging/edit path funnels through this repository, so refreshing
    // here (rather than hunting down every ViewModel call site) can't miss one. Best-effort:
    // no widget instance on the home screen is the common case, not an error worth surfacing.
    private suspend fun refreshWidget() {
        runCatching { TodayWidget().updateAll(context) }
            .onFailure { e -> if (e is kotlinx.coroutines.CancellationException) throw e }
    }

    private suspend fun mirrorToHealthConnect(entry: DiaryEntry) {
        val c = entry.consumed
        healthConnect.writeNutrition(
            // entry.loggedAt is a real local wall-clock LocalDateTime (see its
            // LocalDateTime.now() default), not a UTC one - toEpochMillisUtc()
            // treats whatever LocalDateTime it's given as if it WERE UTC, which
            // is the correct/intentional convention for the Room storage
            // round-trip (toEntity()/toDomain() below), but wrong here: feeding
            // that into Instant.ofEpochMilli() produced an absolute instant
            // offset from the real moment by the device's UTC offset (e.g. a
            // 14:00 Paris lunch landed in Health Connect as 16:00 UTC+2 -
            // display code would then show it as 16:00 local, two hours late).
            // atZone(systemDefault()).toInstant() is the correct local-wall-
            // clock -> real-instant conversion, matching how writeWeight/
            // writeActivity already anchor their own instants to systemDefault().
            loggedAt = entry.loggedAt.atZone(ZoneId.systemDefault()).toInstant(),
            mealSlot = entry.mealSlot,
            name     = entry.productName,
            kcal     = c.energyKcal,
            proteinG = c.proteinG,
            carbsG   = c.carbsG,
            fatG     = c.fatG,
            saturatedFatG = c.saturatedFatG,
            sugarsG  = c.sugarsG,
            fiberG   = c.fiberG,
            saltG    = c.saltG,
        )
    }

    suspend fun update(entry: DiaryEntry) {
        dao.update(entry.toEntity())
        refreshWidget()
    }

    suspend fun delete(id: Long) {
        dao.delete(id)
        refreshWidget()
    }

    fun observeRange(from: LocalDate, to: LocalDate, profileId: String = "default"): Flow<List<DiaryEntry>> =
        dao.observeRange(from.toString(), to.toString(), profileId)
            .map { list -> list.mapNotNull { it.toDomain() } }

    /** All distinct dates with at least one logged entry, across full history — for
     * logStreakDays/longestLogStreak, which need every date ever logged, not just
     * whatever a fixed-size observeRange window happens to cover. */
    suspend fun getAllLoggedDates(profileId: String = "default"): Set<LocalDate> =
        dao.getAllLoggedDates(profileId).mapNotNullTo(mutableSetOf()) { runCatching { it.toLocalDate() }.getOrNull() }

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
        ingredientsJson = ingredientsAdapter.toJson(ingredients),
        category    = category.name,
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
            ingredients = ingredientsAdapter.fromJson(ingredientsJson) ?: emptyList(),
            category    = category?.let { runCatching { ProductCategory.valueOf(it) }.getOrNull() } ?: ProductCategory.OTHER,
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
