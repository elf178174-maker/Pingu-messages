package app.pingu.messages.platform.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Asks every placed widget to refresh.
 *
 * Widgets live in the launcher's process, so the only way to update one is to broadcast to its
 * provider. This is called after anything that changes the inbox: a message arriving, a thread
 * being read, a send completing.
 */
class WidgetUpdater(private val context: Context) {

    fun requestUpdate() {
        runCatching {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val component = ComponentName(context, ConversationsWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isEmpty()) return

            manager.notifyAppWidgetViewDataChanged(ids, R_ID_WIDGET_LIST)
            context.sendBroadcast(
                Intent(context, ConversationsWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                },
            )
        }.onFailure { Log.d(TAG, "Widget refresh failed", it) }
    }

    private companion object {
        const val TAG = "WidgetUpdater"
        val R_ID_WIDGET_LIST = app.pingu.messages.R.id.widget_list
    }
}
