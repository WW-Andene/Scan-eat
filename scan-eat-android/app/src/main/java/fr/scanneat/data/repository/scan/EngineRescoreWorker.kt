package fr.scanneat.data.repository.scan

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.domain.engine.scoring.ENGINE_VERSION
import kotlinx.coroutines.flow.first

/** Roadmap S14: one rescore pass per engine version (unique work name includes the version). */
@HiltWorker
class EngineRescoreWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scanRepo: ScanRepository,
    private val prefs: UserPreferences,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        runCatching { scanRepo.rescoreAllStale(prefs.language.first()) }
            .fold(onSuccess = { Result.success() }, onFailure = { if (runAttemptCount < 2) Result.retry() else Result.failure() })

    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "engine_rescore_$ENGINE_VERSION", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<EngineRescoreWorker>().build(),
            )
        }
    }
}
