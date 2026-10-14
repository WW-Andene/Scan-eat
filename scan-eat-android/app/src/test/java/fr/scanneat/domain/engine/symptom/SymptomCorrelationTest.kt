package fr.scanneat.domain.engine.symptom

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** Roadmap C17: stricter, tested symptom correlations. */
class SymptomCorrelationTest {
    private val d0 = LocalDate.of(2026, 9, 1)
    private fun day(i: Int) = d0.plusDays(i.toLong())

    @Test fun `fisher one-sided matches the tea-tasting reference`() {
        assertEquals(17.0 / 70.0, fisherExactGreater(3, 1, 1, 3), 1e-9)
        assertEquals(1.0, fisherExactGreater(0, 5, 5, 0), 1e-9)
    }

    @Test fun `two co-occurrences are no longer enough for a treatment`() {
        val symptoms = setOf(day(0), day(1))
        val doses = (0 until 20).map { day(it) to (if (it < 2) "Med A" else "Med B") }
        assertTrue(symptomMedicationCorrelations(symptoms, doses).isEmpty())
    }

    @Test fun `clear repeated pattern over 20 days is reported with a small p-value`() {
        val symptoms = (0 until 6).map { day(it) }.toSet()
        val doses = (0 until 20).flatMap { i ->
            listOf(day(i) to "Base") + if (i < 5 || i in 10..11) listOf(day(i) to "Med X") else emptyList()
        }
        val r = symptomMedicationCorrelations(symptoms, doses)
        val x = r.single { it.medicationName == "Med X" }
        assertEquals(5, x.symptomDaysWithMedication)
        assertTrue(x.pValue < 0.05)
    }

    @Test fun `fewer than 14 observed days gives nothing`() {
        val symptoms = (0 until 6).map { day(it) }.toSet()
        val doses = (0 until 10).map { day(it) to "Med X" }
        assertTrue(symptomMedicationCorrelations(symptoms, doses).isEmpty())
    }
}
