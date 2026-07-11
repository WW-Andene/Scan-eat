package fr.scanneat.notifications

import android.content.Context
import android.content.res.Configuration
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.scanneat.R
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.health.WeightRepository
import fr.scanneat.data.repository.reminders.RemindersRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.Locale

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val remindersRepo: RemindersRepository,
    private val weightRepo: WeightRepository,
    private val prefs: UserPreferences,
) : CoroutineWorker(context, params) {

    // Workers have no Compose stringResource() and applicationContext.getString()
    // would follow the device locale, not this app's own in-app language setting
    // (independent of device locale everywhere else this session) - a locale-
    // overridden resources instance is the only way to respect it here too.
    private fun localizedString(lang: String, resId: Int): String {
        val config = Configuration(applicationContext.resources.configuration)
        config.setLocale(Locale(lang))
        return applicationContext.createConfigurationContext(config).resources.getString(resId)
    }

    override suspend fun doWork(): Result {
        val s = remindersRepo.settings.first()
        val lang = prefs.language.first()
        val now = LocalTime.now()

        checkMeal(s.breakfastOn, s.breakfastTime, RemindersRepository.K_LAST_BREAKFAST_DATE, now, 101,
            localizedString(lang, R.string.reminders_notif_breakfast_title), localizedString(lang, R.string.reminders_notif_breakfast_body))
        checkMeal(s.lunchOn, s.lunchTime, RemindersRepository.K_LAST_LUNCH_DATE, now, 102,
            localizedString(lang, R.string.reminders_notif_lunch_title), localizedString(lang, R.string.reminders_notif_lunch_body))
        checkMeal(s.dinnerOn, s.dinnerTime, RemindersRepository.K_LAST_DINNER_DATE, now, 103,
            localizedString(lang, R.string.reminders_notif_dinner_title), localizedString(lang, R.string.reminders_notif_dinner_body))

        if (s.hydrationOn && now.hour in 8..21) {
            if (remindersRepo.hydrationDueAndMark(s.hydrationIntervalHours)) {
                NotificationHelper.show(applicationContext, 104,
                    localizedString(lang, R.string.reminders_notif_hydration_title), localizedString(lang, R.string.reminders_notif_hydration_body))
            }
        }

        if (s.weightOn) {
            val lastDate = weightRepo.observeAll().first().maxByOrNull { it.date }?.date
            val daysSince = lastDate?.let { ChronoUnit.DAYS.between(it, LocalDate.now()) } ?: Long.MAX_VALUE
            if (daysSince >= s.weightThresholdDays && !remindersRepo.wasFiredToday(RemindersRepository.K_LAST_WEIGHT_NUDGE_DATE)) {
                NotificationHelper.show(applicationContext, 105,
                    localizedString(lang, R.string.reminders_notif_weight_title), localizedString(lang, R.string.reminders_notif_weight_body))
                remindersRepo.markFiredToday(RemindersRepository.K_LAST_WEIGHT_NUDGE_DATE)
            }
        }

        return Result.success()
    }

    private suspend fun checkMeal(
        on: Boolean, timeStr: String, lastFiredKey: androidx.datastore.preferences.core.Preferences.Key<String>,
        now: LocalTime, notifId: Int, title: String, text: String,
    ) {
        if (!on) return
        val target = runCatching { LocalTime.parse(timeStr) }.getOrNull() ?: return
        // Fire once on the first run at-or-after the target time, rather than only within
        // a ±15min band: WorkManager's periodic jobs routinely run late under Doze/App
        // Standby/manufacturer battery optimization, and a symmetric window silently drops
        // the reminder for the rest of the day the moment the worker is delayed past it.
        // Capped at +3h so a reminder freshly enabled hours after its time (or a device
        // left idle all evening) doesn't fire wildly stale hours later — RemindersRepository
        // separately marks a reminder's own enable/time-change as fired-today when it's
        // already past, so this cap is purely for a worker run that's unusually delayed.
        val dueNow = !now.isBefore(target) && now.isBefore(target.plusHours(3)) && !remindersRepo.wasFiredToday(lastFiredKey)
        if (dueNow) {
            NotificationHelper.show(applicationContext, notifId, title, text)
            remindersRepo.markFiredToday(lastFiredKey)
        }
    }
}
