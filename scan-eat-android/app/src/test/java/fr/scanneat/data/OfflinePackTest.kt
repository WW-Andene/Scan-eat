package fr.scanneat.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.squareup.moshi.Moshi
import fr.scanneat.data.local.db.offline.OfflineProductDao
import fr.scanneat.data.local.db.offline.OfflineProductEntity
import fr.scanneat.data.remote.api.SalHitDto
import fr.scanneat.data.remote.api.SearchaliciousApi
import fr.scanneat.data.remote.api.SearchaliciousResponse
import fr.scanneat.data.repository.offline.OfflinePackRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Roadmap N9: pack download stops on a short page, lookup works through barcode encodings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflinePackTest {
    private class FakeDao : OfflineProductDao {
        val rows = mutableMapOf<String, OfflineProductEntity>()
        override suspend fun findAny(barcodes: List<String>) = barcodes.firstNotNullOfOrNull { rows[it] }
        override suspend fun insertAll(rows: List<OfflineProductEntity>) { rows.forEach { this.rows[it.barcode] = it } }
        override suspend fun count() = rows.size
        override suspend fun clear() = rows.clear()
    }
    private class FakeApi : SearchaliciousApi {
        var calls = 0
        override suspend fun search(q: String, pageSize: Int, fields: String, langs: String, sortBy: String?, page: Int?): SearchaliciousResponse {
            calls++
            return SearchaliciousResponse(listOf(SalHitDto(code = "0123456789012", productName = "Yaourt", brands = listOf("X"))))
        }
    }

    @Test fun `download then offline lookup`() = runBlocking {
        val dao = FakeDao(); val api = FakeApi()
        val repo = OfflinePackRepository(ApplicationProvider.getApplicationContext<Context>(), api, dao, Moshi.Builder().build())
        assertEquals(1, repo.download())
        assertEquals(1, api.calls) // short page = last page
        assertEquals("Yaourt", repo.find("123456789012")?.productName) // UPC-A form of the stored EAN-13
        assertEquals("X", repo.find("0123456789012")?.brands)
        assertTrue(repo.lastDownloadMs > 0)
    }
}
