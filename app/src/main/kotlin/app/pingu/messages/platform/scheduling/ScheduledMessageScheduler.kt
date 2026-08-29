package app.pingu.messages.platform.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.pingu.messages.data.repository.ScheduledMessageRepository
import java.util.concurrent.TimeUnit

/**
 * Arms the alarms that send scheduled messages.
 *
 * Android gives no guarantee that a process stays alive, so nothing is held in memory: every
 * pending message is a database row, and this class turns those rows into alarms. The alarms are
 * re-armed after a reboot, an app update, a time change and a timezone change, all handled by
 * [SystemEventReceiver].
 *
 * Exact alarms need a permission the user can refuse, and Android 12 and later revoke it freely.
 * The app degrades honestly instead of pretending: without it, the alarm becomes an inexact one and
 * a periodic WorkManager sweep catches anything the system delayed past its time. That is a few
 * minutes of imprecision, not a lost message, and the UI says so before the user schedules.
 */
class ScheduledMessageScheduler(
    private val context: Context,
    private val repository: ScheduledMessageRepository,
) {

    private val alarmManager: AlarmManager?
        get() = ContextCompat.getSystemService(context, AlarmManager::class.java)

    /** True when the platform will honour an exact alarm from this app right now. */
    fun canScheduleExactAlarms(): Boolean = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> true
        else -> alarmManager?.canScheduleExactAlarms() == true
    }

    /** The system screen where the user can grant exact alarms; null when it does not apply. */
    fun exactAlarmSettingsIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms()) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
            }
        } else {
            null
        }

    fun schedule(scheduledMessageId: Long, triggerAtMillis: Long) {
        val manager = alarmManager ?: return
        val pendingIntent = alarmIntent(scheduledMessageId)
        try {
            if (canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (error: SecurityException) {
            // The permission was revoked between the check and the call.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
        ensureSweepScheduled()
    }

    fun cancel(scheduledMessageId: Long) {
        alarmManager?.cancel(alarmIntent(scheduledMessageId))
    }

    /** Re-arms every pending message. Called after a reboot, an update or a clock change. */
    suspend fun rescheduleAll() {
        val pending = repository.pending()
        pending.forEach { message ->
            schedule(message.id, message.scheduledAt)
        }
        if (pending.isNotEmpty()) ensureSweepScheduled()
        Log.i(TAG, "Re-armed ${pending.size} scheduled messages")
    }

    /**
     * A periodic safety net.
     *
     * Doze, battery optimisation and inexact alarms can all delay a send. The sweep runs every 15
     * minutes and dispatches anything whose time has passed, so a delayed message still goes out
     * rather than sitting in the queue forever.
     */
    fun ensureSweepScheduled() {
        val request = PeriodicWorkRequestBuilder<ScheduledMessageSweepWorker>(
            SWEEP_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SWEEP_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }

    private fun alarmIntent(scheduledMessageId: Long): PendingIntent {
        val intent = Intent(context, ScheduledMessageReceiver::class.java).apply {
            action = ScheduledMessageReceiver.ACTION_SEND_SCHEDULED
            putExtra(ScheduledMessageReceiver.EXTRA_SCHEDULED_ID, scheduledMessageId)
            // Distinguishes the PendingIntents, which otherwise compare equal (extras are ignored).
            data = android.net.Uri.parse("pingu://scheduled/$scheduledMessageId")
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_BASE + scheduledMessageId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val TAG = "ScheduledMessages"
        const val SWEEP_WORK_NAME = "scheduled-message-sweep"
        const val SWEEP_INTERVAL_MINUTES = 15L
        const val REQUEST_CODE_BASE = 0x5000_0000
    }
}
