package fr.scanneat.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.scanneat.R
import fr.scanneat.presentation.MainActivity

/** Per-category notification channels — users can individually mute or
 *  change the importance of meals vs. hydration vs. weight vs. medications
 *  vs. the daily digest in Android's system notification settings. Previously
 *  everything shared a single "Rappels" channel so silencing one type
 *  silenced all of them. */
enum class NotifChannel(val id: String, val nameRes: Int) {
    MEALS      ("reminders_meals",      R.string.notif_channel_meals),
    HYDRATION  ("reminders_hydration",  R.string.notif_channel_hydration),
    WEIGHT     ("reminders_weight",     R.string.notif_channel_weight),
    MEDICATION ("reminders_medication", R.string.notif_channel_medication),
    FASTING    ("reminders_fasting",    R.string.notif_channel_fasting),
    ACTIVITY   ("reminders_activity",   R.string.notif_channel_activity),
    SUMMARY    ("daily_summary",        R.string.notif_channel_summary),
    CUSTOM     ("reminders_custom",     R.string.notif_channel_custom),
    PANTRY     ("reminders_pantry",     R.string.notif_channel_pantry),
    RECALLS    ("product_recalls",      R.string.notif_channel_recalls),
    ;

    /** Content hidden on the lock screen behind a generic public version (C11). */
    val isSensitive: Boolean get() = this == MEDICATION
}

object NotificationHelper {
    const val CHANNEL_ID = "reminders"

    private const val LEGACY_CHANNEL_ID = "biolism_reminders"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        NotifChannel.entries.forEach { ch ->
            manager.createNotificationChannel(
                NotificationChannel(
                    ch.id, context.getString(ch.nameRes),
                    // A government safety recall must be seen (heads-up), not only listed.
                    if (ch == NotifChannel.RECALLS) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    // Roadmap C11: medication names are health data (GDPR art. 9) -
                    // never shown in full on the lock screen.
                    if (ch.isSensitive) lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                }
            )
        }
        // Keep the legacy catch-all channel for any notification still using CHANNEL_ID
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.reminders_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
    }

    /** Intent extra: scan id whose result screen the tap should open (recall notifications). */
    const val EXTRA_OPEN_SCAN_ID = "fr.scanneat.extra.OPEN_SCAN_ID"

    /**
     * @return true if the notification was actually posted, false if it was
     * silently skipped (POST_NOTIFICATIONS denied on API 33+). Callers that also
     * mark a reminder "fired today" (see ReminderWorker) must check this first -
     * previously every reminder type was marked done for the day regardless of
     * whether the user ever saw a notification, so a day with permission denied
     * silently lost every reminder with no way to recover until the next day.
     */
    fun show(
        context: Context, id: Int, title: String, text: String, channel: NotifChannel? = null, openScanId: Long? = null,
        doseAction: DoseAction? = null,
    ): Boolean {
        ensureChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val channelId = channel?.id ?: CHANNEL_ID
        // Previously absent - every notification this app posts (meal/hydration/
        // weight/activity/fasting-target/daily digest, and critically the
        // medication dose reminder) was a dead end when tapped: it just
        // auto-cancelled with no navigation back into the app at all.
        val contentIntent = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .apply { openScanId?.let { putExtra(EXTRA_OPEN_SCAN_ID, it) } },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .apply {
                // Roadmap D6: "Pris" / "Reporter" straight from a dose reminder.
                doseAction?.let { a ->
                    addAction(0, context.getString(R.string.notif_action_taken),
                        MedicationActionReceiver.pendingIntent(context, MedicationActionReceiver.ACTION_TAKEN, id, title, text, a))
                    addAction(0, context.getString(R.string.notif_action_snooze),
                        MedicationActionReceiver.pendingIntent(context, MedicationActionReceiver.ACTION_SNOOZE, id, title, text, a))
                }
                if (channel?.isSensitive == true) {
                    setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    setPublicVersion(
                        NotificationCompat.Builder(context, channelId)
                            .setSmallIcon(R.drawable.ic_notification)
                            .setContentTitle(context.getString(R.string.notif_medication_public_title))
                            .setContentIntent(contentIntent)
                            .setAutoCancel(true)
                            .build()
                    )
                }
            }
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
        return true
    }
}

/** Identifies the dose a medication notification is about (Roadmap D6). */
data class DoseAction(val medicationId: String, val profileId: String)
