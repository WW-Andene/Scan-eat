package fr.scanneat.presentation.diary

import fr.scanneat.presentation.diary.components.DiaryTab.*
import fr.scanneat.presentation.diary.components.parseHiddenModules
import fr.scanneat.presentation.diary.components.serializeHiddenModules
import fr.scanneat.presentation.diary.components.visibleDiaryTabs
import org.junit.Assert.*
import org.junit.Test

/** Roadmap A4. */
class ModulesTest {
    @Test fun `hidden primary slot is refilled, hidden never in overflow`() {
        val (primary, overflow) = visibleDiaryTabs(listOf(MEALS, WEIGHT, WATER), setOf(WEIGHT, FASTING))
        assertEquals(listOf(MEALS, WATER, ACTIVITY), primary)
        assertFalse(FASTING in overflow); assertFalse(WEIGHT in overflow)
    }

    @Test fun `meals can never be hidden`() {
        assertEquals(setOf(SLEEP), parseHiddenModules(serializeHiddenModules(setOf(MEALS, SLEEP))))
    }
}
