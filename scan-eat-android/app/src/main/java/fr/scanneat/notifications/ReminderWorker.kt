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
import fr.scanneat.data.repository.health.ActivityRepository
import fr.scanneat.data.repository.health.FastingRepository
import fr.scanneat.data.repository.health.HydrationRepository
import fr.scanneat.data.repository.health.MedicationRepository
import fr.scanneat.data.repository.health.WeightRepository
import fr.scanneat.data.repository.nutrition.ConsumptionRepository
import fr.scanneat.data.repository.reminders.RemindersRepository
import fr.scanneat.domain.engine.dashboard.logStreakDays
import fr.scanneat.presentation.pantry.PantryExpiryUrgency
import fr.scanneat.presentation.pantry.expiryUrgency
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
    private val activityRepo: ActivityRepository,
    private val fastingRepo: FastingRepository,
    private val medicationRepo: MedicationRepository,
    private val consumptionRepo: ConsumptionRepository,
    private val hydrationRepo: HydrationRepository,
    private val pantryRepo: fr.scanneat.data.repository.pantry.PantryRepository,
    private val prefs: UserPreferences,
) : CoroutineWorker(context, params) {

    // Workers have no Compose stringResource() and applicationContext.getString()
    // would follow the device locale, not this app's own in-app language setting -
    // see fr.scanneat.util.localizedString for why this needs a locale override.
    private fun localizedString(lang: String, resId: Int): String =
        localizedString(applicationContext, lang, resId)

    // Plain med.id.hashCode() used to double as both the notification id and the
    // PendingIntent request code - String.hashCode() ranges over the full 32-bit
    // Int space, so it could land on one of the fixed meal/hydration/weight/digest
    // ids (101-111) or a custom reminder's id (910+), silently replacing
    // (FLAG_UPDATE_CURRENT) an unrelated reminder's notification on screen with a
    // medication one or vice versa. Reserved base offset keeps every medication id
    // out of the low ranges every other reminder type uses; the modulo bound still
    // leaves a (very low, pre-existing) chance of two medications colliding with
    // each other, but that was already true of the raw hashCode and isn't what
    // this fix targets - closing the cross-type collision is the real, deterministic
    // improvement here.
    // [time] included in the hash input so a medication with multiple daily
    // reminder times gets a distinct notification id per slot - without it,
    // every slot's reminder would silently replace (FLAG_UPDATE_CURRENT) the
    // previous slot's still-unread notification on screen.
    private fun medicationNotificationId(medId: String, time: String): Int =
        MEDICATION_NOTIF_ID_BASE + ("$medId|$time".hashCode() and 0x7FFFFFFF) % MEDICATION_NOTIF_ID_RANGE

    override suspend fun doWork(): Result {
        val s = remindersRepo.settings.first()
        val lang = prefs.language.first()
        val profileId = prefs.activeProfileId.first()
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
                val goalMl = hydrationRepo.goalMl(profile.sex, profile.activityLevel, profile.healthConditions, weightKg = profile.weightKg)
                val todayMl = hydrationRepo.observe(LocalDate.now(), profileId).first()
                if (todayMl < goalMl) {
                    NotificationHelper.show(applicationContext, 104,
                        localizedString(lang, R.string.reminders_notif_hydration_title), localizedString(lang, R.string.reminders_notif_hydration_body), NotifChannel.HYDRATION)
                }
            }
        }
        checkMeal(s.hydrationCustomOn, s.hydrationCustomTime, RemindersRepository.K_LAST_HYDRATION_CUSTOM_DATE, now, 107,
            localizedString(lang, R.string.reminders_notif_hydration_title), localizedString(lang, R.string.reminders_notif_hydration_body), NotifChannel.HYDRATION)

        if (s.weightOn) {
            val lastDate = weightRepo.observeAll(profileId).first().maxByOrNull { it.date }?.date
            val daysSince = lastDate?.let { ChronoUnit.DAYS.between(it, LocalDate.now()) } ?: Long.MAX_VALUE
            if (daysSince >= s.weightThresholdDays && !remindersRepo.wasFiredToday(RemindersRepository.K_LAST_WEIGHT_NUDGE_DATE)) {
                if (NotificationHelper.show(applicationContext, 105,
                    localizedString(lang, R.string.reminders_notif_weight_title), localizedString(lang, R.string.reminders_notif_weight_body), NotifChannel.WEIGHT)) {
                    remindersRepo.markFiredToday(RemindersRepository.K_LAST_WEIGHT_NUDGE_DATE)
                }
            }
        }
        checkMeal(s.weightCustomOn, s.weightCustomTime, RemindersRepository.K_LAST_WEIGHT_CUSTOM_DATE, now, 108,
            localizedString(lang, R.string.reminders_notif_weight_title), localizedString(lang, R.string.reminders_notif_weight_body), NotifChannel.WEIGHT)

        if (s.activityOn) {
            val lastDate = activityRepo.getRange(LocalDate.now().minusDays(90), LocalDate.now(), profileId).maxByOrNull { it.date }?.date
            val daysSince = lastDate?.let { ChronoUnit.DAYS.between(it, LocalDate.now()) } ?: Long.MAX_VALUE
            if (daysSince >= s.activityThresholdDays && !remindersRepo.wasFiredToday(RemindersRepository.K_LAST_ACTIVITY_NUDGE_DATE)) {
                if (NotificationHelper.show(applicationContext, 111,
                    localizedString(lang, R.string.reminders_notif_activity_title), localizedString(lang, R.string.reminders_notif_activity_body), NotifChannel.ACTIVITY)) {
                    remindersRepo.markFiredToday(RemindersRepository.K_LAST_ACTIVITY_NUDGE_DATE)
                }
            }
        }

        s.customReminders.forEach { cr ->
            checkMeal(cr.on, cr.time, remindersRepo.customLastFiredKey(cr.id), now, cr.id, cr.label, cr.label, NotifChannel.CUSTOM)
        }

        val takenMedIds = medicationRepo.observeLogByDate(LocalDate.now(), profileId).first().map { it.medicationId }.toSet()
        val today = LocalDate.now().dayOfWeek
        medicationRepo.observeAll(profileId).first()
            .filter { it.active && it.reminderOn && it.isScheduledOn(today) }
            .forEach { med ->
                val title = localizedString(lang, R.string.reminders_notif_medication_title)
                val body = String.format(localizedString(lang, R.string.reminders_notif_medication_body), med.name)
                // User-requested: medications taken multiple times/day previously had
                // only one reminderTime and one fired-today flag shared across the
                // whole medication - the second/third dose of the day never got its
                // own reminder at all. Each of med.reminderTimes (reminderTime plus
                // any extraReminderTimes) now fires and re-notifies independently via
                // its own per-slot key (medicationLastFiredKey(id, time)).
                med.reminderTimes.forEach { time ->
                    val slotId = medicationNotificationId(med.id, time)
                    val justFired = checkMeal(true, time, remindersRepo.medicationLastFiredKey(med.id, time), now, slotId, title, body, NotifChannel.MEDICATION)

                    // Dose reminders used to fire once at the scheduled time and go silent
                    // regardless of whether the dose was ever logged. MedicationLogEntry
                    // already records real per-dose "taken" timestamps, so re-notify every
                    // hour until it's logged, instead of only reminding once. "Taken today"
                    // is still a single daily flag per medication (not per time slot - see
                    // MedicationLogEntry's own shape), so logging any one dose quiets every
                    // remaining slot's re-notify for the rest of the day.
                    val alreadyFiredToday = justFired || remindersRepo.wasFiredToday(remindersRepo.medicationLastFiredKey(med.id, time))
                    if (!justFired && alreadyFiredToday && med.id !in takenMedIds &&
                        remindersRepo.medicationRenotifyDueAndMark(med.id, time, MEDICATION_RENOTIFY_MINUTES)) {
                        // Was the exact same title/body as the original on-time reminder,
                        // repeated verbatim every hour indefinitely until logged - reads as
                        // naggy rather than a helpful nudge, with no acknowledgment this is
                        // a repeat.
                        val repeatBody = String.format(localizedString(lang, R.string.reminders_notif_medication_body_repeat), med.name)
                        NotificationHelper.show(applicationContext, slotId, title, repeatBody, NotifChannel.MEDICATION)
                    }
                }
            }

        fastingRepo.state(profileId).first()?.let { fast ->
            if (fast.elapsedHours >= fast.targetHours && !remindersRepo.fastingTargetAlreadyNotified(fast.startMs)) {
                if (NotificationHelper.show(applicationContext, 109,
                    localizedString(lang, R.string.reminders_notif_fasting_title), localizedString(lang, R.string.reminders_notif_fasting_body), NotifChannel.FASTING)) {
                    remindersRepo.markFastingTargetNotified(fast.startMs)
                }
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
            // R&D §X.0: logStreakDays() was already computed on Dashboard/the widget
            // but nothing proactively warned a user their streak was about to lapse -
            // the only way to notice was opening the app after midnight to find it
            // already broken. Post-21:00 is exactly when this digest already fires,
            // so this reuses that same window: with a 1-day grace period, an unlogged
            // today still returns yesterday-anchored streak length, which is exactly
            // the run that breaks at midnight if nothing is logged before then.
            val streakAtRisk = if (mealCount == 0) logStreakDays(consumptionRepo.getAllLoggedDates(), today) else 0
            val (title, body) = if (streakAtRisk > 0)
                localizedString(lang, R.string.notif_streak_risk_title) to
                    String.format(localizedString(lang, R.string.notif_streak_risk_body), streakAtRisk)
            else
                localizedString(lang, R.string.notif_summary_title) to
                    String.format(localizedString(lang, R.string.notif_summary_body), totalKcal, mealCount)
            if (NotificationHelper.show(applicationContext, 110, title, body, NotifChannel.SUMMARY)) {
                remindersRepo.markFiredToday(K_LAST_DIGEST_DATE)
            }
        }

        // New: pantry expiry alert - once per day, after 9:00, when at least one
        // item is expiring soon/already expired (see PantryExpiryUrgency). Unlike
        // every other reminder above, default-on (see ReminderSettings.pantryExpiryOn's
        // own doc comment) - a fired-today guard still applies so this can't repeat
        // more than once a day even while items stay expired across several runs.
        if (s.pantryExpiryOn && now.hour >= 9 && !remindersRepo.wasFiredToday(RemindersRepository.K_LAST_PANTRY_EXPIRY_DATE)) {
            val expiring = pantryRepo.observeAll(profileId).first()
                .filter { it.expiryUrgency() in setOf(PantryExpiryUrgency.SOON, PantryExpiryUrgency.EXPIRED) }
            if (expiring.isNotEmpty()) {
                val title = localizedString(lang, R.string.reminders_notif_pantry_expiry_title)
                val body = String.format(localizedString(lang, R.string.reminders_notif_pantry_expiry_body), expiring.size, expiring.take(3).joinToString(", ") { it.name })
                if (NotificationHelper.show(applicationContext, 112, title, body, NotifChannel.PANTRY)) {
                    remindersRepo.markFiredToday(RemindersRepository.K_LAST_PANTRY_EXPIRY_DATE)
                }
            }
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
            // Only mark "fired today" if the notification actually posted (not
            // silently skipped for a denied POST_NOTIFICATIONS permission) -
            // otherwise a day with permission denied permanently lost this reminder
            // with no way to recover, since wasFiredToday would then suppress every
            // later check on the same day even once permission is granted.
            if (NotificationHelper.show(applicationContext, notifId, title, text, channel)) {
                remindersRepo.markFiredToday(lastFiredKey)
            }
        }
        return dueNow
    }

    companion object {
        val K_LAST_DIGEST_DATE = androidx.datastore.preferences.core.stringPreferencesKey("rem_last_digest_date")
        private const val MEDICATION_RENOTIFY_MINUTES = 60L
        // Reserved id range for medication notifications - stays clear of the
        // fixed 101-111 ids above and the custom-reminder range (910+, see
        // RemindersRepository.K_CUSTOM_NEXT_ID), so a medication's derived id can
        // never collide with a non-medication reminder's notification/PendingIntent.
        private const val MEDICATION_NOTIF_ID_BASE = 2_000_000
        private const val MEDICATION_NOTIF_ID_RANGE = 500_000
    }
}
