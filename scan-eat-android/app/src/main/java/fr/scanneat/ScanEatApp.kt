package fr.scanneat

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
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
        // Previously gated the whole job on setRequiresBatteryNotLow(true) - this
        // worker also drives medication dose reminders (MedicationRepository), not
        // just non-critical meal/hydration nudges, and each individual due-window
        // check is only open for 3 hours (ReminderWorker.checkMeal's
        // secondsSinceTarget < 3*3600 bound). A device left on low battery and
        // unplugged for an ordinary afternoon/evening would have the entire run
        // deferred by WorkManager, silently missing that day's medication
        // reminder with no error surfaced anywhere and no catch-up once the
        // battery constraint clears. A 15-minute periodic tick is cheap enough
        // that skipping it for battery is not worth risking a missed medical
        // adherence reminder.
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES)
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
