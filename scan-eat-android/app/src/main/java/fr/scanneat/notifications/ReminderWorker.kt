package fr.scanneat.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import fr.scanneat.R
import fr.scanneat.data.local.prefs.UserPreferences
import fr.scanneat.data.repository.health.FastingRepository
import fr.scanneat.data.repository.health.HydrationRepository
import fr.scanneat.data.repository.health.MedicationRepository
import fr.scanneat.data.repository.health.WeightRepository
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.data.repository.reminders.RemindersRepository
import fr.scanneat.util.localizedString
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val remindersRepo: RemindersRepository,
    private val weightRepo: WeightRepository,
    private val fastingRepo: FastingRepository,
    private val medicationRepo: MedicationRepository,
    private val consumptionRepo: ConsumptionRepository,
    private val hydrationRepo: HydrationRepository,
    private val prefs: UserPreferences,
) : CoroutineWorker(context, params) {

    // Workers have no Compose stringResource() and applicationContext.getString()
    // would follow the device locale, not this app's own in-app language setting -
    // see fr.scanneat.util.localizedString for why this needs a locale override.
    private fun localizedString(lang: String, resId: Int): String =
        localizedString(applicationContext, lang, resId)

    override suspend fun doWork(): Result {
        val s = remindersRepo.settings.first()
        val lang = prefs.language.first()
        val now = LocalTime.now()

        checkMeal(s.breakfastOn, s.breakfastTime, RemindersRepository.K_LAST_BREAKFAST_DATE, now, 101,
            localizedString(lang, R.string.reminders_notif_breakfast_title), localizedString(lang, R.string.reminders_notif_breakfast_body), NotifChannel.MEALS)
        checkMeal(s.snackOn, s.snackTime, RemindersRepository.K_LAST_SNACK_DATE, now, 106,
            localizedString(lang, R.string.reminders_notif_snack_title), localizedString(lang, R.string.reminders_notif_snack_body), NotifChannel.MEALS)
        checkMeal(s.lunchOn, s.lunchTime, RemindersRepository.K_LAST_LUNCH_DATE, now, 102,
            localizedString(lang, R.string.reminders_notif_lunch_title), localizedString(lang, R.string.reminders_notif_lunch_body), NotifChannel.MEALS)
        checkMeal(s.dinnerOn, s.dinnerTime, RemindersRepository.K_LAST_DINNER_DATE, now, 103,
            localizedString(lang, R.string.reminders_notif_dinner_title), localizedString(lang, R.string.reminders_notif_dinner_body), NotifChannel.MEALS)

        if (s.hydrationOn && now.hour in 8..21) {
            if (remindersRepo.hydrationDueAndMark(s.hydrationIntervalHours)) {
                // Previously fired on the fixed interval regardless of intake — a user
                // who already hit today's water goal kept getting nudged anyway.
                val profile = prefs.profile.first()
                val goalMl = hydrationRepo.goalMl(profile.sex, profile.activityLevel, profile.healthConditions)
                val todayMl = hydrationRepo.observe(LocalDate.now()).first()
                if (todayMl < goalMl) {
                    NotificationHelper.show(applicationContext, 104,
                        localizedString(lang, R.string.reminders_notif_hydration_title), localizedString(lang, R.string.reminders_notif_hydration_body), NotifChannel.HYDRATION)
                }
            }
        }
        checkMeal(s.hydrationCustomOn, s.hydrationCustomTime, RemindersRepository.K_LAST_HYDRATION_CUSTOM_DATE, now, 107,
            localizedString(lang, R.string.reminders_notif_hydration_title), localizedString(lang, R.string.reminders_notif_hydration_body), NotifChannel.HYDRATION)

        if (s.weightOn) {
            val lastDate = weightRepo.observeAll().first().maxByOrNull { it.date }?.date
            val daysSince = lastDate?.let { ChronoUnit.DAYS.between(it, LocalDate.now()) } ?: Long.MAX_VALUE
            if (daysSince >= s.weightThresholdDays && !remindersRepo.wasFiredToday(RemindersRepository.K_LAST_WEIGHT_NUDGE_DATE)) {
                NotificationHelper.show(applicationContext, 105,
                    localizedString(lang, R.string.reminders_notif_weight_title), localizedString(lang, R.string.reminders_notif_weight_body), NotifChannel.WEIGHT)
                remindersRepo.markFiredToday(RemindersRepository.K_LAST_WEIGHT_NUDGE_DATE)
            }
        }
        checkMeal(s.weightCustomOn, s.weightCustomTime, RemindersRepository.K_LAST_WEIGHT_CUSTOM_DATE, now, 108,
            localizedString(lang, R.string.reminders_notif_weight_title), localizedString(lang, R.string.reminders_notif_weight_body), NotifChannel.WEIGHT)

        s.customReminders.forEach { cr ->
            checkMeal(cr.on, cr.time, remindersRepo.customLastFiredKey(cr.id), now, cr.id, cr.label, cr.label, NotifChannel.CUSTOM)
        }

        val takenMedIds = medicationRepo.observeLogByDate(LocalDate.now()).first().map { it.medicationId }.toSet()
        medicationRepo.observeAll().first().filter { it.active && it.reminderOn }.forEach { med ->
            val title = localizedString(lang, R.string.reminders_notif_medication_title)
            val body = String.format(localizedString(lang, R.string.reminders_notif_medication_body), med.name)
            val justFired = checkMeal(true, med.reminderTime, remindersRepo.medicationLastFiredKey(med.id), now, med.id.hashCode(), title, body, NotifChannel.MEDICATION)

            // Dose reminders used to fire once at the scheduled time and go silent
            // regardless of whether the dose was ever logged. MedicationLogEntry
            // already records real per-dose "taken" timestamps, so re-notify every
            // hour until it's logged, instead of only reminding once.
            val alreadyFiredToday = justFired || remindersRepo.wasFiredToday(remindersRepo.medicationLastFiredKey(med.id))
            if (!justFired && alreadyFiredToday && med.id !in takenMedIds &&
                remindersRepo.medicationRenotifyDueAndMark(med.id, MEDICATION_RENOTIFY_MINUTES)) {
                NotificationHelper.show(applicationContext, med.id.hashCode(), title, body, NotifChannel.MEDICATION)
            }
        }

        fastingRepo.state.first()?.let { fast ->
            if (fast.elapsedHours >= fast.targetHours && !remindersRepo.fastingTargetAlreadyNotified(fast.startMs)) {
                NotificationHelper.show(applicationContext, 109,
                    localizedString(lang, R.string.reminders_notif_fasting_title), localizedString(lang, R.string.reminders_notif_fasting_body), NotifChannel.FASTING)
                remindersRepo.markFastingTargetNotified(fast.startMs)
            }
        }

        // New: daily digest — fires once after 21:00 if enabled, summarising today's
        // logged kcal and meal count, so users get a passive end-of-day awareness
        // nudge without having to open the app.
        if (s.dailyDigestOn && now.hour >= 21 && !remindersRepo.wasFiredToday(K_LAST_DIGEST_DATE)) {
            val today = LocalDate.now()
            val dayData = consumptionRepo.observeDay(today).first()
            val totalKcal = dayData.totals.energyKcal.toInt()
            val mealCount = dayData.entries.size
            val body = String.format(localizedString(lang, R.string.notif_summary_body), totalKcal, mealCount)
            NotificationHelper.show(applicationContext, 110,
                localizedString(lang, R.string.notif_summary_title), body, NotifChannel.SUMMARY)
            remindersRepo.markFiredToday(K_LAST_DIGEST_DATE)
        }

        return Result.success()
    }

    /** Returns true if this call actually fired the notification (used by medication re-notify to avoid double-firing on the same tick). */
    private suspend fun checkMeal(
        on: Boolean, timeStr: String, lastFiredKey: androidx.datastore.preferences.core.Preferences.Key<String>,
        now: LocalTime, notifId: Int, title: String, text: String, channel: NotifChannel? = null,
    ): Boolean {
        if (!on) return false
        val target = runCatching { LocalTime.parse(timeStr) }.getOrNull() ?: return false
        // Wrap the day boundary explicitly: for targets in the evening (e.g. 22:00),
        // target.plusHours(3) rolls over past midnight (01:00), and comparing raw
        // LocalTime values via isBefore/isAfter breaks because "now" (still in the
        // evening) is never isBefore an earlier-looking wrapped clock time.
        val secondsSinceTarget = (now.toSecondOfDay() - target.toSecondOfDay()).let { if (it < 0) it + 86400 else it }
        val dueNow = secondsSinceTarget < 3 * 3600 && !remindersRepo.wasFiredToday(lastFiredKey)
        if (dueNow) {
            NotificationHelper.show(applicationContext, notifId, title, text, channel)
            remindersRepo.markFiredToday(lastFiredKey)
        }
        return dueNow
    }

    companion object {
        val K_LAST_DIGEST_DATE = androidx.datastore.preferences.core.stringPreferencesKey("rem_last_digest_date")
        private const val MEDICATION_RENOTIFY_MINUTES = 60L
    }
}
