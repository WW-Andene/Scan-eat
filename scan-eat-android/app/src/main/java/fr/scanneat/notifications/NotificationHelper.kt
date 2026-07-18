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
}

object NotificationHelper {
    const val CHANNEL_ID = "reminders"

    private const val LEGACY_CHANNEL_ID = "biolism_reminders"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            NotifChannel.entries.forEach { ch ->
                manager.createNotificationChannel(
                    NotificationChannel(ch.id, context.getString(ch.nameRes), NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            // Keep the legacy catch-all channel for any notification still using CHANNEL_ID
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.reminders_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
            )
            manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        }
    }

    fun show(context: Context, id: Int, title: String, text: String, channel: NotifChannel? = null) {
        ensureChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val channelId = channel?.id ?: CHANNEL_ID
        // Previously absent - every notification this app posts (meal/hydration/
        // weight/activity/fasting-target/daily digest, and critically the
        // medication dose reminder) was a dead end when tapped: it just
        // auto-cancelled with no navigation back into the app at all.
        val contentIntent = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
