package fr.scanneat.domain.engine.scoring

import fr.scanneat.domain.model.Profile
import fr.scanneat.domain.model.Sex
import org.junit.Assert.*
import org.junit.Test

/** Roadmap S9/S10 regressions. */
class ScoreConsistencyS9S10Test {

    private val adult = Profile(sex = Sex.FEMALE, ageYears = 35, heightCm = 165.0, weightKg = 60.0)

    @Test
    fun `omega-3 bonus only for walnut, not coconut cashew nutmeg pecan`() {
        listOf("noix", "Noix", "cerneaux de noix", "noix de Grenoble", "graines de lin", "saumon")
            .forEach { assertTrue(it, OMEGA3_INGREDIENT.containsMatchIn(it)) }
        listOf("noix de coco", "noix de cajou", "noix de muscade", "noix de pécan", "noix du Brésil", "noix de macadamia", "linge")
            .forEach { assertFalse(it, OMEGA3_INGREDIENT.containsMatchIn(it)) }
    }

    @Test
    fun `fiber target is EFSA 25 g whatever the diet`() {
        DietKey.entries.forEach { d ->
            assertEquals(d.name, 25.0, dailyTargets(adult.copy(diet = d))!!.fiberGTarget, 0.0)
        }
    }

    @Test
    fun `hypertension salt cap follows ESH 2023 (5 g)`() {
        assertEquals(5.0, dailyTargets(adult.copy(healthConditions = setOf("hypertension")))!!.saltGMax, 0.0)
    }
}
