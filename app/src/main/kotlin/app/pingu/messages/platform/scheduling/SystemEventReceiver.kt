package app.pingu.messages.platform.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.pingu.messages.PinguApplication
import app.pingu.messages.platform.BroadcastScope

/**
 * Re-arms scheduled messages after events that clear the alarm queue.
 *
 * Android drops every alarm on reboot and when an app is updated, and changing the clock or the
 * timezone silently moves when an alarm would fire. All four are handled: the queue is read back
 * from the database and re-armed, and anything whose time passed while the device was off is sent
 * immediately.
 */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return

        val container = (context.applicationContext as PinguApplication).container
        BroadcastScope.launch(this, TAG) {
            Log.i(TAG, "Re-arming scheduled work after $action")
            container.scheduledMessageScheduler.rescheduleAll()
            container.scheduledMessageDispatcher.dispatchDue()
            container.widgetUpdater.requestUpdate()
        }
    }

    private companion object {
        const val TAG = "SystemEventReceiver"

        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
