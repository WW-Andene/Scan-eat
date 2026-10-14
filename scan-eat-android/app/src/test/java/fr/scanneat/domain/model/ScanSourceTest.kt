package fr.scanneat.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Roadmap C6: rows stored as "LLM" must still load after the rename. */
class ScanSourceTest {
    @Test fun `legacy LLM value maps to ON_DEVICE_OCR`() {
        assertEquals(ScanSource.ON_DEVICE_OCR, ScanSource.fromStored("LLM"))
    }

    @Test fun `current names round-trip`() {
        ScanSource.entries.forEach { assertEquals(it, ScanSource.fromStored(it.name)) }
    }
}
