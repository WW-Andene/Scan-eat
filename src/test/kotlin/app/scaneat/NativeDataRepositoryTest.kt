package app.scaneat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeDataRepositoryTest {
    @Test fun `native resources preserve food database`() {
        assertEquals(54, NativeDataRepository.foodCount())
        val apple = NativeDataRepository.searchFood("apple").first()
        assertEquals("pomme", apple.name)
        assertTrue(apple.kcal > 0.0)
    }

    @Test fun `native resources preserve activity profile and pairing data`() {
        assertTrue("walking_brisk" in NativeDataRepository.activityTypes())
        assertTrue("balanced" in NativeDataRepository.macroPresetKeys())
        assertEquals(158, NativeDataRepository.pairingCount())
        assertTrue(NativeDataRepository.pairingsFor("tomate").isNotEmpty())
    }
}
