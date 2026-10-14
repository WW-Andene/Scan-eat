package fr.scanneat.data

import fr.scanneat.data.repository.backup.AutoBackupSettings
import org.junit.Assert.assertEquals
import org.junit.Test

/** Roadmap C12: only the 4 most recent automatic backups are kept; other files are never touched. */
class AutoBackupPruneTest {
    @Test fun `keeps the four newest, ignores foreign files`() {
        val names = listOf("2026-09-01", "2026-09-08", "2026-09-15", "2026-09-22", "2026-09-29", "2026-10-06")
            .map { "scaneat-auto-$it-1.json" } + "vacances.jpg" + "scaneat-manual.json"
        assertEquals(listOf("scaneat-auto-2026-09-08-1.json", "scaneat-auto-2026-09-01-1.json"), AutoBackupSettings.filesToPrune(names))
    }
}
