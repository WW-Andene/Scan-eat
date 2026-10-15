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
        // Journal tabs (MEALS, WATER, Corps=WEIGHT, FASTING, MOOD); hiding Corps and Fasting.
        val (primary, overflow) = visibleDiaryTabs(listOf(MEALS, WEIGHT, WATER), setOf(WEIGHT, FASTING))
        assertEquals(listOf(MEALS, WATER, MOOD), primary)
        assertFalse(FASTING in overflow); assertFalse(WEIGHT in overflow)
        // Corps stays while one body tracker is followed; Treatment/Expenses never are Journal tabs.
        assertEquals(setOf(FASTING), fr.scanneat.presentation.diary.components.hiddenJournalTabs(setOf(WEIGHT, ACTIVITY, FASTING)))
        assertEquals(setOf(WEIGHT), fr.scanneat.presentation.diary.components.hiddenJournalTabs(setOf(WEIGHT, ACTIVITY, SLEEP)))
        assertNull(fr.scanneat.presentation.diary.components.journalSlotOf(TREATMENT))
    }

    @Test fun `meals can never be hidden`() {
        assertEquals(setOf(SLEEP), parseHiddenModules(serializeHiddenModules(setOf(MEALS, SLEEP))))
    }
}
