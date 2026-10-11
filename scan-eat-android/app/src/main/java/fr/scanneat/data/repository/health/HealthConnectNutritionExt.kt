package fr.scanneat.data.repository.health

import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import fr.scanneat.domain.model.MealSlot
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId

// Nutrition sync — extracted verbatim out of HealthConnectRepository, the cohesive
// "mirror a logged diary entry to Health Connect" concern. Extension function on
// HealthConnectRepository, same purely-structural split as
// HealthConnectWeightExt.kt/HealthConnectHydrationExt.kt/HealthConnectActivityExt.kt.
// HealthConnectRepository's own public API is unchanged.

/**
 * Mirrors a single logged diary entry as a NutritionRecord — ConsumptionRepository.log()/
 * logAll() had zero Health Connect wiring at all, unlike weight/hydration/activity, so a
 * day's actual food intake never left this app. A tiny (1s) interval ending at [loggedAt],
 * same convention as [writeHydrationDelta] - NutritionRecord requires startTime < endTime.
 */
suspend fun HealthConnectRepository.writeNutrition(
    loggedAt: Instant,
    mealSlot: MealSlot,
    name: String,
    kcal: Double,
    proteinG: Double,
    carbsG: Double,
    fatG: Double,
    saturatedFatG: Double,
    sugarsG: Double,
    fiberG: Double,
    saltG: Double,
) {
    try {
        if (!hasPermission(HealthConnectRepository.nutritionPermissions)) return
        val end = loggedAt
        val start = end.minusSeconds(1)
        val record = NutritionRecord(
            startTime = start,
            startZoneOffset = ZoneId.systemDefault().rules.getOffset(start),
            endTime = end,
            endZoneOffset = ZoneId.systemDefault().rules.getOffset(end),
            name = name,
            mealType = mealTypeFor(mealSlot),
            energy = Energy.kilocalories(kcal),
            protein = Mass.grams(proteinG),
            totalCarbohydrate = Mass.grams(carbsG),
            totalFat = Mass.grams(fatG),
            saturatedFat = Mass.grams(saturatedFatG),
            sugar = Mass.grams(sugarsG),
            dietaryFiber = Mass.grams(fiberG),
            // EU nutrition-label convention: sodium(g) ≈ salt(g) / 2.5 - saltG is what
            // NutritionPer100g/ConsumedNutrition track, Health Connect wants sodium.
            sodium = Mass.grams(saltG / 2.5),
        )
        client().insertRecords(listOf(record))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w(HealthConnectRepository.TAG, "writeNutrition failed", e)
    }
}

private fun mealTypeFor(slot: MealSlot): Int = when (slot) {
    MealSlot.BREAKFAST -> MealType.MEAL_TYPE_BREAKFAST
    MealSlot.LUNCH     -> MealType.MEAL_TYPE_LUNCH
    MealSlot.DINNER    -> MealType.MEAL_TYPE_DINNER
    MealSlot.SNACK     -> MealType.MEAL_TYPE_SNACK
}
