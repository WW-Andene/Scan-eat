package fr.scanneat.data.repository.offline

import android.content.Context
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.scanneat.data.local.db.offline.OfflineProductDao
import fr.scanneat.data.local.db.offline.OfflineProductEntity
import fr.scanneat.data.remote.api.OffProductDto
import fr.scanneat.data.remote.api.SearchaliciousApi
import fr.scanneat.util.barcodeCandidates
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Roadmap N9: downloads the [PACK_PAGES] x 100 most scanned products sold in
 * France and serves them when a barcode lookup cannot reach Open Food Facts.
 * Pages are fetched one per second to stay polite with OFF's free service.
 */
@Singleton
class OfflinePackRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val salApi: SearchaliciousApi,
    private val dao: OfflineProductDao,
    moshi: Moshi,
) {
    private val adapter = moshi.adapter(OffProductDto::class.java)
    private val prefs get() = context.getSharedPreferences("offline_pack", Context.MODE_PRIVATE)

    val isEnabled: Boolean get() = prefs.getBoolean(KEY_ENABLED, false)
    val lastDownloadMs: Long get() = prefs.getLong(KEY_LAST, 0L)
    suspend fun count(): Int = dao.count()

    fun setEnabled(on: Boolean) = prefs.edit().putBoolean(KEY_ENABLED, on).apply()

    suspend fun clear() { dao.clear(); prefs.edit().remove(KEY_LAST).apply() }

    /** Returns the number of products stored; throws on network failure (worker retries). */
    suspend fun download(onProgress: (Int) -> Unit = {}): Int {
        val now = System.currentTimeMillis()
        var stored = 0
        for (page in 1..PACK_PAGES) {
            val hits = salApi.search(q = "countries_tags:\"en:france\"", pageSize = 100, langs = "fr", sortBy = "-unique_scans_n", page = page).hits.orEmpty()
            val rows = hits.mapNotNull { h -> h.code?.takeIf { it.all(Char::isDigit) }?.let { OfflineProductEntity(it, adapter.toJson(h.toOffProductDto()), now) } }
            dao.insertAll(rows)
            stored += rows.size
            onProgress(page * 100 / PACK_PAGES)
            if (hits.size < 100) break
            delay(1_000)
        }
        prefs.edit().putLong(KEY_LAST, now).apply()
        return stored
    }

    /** Offline product for any encoding of [barcode], or null. */
    suspend fun find(barcode: String): OffProductDto? =
        dao.findAny(barcodeCandidates(barcode))?.let { runCatching { adapter.fromJson(it.json) }.getOrNull() }

    companion object {
        const val PACK_PAGES = 50
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST = "last_download_ms"
    }
}
