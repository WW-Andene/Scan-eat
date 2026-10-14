package fr.scanneat.domain.engine.symptom

import fr.scanneat.domain.model.DiaryEntry
import fr.scanneat.domain.model.Ingredient
import fr.scanneat.domain.model.MealSlot
import fr.scanneat.domain.model.NutritionPer100g
import fr.scanneat.domain.model.ScanSource
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** Roadmap D4. */
class EliminationTrialTest {
    private val start = LocalDate.of(2026, 10, 1)
    private val n = NutritionPer100g(energyKcal = 50.0, fatG = 1.0, saturatedFatG = 0.5, carbsG = 5.0, sugarsG = 5.0, fiberG = 0.0, proteinG = 3.0, saltG = 0.1)
    private fun entry(d: LocalDate, name: String, vararg ing: String) =
        DiaryEntry(date = d, mealSlot = MealSlot.entries.first(), productName = name, portionG = 100.0, nutrition = n,
            source = ScanSource.MANUAL, ingredients = ing.map { Ingredient(it) })

    @Test fun `rates per window, slips detected, phases`() {
        val t = EliminationTrial("lactose", start)
        val symptoms = (1..7).map { start.minusDays(it.toLong() * 2) }.toSet() + start.plusDays(3)
        val diary = listOf(entry(start.plusDays(2), "Yaourt nature", "lait"), entry(start.plusDays(2), "Riz", "riz"))
        val r = trialReport(t, symptoms, diary, start.plusDays(9))
        assertEquals(TrialPhase.AVOIDANCE, r.phase); assertEquals(5, r.daysLeftInPhase)
        assertEquals(50, r.baseline.percent)       // 7 symptom days / 14
        assertEquals(10, r.avoidance.days); assertEquals(1, r.avoidance.symptomDays)
        assertEquals(1, r.slips.size); assertTrue("Yaourt" in r.slips.single())
        assertEquals(TrialPhase.FINISHED, trialPhase(t, start.plusDays(30)))
    }
}
