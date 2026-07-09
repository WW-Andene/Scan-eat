package fr.scanneat.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.scanneat.data.repository.health.WeightRepository
import fr.scanneat.data.repository.reminders.RemindersRepository
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val remindersRepo: RemindersRepository,
    private val weightRepo: WeightRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val s = remindersRepo.settings.first()
        val now = LocalTime.now()

        checkMeal(s.breakfastOn, s.breakfastTime, RemindersRepository.K_LAST_BREAKFAST_DATE, now, 101,
            "Petit-déjeuner", "N'oublie pas de journaliser ton petit-déjeuner.")
        checkMeal(s.lunchOn, s.lunchTime, RemindersRepository.K_LAST_LUNCH_DATE, now, 102,
            "Déjeuner", "N'oublie pas de journaliser ton déjeuner.")
        checkMeal(s.dinnerOn, s.dinnerTime, RemindersRepository.K_LAST_DINNER_DATE, now, 103,
            "Dîner", "N'oublie pas de journaliser ton dîner.")

        if (s.hydrationOn && now.hour in 8..21) {
            if (remindersRepo.hydrationDueAndMark(s.hydrationIntervalHours)) {
                NotificationHelper.show(applicationContext, 104, "Hydratation", "Pense à boire un verre d'eau.")
            }
        }

        if (s.weightOn) {
            val lastDate = weightRepo.observeAll().first().maxByOrNull { it.date }?.date
            val daysSince = lastDate?.let { ChronoUnit.DAYS.between(it, LocalDate.now()) } ?: Long.MAX_VALUE
            if (daysSince >= s.weightThresholdDays && !remindersRepo.wasFiredToday(RemindersRepository.K_LAST_WEIGHT_NUDGE_DATE)) {
                NotificationHelper.show(applicationContext, 105, "Pesée", "Ça fait un moment — enregistre ton poids.")
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
        val withinWindow = kotlin.math.abs(Duration.between(target, now).toMinutes()) <= 15
        if (withinWindow && !remindersRepo.wasFiredToday(lastFiredKey)) {
            NotificationHelper.show(applicationContext, notifId, title, text)
            remindersRepo.markFiredToday(lastFiredKey)
        }
    }
}
