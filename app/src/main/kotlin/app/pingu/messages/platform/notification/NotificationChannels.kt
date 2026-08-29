package app.pingu.messages.platform.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.content.ContextCompat
import app.pingu.messages.R

/**
 * Notification channels.
 *
 * Three channels, each matching a genuinely different kind of interruption, so the user can silence
 * one without losing the others. Channel settings belong to the user once created: the app sets
 * sensible defaults on first run and never re-creates a channel to override a choice, which is
 * exactly what Android intends and what "Sound and vibration" in Settings links out to.
 *
 * Per-conversation settings are not separate channels either. On Android 11 and later a
 * notification tagged with a conversation shortcut gets its own section in system settings
 * automatically, which is better than the dozens of channels the alternative would create.
 */
object NotificationChannels {

    const val MESSAGES = "messages"
    const val FAILURES = "send_failures"
    const val BACKGROUND = "background"

    fun ensureCreated(context: Context) {
        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?: return

        if (manager.getNotificationChannel(MESSAGES) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    MESSAGES,
                    context.getString(R.string.notification_channel_messages),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.notification_channel_messages_description)
                    enableVibration(true)
                    enableLights(true)
                    setShowBadge(true)
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                            .build(),
                    )
                },
            )
        }

        if (manager.getNotificationChannel(FAILURES) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    FAILURES,
                    context.getString(R.string.notification_channel_failures),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.notification_channel_failures_description)
                    setShowBadge(true)
                },
            )
        }

        if (manager.getNotificationChannel(BACKGROUND) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    BACKGROUND,
                    context.getString(R.string.notification_channel_background),
                    NotificationManager.IMPORTANCE_MIN,
                ).apply {
                    description = context.getString(R.string.notification_channel_background_description)
                    setShowBadge(false)
                },
            )
        }
    }
}
