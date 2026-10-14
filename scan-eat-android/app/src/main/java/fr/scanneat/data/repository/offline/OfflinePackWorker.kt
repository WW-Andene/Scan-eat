package fr.scanneat.data.repository.offline

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** Roadmap N9: monthly refresh of the offline pack, on Wi-Fi (unmetered) and battery not low. */
@HiltWorker
class OfflinePackWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: OfflinePackRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!repo.isEnabled) return Result.success()
        return try { repo.download(); Result.success() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }
    }

    companion object {
        private const val NAME = "offline_pack_monthly"
        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).setRequiresBatteryNotLow(true).build()

        fun enable(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<OfflinePackWorker>(30, TimeUnit.DAYS).setConstraints(constraints).build())
            refreshNow(context)
        }

        fun refreshNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("$NAME-now", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<OfflinePackWorker>().setConstraints(constraints).build())
        }

        fun disable(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(NAME)
    }
}
