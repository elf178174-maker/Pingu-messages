package app.pingu.messages.platform.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.pingu.messages.PinguApplication
import app.pingu.messages.platform.BroadcastScope

/** Fired by the alarm manager when a scheduled message is due. */
class ScheduledMessageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SEND_SCHEDULED) return
        val id = intent.getLongExtra(EXTRA_SCHEDULED_ID, 0L)
        if (id <= 0L) return

        val container = (context.applicationContext as PinguApplication).container
        BroadcastScope.launch(this, TAG) {
            container.scheduledMessageDispatcher.dispatch(id)
            container.widgetUpdater.requestUpdate()
        }
    }

    companion object {
        private const val TAG = "ScheduledMessageAlarm"
        const val ACTION_SEND_SCHEDULED = "app.pingu.messages.action.SEND_SCHEDULED"
        const val EXTRA_SCHEDULED_ID = "scheduled_id"
    }
}
