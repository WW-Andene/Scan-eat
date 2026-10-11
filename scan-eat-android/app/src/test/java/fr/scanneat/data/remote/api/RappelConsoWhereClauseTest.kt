package fr.scanneat.data.remote.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RappelConsoWhereClauseTest {
    @Test
    fun `EAN-13 scan also matches its UPC-A listing`() {
        val clause = rappelConsoGtinWhereClause("0049000028911")
        assertTrue(clause, "gtin=\"0049000028911\"" in clause)
        assertTrue(clause, "gtin=\"049000028911\"" in clause)
    }

    @Test
    fun `UPC-A scan also matches its EAN-13 listing`() {
        assertTrue("gtin=\"0049000028911\"" in rappelConsoGtinWhereClause("049000028911"))
    }

    @Test
    fun `plain EAN-13 is a single exact match`() {
        assertEquals("gtin=\"3017620422003\"", rappelConsoGtinWhereClause("3017620422003"))
    }
}
