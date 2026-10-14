package fr.scanneat.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.scanneat.R
import fr.scanneat.data.local.db.pantry.PantryDao
import fr.scanneat.data.local.db.scan.ScanHistoryDao
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.recall.RecallRepository
import fr.scanneat.util.localizedString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Roadmap C3/D1: products were checked against RappelConso only at scan time,
 * so a product bought, scanned and recalled the following week never raised
 * anything. Once a day (network required) this pulls every recall published
 * since the last successful sync, crosses it with the barcodes of the scan
 * history (last [HISTORY_WINDOW_DAYS] days) and of the pantry, caches the
 * matches (the result screen's RecallBanner and the pantry's recalled flag
 * then show them) and notifies each newly recalled product once.
 */
@HiltWorker
class RecallSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val recallRepo: RecallRepository,
    private val scanHistoryDao: ScanHistoryDao,
    private val pantryDao: PantryDao,
    private val prefs: UserPreferences,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = LocalDate.now()
        // Overlap by one day with the previous run: a recall published late on
        // the last sync day is still caught; the cache dedupes notifications.
        val since = store.getString(KEY_LAST_SYNC_DATE, null)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.minusDays(1)
            ?.let { maxOf(it, today.minusDays(FIRST_RUN_LOOKBACK_DAYS)) }
            ?: today.minusDays(FIRST_RUN_LOOKBACK_DAYS)

        val owned = buildSet {
            addAll(scanHistoryDao.barcodesScannedSince(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(HISTORY_WINDOW_DAYS)))
            addAll(pantryDao.allBarcodes())
        }
        val newlyRecalled = try {
            recallRepo.syncRecentRecalls(owned, since.toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
        store.edit().putString(KEY_LAST_SYNC_DATE, today.toString()).apply()

        if (newlyRecalled.isNotEmpty()) {
            val lang = prefs.language.first()
            newlyRecalled.forEach { (barcode, entry) ->
                val name = entry.productLabel?.takeIf { it.isNotBlank() } ?: barcode
                val body = buildString {
                    append(localizedString(applicationContext, lang, R.string.recall_notif_body))
                    entry.reasonFr?.takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
                }
                NotificationHelper.show(
                    applicationContext,
                    RECALL_NOTIF_ID_BASE + (barcode.hashCode() and 0x7FFFFFFF) % RECALL_NOTIF_ID_RANGE,
                    localizedString(applicationContext, lang, R.string.recall_notif_title, name),
                    body,
                    NotifChannel.RECALLS,
                    // Tap opens the product's result (RecallBanner shows the
                    // full notice); pantry-only products open the app.
                    openScanId = scanHistoryDao.latestIdForBarcode(barcode),
                )
            }
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "recall_sync"
        private const val PREFS_NAME = "recall_sync"
        private const val KEY_LAST_SYNC_DATE = "last_sync_date"
        private const val FIRST_RUN_LOOKBACK_DAYS = 30L
        private const val HISTORY_WINDOW_DAYS = 180L
        // Clear of ReminderWorker's fixed ids (101-112), custom reminders (910+)
        // and medication ids (2_000_000 .. 2_500_000).
        private const val RECALL_NOTIF_ID_BASE = 3_000_000
        private const val RECALL_NOTIF_ID_RANGE = 500_000
    }
}
