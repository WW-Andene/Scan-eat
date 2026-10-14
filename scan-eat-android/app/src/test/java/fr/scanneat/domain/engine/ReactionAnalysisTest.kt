package fr.scanneat.domain.engine

import fr.scanneat.domain.engine.reaction.sharedReactionSuspects
import org.junit.Assert.*
import org.junit.Test

/** Roadmap N10. */
class ReactionAnalysisTest {
    @Test fun `allergen and ingredient shared by two reactions, ubiquitous ones skipped`() {
        val s = sharedReactionSuspects(listOf(
            listOf("farine de blé", "noisettes", "sucre", "lupin"),
            listOf("chocolat", "noisettes", "sucre", "sel"),
            listOf("riz", "eau", "sucre"),
        ))
        assertTrue(s.first().isAnnexIIAllergen)
        assertTrue(s.any { it.isAnnexIIAllergen && it.reactionCount == 2 })
        assertTrue(s.any { !it.isAnnexIIAllergen && it.label == "noisettes" && it.reactionCount == 2 })
        assertFalse(s.any { it.label == "sucre" })
    }

    @Test fun `a single reaction proves nothing`() {
        assertTrue(sharedReactionSuspects(listOf(listOf("arachides"))).isEmpty())
    }
}
