package fr.scanneat.data.remote.api

import org.junit.Assert.*
import org.junit.Test

/** Roadmap C3: matching RappelConso's recent recalls against the user's barcodes. */
class RecallSyncMatchTest {

    private fun record(gtin: String?, label: String = "x") = RappelConsoRecordDto(
        gtin = gtin, libelle = label, motifRappel = null, risquesEncourus = null,
        datePublication = null, conduitesATenir = null, liensVersLesImages = null, lienVersLaFicheRappel = null,
    )

    @Test
    fun `exact EAN-13 match`() {
        val m = matchRecallsToOwnedBarcodes(listOf(record("3433700020172", "saucisse")), listOf("3433700020172", "3017620422003"))
        assertEquals(setOf("3433700020172"), m.keys)
        assertEquals("saucisse", m.getValue("3433700020172").libelle)
    }

    @Test
    fun `leading zero lost by the numeric gtin column still matches`() {
        val m = matchRecallsToOwnedBarcodes(listOf(record("208028000000")), listOf("0208028000000"))
        assertEquals(setOf("0208028000000"), m.keys)
    }

    @Test
    fun `no match, null gtin and non-numeric codes are ignored`() {
        assertTrue(matchRecallsToOwnedBarcodes(listOf(record(null), record("1234567890128")), listOf("3017620422003", "ABC")).isEmpty())
        assertTrue(matchRecallsToOwnedBarcodes(emptyList(), listOf("3017620422003")).isEmpty())
    }

    @Test
    fun `published-since clause`() {
        assertEquals("date_publication>=\"2026-10-01\"", rappelConsoPublishedSinceClause("2026-10-01"))
    }
}
