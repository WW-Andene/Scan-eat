package fr.scanneat.presentation.profile

import fr.scanneat.domain.engine.scoring.DietKey
import fr.scanneat.domain.model.Profile
import org.junit.Assert.*
import org.junit.Test

/** Roadmap N8: a received profile keeps its shared allergens and diet. */
class SharedProfileReadOnlyTest {
    private val stored = Profile(id = "p2", name = "Léa", allergens = setOf("peanuts"), diet = DietKey.entries.last(), sharedReadOnly = true)

    @Test fun `shared fields are kept, others editable`() {
        val saved = keepSharedFields(stored, stored.copy(allergens = emptySet(), diet = DietKey.NONE, name = "Léa B", sharedReadOnly = false))
        assertEquals(setOf("peanuts"), saved.allergens)
        assertEquals(stored.diet, saved.diet)
        assertEquals("Léa B", saved.name)
        assertTrue(saved.sharedReadOnly)
    }

    @Test fun `own profile stays fully editable`() {
        val own = stored.copy(sharedReadOnly = false)
        assertEquals(emptySet<String>(), keepSharedFields(own, own.copy(allergens = emptySet())).allergens)
    }
}
