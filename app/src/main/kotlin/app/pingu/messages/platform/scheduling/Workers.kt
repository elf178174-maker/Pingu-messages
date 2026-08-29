package app.pingu.messages.platform.scheduling

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.pingu.messages.PinguApplication
import java.util.concurrent.TimeUnit

/**
 * Background work.
 *
 * Two jobs, both deliberately small:
 *
 *  * [ScheduledMessageSweepWorker] is the safety net for scheduled messages that an inexact alarm
 *    delayed past their time;
 *  * [MaintenanceWorker] keeps the mirror and the media cache tidy.
 *
 * Neither does anything a user is waiting for, so both are ordinary deferrable work rather than
 * expedited jobs.
 */
class ScheduledMessageSweepWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as PinguApplication).container
        return try {
            container.scheduledMessageDispatcher.dispatchDue()
            Result.success()
        } catch (error: Exception) {
            Log.w(TAG, "Sweep failed", error)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "ScheduledSweep"
    }
}

/**
 * Periodic housekeeping: re-syncs the mirror with the telephony provider, drops completed scheduled
 * entries and applies the "delete old media" setting.
 */
class MaintenanceWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as PinguApplication).container
        return try {
            container.syncRepository.syncAll()
            container.scheduledMessageRepository.purgeOld(
                System.currentTimeMillis() - TimeUnit.DAYS.toMillis(COMPLETED_RETENTION_DAYS),
            )
            container.storageMaintenance.applyRetentionPolicy()
            container.widgetUpdater.requestUpdate()
            Result.success()
        } catch (error: Exception) {
            Log.w(TAG, "Maintenance failed", error)
            Result.retry()
        }
    }

    private companion object {
        const val TAG = "Maintenance"
        const val COMPLETED_RETENTION_DAYS = 7L
    }
}
