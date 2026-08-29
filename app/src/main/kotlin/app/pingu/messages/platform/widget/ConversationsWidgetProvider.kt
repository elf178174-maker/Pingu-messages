package app.pingu.messages.platform.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import app.pingu.messages.PinguApplication
import app.pingu.messages.R
import app.pingu.messages.platform.PendingIntents
import app.pingu.messages.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The home-screen widget.
 *
 * Built with classic `RemoteViews` rather than Glance: a widget is rendered by the launcher's
 * process, so it must stay small and dependency-free, and the list adapter model here is exactly
 * what a scrolling list of conversations needs.
 *
 * Rows are served by [ConversationsWidgetService]; this provider only draws the frame, the unread
 * badge and the compose button, and wires the tap targets.
 */
class ConversationsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { widgetId -> render(context, appWidgetManager, widgetId) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            manager.getAppWidgetIds(
                android.content.ComponentName(context, ConversationsWidgetProvider::class.java),
            ).forEach { render(context, manager, it) }
        }
    }

    private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_conversations)

        views.setRemoteAdapter(
            R.id.widget_list,
            Intent(context, ConversationsWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                // The data URI makes each widget's intent distinct so adapters are not shared.
                data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            },
        )
        views.setEmptyView(R.id.widget_list, R.id.widget_empty)

        views.setOnClickPendingIntent(R.id.widget_title, openAppIntent(context))
        views.setOnClickPendingIntent(R.id.widget_compose, composeIntent(context))
        views.setPendingIntentTemplate(R.id.widget_list, conversationTemplate(context))

        manager.updateAppWidget(widgetId, views)

        // The unread badge needs a database read, so it is filled in asynchronously.
        val container = (context.applicationContext as PinguApplication).container
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val unread = container.conversationRepository.recent(UNREAD_SCAN_LIMIT)
                .sumOf { it.unreadCount }
            val update = RemoteViews(context.packageName, R.layout.widget_conversations)
            if (unread > 0) {
                update.setTextViewText(R.id.widget_unread_badge, formatBadge(unread))
                // The badge reads "99+" once it saturates, which tells a screen reader nothing.
                update.setContentDescription(
                    R.id.widget_unread_badge,
                    context.resources.getQuantityString(R.plurals.unread_count, unread, unread),
                )
                update.setViewVisibility(R.id.widget_unread_badge, View.VISIBLE)
            } else {
                update.setViewVisibility(R.id.widget_unread_badge, View.GONE)
            }
            manager.partiallyUpdateAppWidget(widgetId, update)
        }
    }

    private fun formatBadge(count: Int): String = if (count > MAX_BADGE) "$MAX_BADGE+" else "$count"

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        PendingIntents.nextRequestCode(),
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntents.immutable,
    )

    private fun composeIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        PendingIntents.nextRequestCode(),
        Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_COMPOSE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntents.immutable,
    )

    /**
     * A template PendingIntent: list rows supply only the differing extras, which is the only way
     * a collection widget can have per-row taps.
     */
    private fun conversationTemplate(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        PendingIntents.nextRequestCode(),
        Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_CONVERSATION
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntents.mutable,
    )

    private companion object {
        const val UNREAD_SCAN_LIMIT = 500
        const val MAX_BADGE = 99
    }
}
