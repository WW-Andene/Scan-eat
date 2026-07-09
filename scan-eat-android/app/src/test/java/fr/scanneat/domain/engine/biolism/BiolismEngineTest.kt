package fr.scanneat.domain.engine.biolism

import org.junit.Assert.*
import org.junit.Test

class BiolismEngineTest {

    // ---- computeKetoRQ: 7-phase RQ curve boundaries ----

    @Test
    fun `computeKetoRQ at phase boundaries matches the documented curve`() {
        assertEquals(0.858, BiolismEngine.computeKetoRQ(0.0, ketoAdapted = false), 0.001)
        assertEquals(0.828, BiolismEngine.computeKetoRQ(8.0, ketoAdapted = false), 0.001)
        assertEquals(0.788, BiolismEngine.computeKetoRQ(24.0, ketoAdapted = false), 0.001)
        assertEquals(0.768, BiolismEngine.computeKetoRQ(72.0, ketoAdapted = false), 0.001)
        assertEquals(0.735, BiolismEngine.computeKetoRQ(168.0, ketoAdapted = false), 0.001)
        assertEquals(0.720, BiolismEngine.computeKetoRQ(504.0, ketoAdapted = false), 0.001)
        assertEquals(0.710, BiolismEngine.computeKetoRQ(1440.0, ketoAdapted = false), 0.001)
    }

    @Test
    fun `computeKetoRQ phase 6 extended starvation creeps back up but stays capped`() {
        val rqAtWeekBeyond = BiolismEngine.computeKetoRQ(2160.0, ketoAdapted = false) // 1440 + 720h
        // Should have risen off the 0.710 floor, but never above the 0.740 cap.
        assertTrue(rqAtWeekBeyond > 0.710)
        assertTrue(rqAtWeekBeyond <= 0.740)
    }

    @Test
    fun `computeKetoRQ is monotonically non-increasing through phase 5`() {
        val hours = listOf(0.0, 4.0, 8.0, 16.0, 24.0, 48.0, 72.0, 120.0, 168.0, 336.0, 504.0, 1000.0, 1440.0)
        val values = hours.map { BiolismEngine.computeKetoRQ(it, ketoAdapted = false) }
        for (i in 1 until values.size) {
            assertTrue(
                "RQ should not increase from hour ${hours[i - 1]} to ${hours[i]}",
                values[i] <= values[i - 1] + 1e-9,
            )
        }
    }

    // ---- computeSubstrates: protein fraction at key keto hours ----

    @Test
    fun `computeSubstrates protein fraction at key keto hours`() {
        assertEquals(0.170, BiolismEngine.computeSubstrates(0.858, 0.0).protFrac, 0.001)
        assertEquals(0.190, BiolismEngine.computeSubstrates(0.788, 24.0).protFrac, 0.001)
        assertEquals(0.220, BiolismEngine.computeSubstrates(0.768, 96.0).protFrac, 0.001)
        assertEquals(0.120, BiolismEngine.computeSubstrates(0.720, 504.0).protFrac, 0.001)
    }

    @Test
    fun `computeSubstrates fractions always sum to one`() {
        for (kh in listOf(0.0, 24.0, 96.0, 168.0, 504.0, 1440.0, 2000.0)) {
            val sub = BiolismEngine.computeSubstrates(0.8, kh)
            assertEquals(1.0, sub.fatFrac + sub.carbFrac + sub.protFrac, 1e-9)
        }
    }

    // ---- computeMetabolics: BMR range for known profiles ----

    private fun maleProfile(waist: Double = 0.0, hip: Double = 0.0, neck: Double = 0.0) = BiolismProfile(
        sex = BiolismSex.MALE,
        ageYears = 30,
        heightCm = 180.0,
        weightKg = 80.0,
        activityId = "sedentary",
        ethnicityId = "caucasian",
        waistCm = waist,
        hipCm = hip,
        neckCm = neck,
    )

    private fun femaleProfile() = BiolismProfile(
        sex = BiolismSex.FEMALE,
        ageYears = 28,
        heightCm = 165.0,
        weightKg = 60.0,
        activityId = "sedentary",
        ethnicityId = "caucasian",
    )

    @Test
    fun `computeMetabolics returns null for an invalid profile`() {
        assertNull(BiolismEngine.computeMetabolics(BiolismProfile()))
    }

    @Test
    fun `computeMetabolics BMR is in the expected range for a known male profile`() {
        val m = BiolismEngine.computeMetabolics(maleProfile())!!
        // Mifflin-St Jeor alone gives 1780; Katch-McArdle averages in around ±100 kcal of that.
        assertTrue("bmrDay=${m.bmrDay}", m.bmrDay in 1650.0..1850.0)
        assertEquals(1780.0, m.bmrMsj, 0.5)
        assertEquals(24.7, m.bmi, 0.1)
    }

    @Test
    fun `computeMetabolics BMR is in the expected range for a known female profile`() {
        val m = BiolismEngine.computeMetabolics(femaleProfile())!!
        // Mifflin-St Jeor alone gives 1330.25.
        assertTrue("bmrDay=${m.bmrDay}", m.bmrDay in 1200.0..1400.0)
        assertEquals(1330.25, m.bmrMsj, 0.5)
    }

    @Test
    fun `computeMetabolics TDEE scales with activity multiplier`() {
        val sedentary = BiolismEngine.computeMetabolics(maleProfile().copy(activityId = "sedentary"))!!
        val veryActive = BiolismEngine.computeMetabolics(maleProfile().copy(activityId = "very"))!!
        assertTrue(veryActive.tdeeDay > sedentary.tdeeDay)
    }

    @Test
    fun `computeMetabolics Navy tape BF% is only populated when circumferences are provided`() {
        val withoutTape = BiolismEngine.computeMetabolics(maleProfile())!!
        assertNull(withoutTape.navyBfPct)

        val withTape = BiolismEngine.computeMetabolics(maleProfile(waist = 85.0, neck = 38.0))!!
        assertNotNull(withTape.navyBfPct)
    }

    // ---- computeHormones: direction under ketosis ----

    @Test
    fun `computeHormones testosterone rises and insulin falls under ketosis`() {
        val profile = maleProfile()
        val m = BiolismEngine.computeMetabolics(profile)!!

        val baseline = BiolismEngine.computeHormones(profile, m, ketoHours = 0.0, fastingHours = 0.0, ketoAdapted = false)!!
        val inKetosis = BiolismEngine.computeHormones(profile, m, ketoHours = 100.0, fastingHours = 0.0, ketoAdapted = false)!!

        assertTrue(
            "testosterone should rise under ketosis: ${baseline.testosterone.value} -> ${inKetosis.testosterone.value}",
            inKetosis.testosterone.value > baseline.testosterone.value,
        )
        assertTrue(
            "insulin should fall under ketosis: ${baseline.insulin.value} -> ${inKetosis.insulin.value}",
            inKetosis.insulin.value < baseline.insulin.value,
        )
    }

    @Test
    fun `computeHormones returns null for an invalid profile`() {
        val validMetabolics = BiolismEngine.computeMetabolics(maleProfile())!!
        assertNull(BiolismEngine.computeHormones(BiolismProfile(), validMetabolics, 0.0, 0.0, false))
    }
}
