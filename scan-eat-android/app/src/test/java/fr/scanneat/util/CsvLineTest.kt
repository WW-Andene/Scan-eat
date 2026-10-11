package fr.scanneat.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvLineTest {
    @Test
    fun `splits plain fields`() = assertEquals(listOf("a", "b", ""), parseCsvLine("a,b,"))

    @Test
    fun `keeps commas inside quotes`() =
        assertEquals(listOf("123", "X 500 mg, comprimé"), parseCsvLine("123,\"X 500 mg, comprimé\""))

    @Test
    fun `unescapes doubled quotes`() = assertEquals(listOf("say \"hi\""), parseCsvLine("\"say \"\"hi\"\"\""))
}
