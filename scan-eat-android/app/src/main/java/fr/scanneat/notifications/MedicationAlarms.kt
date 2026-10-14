package fr.scanneat.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import fr.scanneat.data.repository.health.Medication
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Roadmap C9: dose reminders relied only on ReminderWorker's 15-minute periodic
 * tick (later still under Doze). One exact alarm is armed for the next dose
 * slot of any active medication; when it rings, [MedicationAlarmReceiver] runs
 * ReminderWorker right away (its per-slot "fired today" keys already prevent
 * duplicates) and ReminderWorker re-arms the following slot. Meal/hydration
 * reminders stay on the periodic worker.
 */
object MedicationAlarms {
    private const val REQUEST_CODE = 7_000_001
    private const val IMMEDIATE_WORK = "medication_alarm_run"

    /** Next dose time strictly after [now] among [meds] (active, reminder on, scheduled day), within a week. */
    fun nextDoseTrigger(meds: List<Medication>, now: LocalDateTime): LocalDateTime? =
        meds.filter { it.active && it.reminderOn }.flatMap { med ->
            med.reminderTimes.mapNotNull { t -> runCatching { LocalTime.parse(t) }.getOrNull() }.mapNotNull { time ->
                (0L..7L).asSequence()
                    .map { now.toLocalDate().plusDays(it) }
                    .filter { med.isScheduledOn(it.dayOfWeek) }
                    .map { LocalDateTime.of(it, time) }
                    .firstOrNull { it.isAfter(now) }
            }
        }.minOrNull()

    fun schedule(context: Context, meds: List<Medication>, now: LocalDateTime = LocalDateTime.now()) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_CODE, Intent(context, MedicationAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val next = nextDoseTrigger(meds, now)
        if (next == null) { am.cancel(pi); return }
        val atMs = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        // Without the exact-alarm permission (denied by default from Android 14)
        // fall back to an inexact Doze-allowed alarm - still better than the
        // 15-minute periodic tick alone, which keeps running as a safety net.
        if (exactAllowed) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
    }

    internal fun runReminderWorkerNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.REPLACE, request)
    }
}

/** Dose alarm rang, or the device rebooted (alarms are cleared at boot): run the reminder check now; it re-arms the next alarm. */
class MedicationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        MedicationAlarms.runReminderWorkerNow(context)
    }
}

/** Alarms are cleared at reboot; only BOOT_COMPLETED is accepted. */
class MedicationBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) MedicationAlarms.runReminderWorkerNow(context)
    }
}
