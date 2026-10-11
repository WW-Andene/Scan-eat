package fr.scanneat.presentation.medication

import org.junit.Assert.assertTrue
import org.junit.Test

// Users enter the name printed on the box - usually a French brand, not the
// INN. Previscan (fluindione, France's most-prescribed AVK), Lovenox and
// Kardégic were not recognized at all.
class InteractionBrandNamesTest {

    @Test
    fun `brand-name anticoagulant plus NSAID warns`() {
        for (anticoag in listOf("Previscan 20 mg", "Lovenox", "Xarelto", "Eliquis 5mg", "fluindione")) {
            for (nsaid in listOf("Advil", "Nurofen", "Voltarène", "célécoxib")) {
                val w = detectInteractions(listOf(anticoag, nsaid))
                assertTrue("$anticoag + $nsaid", w.contains(InteractionWarning.AnticoagNsaid))
            }
        }
    }

    @Test
    fun `brand-name anticoagulant plus antiplatelet warns`() {
        val w = detectInteractions(listOf("Previscan", "Kardégic 75"))
        assertTrue(w.contains(InteractionWarning.AnticoagAntiplatelet))
    }

    @Test
    fun `more common french brands are recognized`() {
        assertTrue(detectInteractions(listOf("Previscan", "Aspégic 500")).contains(InteractionWarning.AnticoagAntiplatelet))
        assertTrue(detectInteractions(listOf("Calciparine", "Mobic")).contains(InteractionWarning.AnticoagNsaid))
        assertTrue(detectInteractions(listOf("Xarelto", "Feldène", "Surgam")).contains(InteractionWarning.AnticoagNsaid))
        assertTrue(detectInteractions(listOf("Cipralex", "Humoryl")).contains(InteractionWarning.SsriMaoi))
    }
}
