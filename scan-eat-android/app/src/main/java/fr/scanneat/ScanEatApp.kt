package fr.scanneat

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import fr.scanneat.notifications.ReminderWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class ScanEatApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        // This job fires every 15 minutes for the lifetime of the install - skip
        // it when the battery is critically low rather than draining it further
        // for a non-critical reminder check.
        val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        // Unique work name is an internal WorkManager id, unrelated to the
        // "reminders" notification channel id - kept as "biolism_reminders" on
        // purpose. Renaming it would make enqueueUniquePeriodicWork's KEEP
        // policy treat existing installs as having no prior job, enqueueing a
        // duplicate that runs alongside the old one forever.
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("biolism_reminders", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
