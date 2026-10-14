package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.*
import org.junit.Assert.*
import org.junit.Test

/** Roadmap S9: Henry equations under 18, EFSA calcium by age, IBD flare-only rules. */
class S9PediatricIbdTest {
    @Test fun `children use Henry, adults Mifflin`() {
        val boy = Profile(sex = Sex.MALE, ageYears = 8, heightCm = 128.0, weightKg = 26.0)
        assertEquals((0.0937 * 26 + 2.15) * 239.006, bmrMifflinStJeor(boy)!!, 0.5)
        val adult = Profile(sex = Sex.MALE, ageYears = 30, heightCm = 180.0, weightKg = 80.0)
        assertEquals(1780.0, bmrMifflinStJeor(adult)!!, 0.01)
    }

    @Test fun `calcium target follows EFSA age bands`() {
        fun ca(age: Int) = dailyTargets(Profile(sex = Sex.FEMALE, ageYears = age, heightCm = 160.0, weightKg = 50.0))!!.calciumMgTarget
        assertEquals(800.0, ca(9), 0.0); assertEquals(1150.0, ca(15), 0.0); assertEquals(1000.0, ca(20), 0.0); assertEquals(950.0, ca(40), 0.0)
    }

    @Test fun `high fibre penalised for IBD only during a flare`() {
        val p = Product(name = "Pain complet", category = ProductCategory.OTHER, novaClass = NovaClass.PROCESSED,
            ingredients = listOf(Ingredient("farine complète")),
            nutrition = NutritionPer100g(energyKcal = 250.0, fatG = 3.0, saturatedFatG = 0.5, carbsG = 45.0, sugarsG = 3.0, fiberG = 8.0, proteinG = 9.0, saltG = 1.0))
        val audit = scoreProduct(p)
        val remission = computePersonalScore(audit, p, Profile(healthConditions = setOf("crohn_ibd")))
        val flare = computePersonalScore(audit, p, Profile(healthConditions = setOf("crohn_ibd", "ibd_flare")))
        assertFalse(remission.adjustments.any { "fibres" in it.reason && it.points < 0 })
        assertTrue(flare.adjustments.any { "fibres" in it.reason && it.points < 0 })
    }
}
