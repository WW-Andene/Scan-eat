package fr.scanneat.presentation.dashboard

import fr.scanneat.presentation.diary.components.DiaryTab
import org.junit.Assert.*
import org.junit.Test

/** Roadmap A11. */
class DashboardLayoutTest {
    @Test fun `round trip with order and hidden cards, missing cards appended`() {
        val l = DashboardLayout.DEFAULT.moved(DashboardCard.WEIGHT, -100).toggled(DashboardCard.BUDGET)
        val back = DashboardLayout.parse(l.serialize())
        assertEquals(DashboardCard.WEIGHT, back.order.first())
        assertTrue(DashboardCard.BUDGET in back.hidden)
        assertEquals(DashboardCard.entries.size, DashboardLayout.parse("WEIGHT,!MACROS,UNKNOWN").order.size)
    }

    @Test fun `unfollowed module hides its cards`() {
        val v = DashboardLayout.DEFAULT.visible(setOf(DiaryTab.WEIGHT))
        assertFalse(DashboardCard.WEIGHT in v); assertFalse(DashboardCard.INSIGHT in v); assertTrue(DashboardCard.CALORIES in v)
    }
}
