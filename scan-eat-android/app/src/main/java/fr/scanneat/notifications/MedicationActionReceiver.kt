package fr.scanneat.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import fr.scanneat.data.repository.health.MedicationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Roadmap D6: actions on a dose reminder.
 * - TAKEN: logs the dose (same [MedicationRepository.logTaken] as the app,
 *   one log per scheduled dose) and removes the notification.
 * - SNOOZE: removes it and posts it again [SNOOZE_MINUTES] later.
 */
@AndroidEntryPoint
class MedicationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var medicationRepo: MedicationRepository

    override fun onReceive(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        val medId = intent.getStringExtra(EXTRA_MED_ID) ?: return
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID) ?: "default"
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        val action = DoseAction(medId, profileId)
        when (intent.action) {
            ACTION_TAKEN -> {
                NotificationManagerCompat.from(context).cancel(notifId)
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        medicationRepo.observeAll(profileId).first().firstOrNull { it.id == medId }
                            ?.let { medicationRepo.logTaken(it, profileId = profileId) }
                    } finally {
                        pending.finish()
                    }
                }
            }
            ACTION_SNOOZE -> {
                NotificationManagerCompat.from(context).cancel(notifId)
                val am = context.getSystemService(AlarmManager::class.java) ?: return
                am.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L,
                    pendingIntent(context, ACTION_REPOST, notifId, title, text, action),
                )
            }
            ACTION_REPOST -> NotificationHelper.show(context, notifId, title, text, NotifChannel.MEDICATION, doseAction = action)
        }
    }

    companion object {
        const val ACTION_TAKEN = "fr.scanneat.action.DOSE_TAKEN"
        const val ACTION_SNOOZE = "fr.scanneat.action.DOSE_SNOOZE"
        private const val ACTION_REPOST = "fr.scanneat.action.DOSE_REPOST"
        private const val EXTRA_NOTIF_ID = "notif_id"
        private const val EXTRA_MED_ID = "med_id"
        private const val EXTRA_PROFILE_ID = "profile_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        const val SNOOZE_MINUTES = 15L

        fun pendingIntent(context: Context, action: String, notifId: Int, title: String, text: String, dose: DoseAction): PendingIntent {
            val intent = Intent(context, MedicationActionReceiver::class.java).setAction(action)
                .putExtra(EXTRA_NOTIF_ID, notifId).putExtra(EXTRA_MED_ID, dose.medicationId)
                .putExtra(EXTRA_PROFILE_ID, dose.profileId).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text)
            // Distinct request code per (notification, action) so the three PendingIntents never overwrite each other.
            return PendingIntent.getBroadcast(context, notifId * 3 + when (action) { ACTION_TAKEN -> 0; ACTION_SNOOZE -> 1; else -> 2 }, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
