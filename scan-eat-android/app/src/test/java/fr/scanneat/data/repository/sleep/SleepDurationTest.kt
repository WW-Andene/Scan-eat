package fr.scanneat.data.repository.sleep

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SleepDurationTest {
    private fun entry(hours: Double) = SleepEntry("id", LocalDate.now(), 0L, (hours * 3_600_000).toLong(), 3, "")

    @Test
    fun `normal night is unchanged`() = assertEquals(8.0, entry(8.0).durationHours, 0.001)

    @Test
    fun `legacy +24h entry is corrected`() = assertEquals(7.0, entry(31.0).durationHours, 0.001)
}
