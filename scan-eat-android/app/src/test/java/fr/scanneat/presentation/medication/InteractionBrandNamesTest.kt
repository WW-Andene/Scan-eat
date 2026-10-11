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
    fun `aspegic and common NSAID brands are recognized`() {
        assertTrue(detectInteractions(listOf("Previscan", "Aspégic 100")).contains(InteractionWarning.AnticoagAntiplatelet))
        for (nsaid in listOf("Arcoxia", "Mobic", "Toprec", "Nureflex", "Naprosyne")) {
            assertTrue(nsaid, detectInteractions(listOf("Xarelto", nsaid)).contains(InteractionWarning.AnticoagNsaid))
        }
    }

    @Test
    fun `SSRI plus bleeding-risk drug warns`() {
        for (other in listOf("Xarelto", "Advil", "Kardégic")) {
            assertTrue(other, detectInteractions(listOf("Prozac", other)).contains(InteractionWarning.SsriBleeding))
        }
        assertTrue(detectInteractions(listOf("Prozac", "Doliprane")).isEmpty())
    }
}
