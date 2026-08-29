package app.pingu.messages.platform.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import app.pingu.messages.PinguApplication
import app.pingu.messages.R
import app.pingu.messages.core.time.RelativeTime
import app.pingu.messages.core.time.TimeBucket
import app.pingu.messages.core.util.Avatars
import app.pingu.messages.domain.model.Conversation
import app.pingu.messages.ui.MainActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.runBlocking

/** Supplies the rows of the conversations widget. */
class ConversationsWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        ConversationsWidgetFactory(applicationContext)
}

/**
 * The widget's list adapter.
 *
 * `RemoteViewsFactory` is a synchronous API called from the launcher, so [onDataSetChanged] blocks
 * on a single bounded query. That is the contract the framework defines for this callback: it is
 * invoked off the main thread precisely so it can load data.
 */
private class ConversationsWidgetFactory(
    private val context: Context,
) : RemoteViewsService.RemoteViewsFactory {

    private var conversations: List<Conversation> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val container = (context.applicationContext as PinguApplication).container
        conversations = runBlocking { container.conversationRepository.recent(MAX_ROWS) }
    }

    override fun onDestroy() {
        conversations = emptyList()
    }

    override fun getCount(): Int = conversations.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_conversation_item)
        val conversation = conversations.getOrNull(position) ?: return views

        val title = conversation.title.ifBlank { context.getString(R.string.contact_unknown) }
        views.setTextViewText(R.id.item_title, title)
        views.setTextViewText(R.id.item_snippet, snippetFor(conversation))
        views.setTextViewText(R.id.item_timestamp, timestampFor(conversation.lastMessageTimestamp))
        views.setTextViewText(R.id.item_avatar, Avatars.initials(title))
        views.setViewVisibility(R.id.item_avatar, View.VISIBLE)

        views.setOnClickFillInIntent(
            R.id.item_title,
            Intent().putExtra(MainActivity.EXTRA_THREAD_ID, conversation.threadId),
        )
        return views
    }

    private fun snippetFor(conversation: Conversation): String = when {
        conversation.hasDraft -> context.getString(R.string.conversation_draft_prefix) + " " +
            conversation.draftText.orEmpty()

        conversation.snippet.isNotBlank() -> conversation.snippet
        conversation.snippetHasAttachment -> context.getString(R.string.conversation_attachment)
        else -> ""
    }

    private fun timestampFor(timestamp: Long): String {
        if (timestamp <= 0L) return ""
        val zone = ZoneId.systemDefault()
        val instant = Instant.ofEpochMilli(timestamp)
        val formatter = when (RelativeTime.bucket(timestamp, System.currentTimeMillis(), zone)) {
            TimeBucket.TODAY -> DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
            TimeBucket.YESTERDAY, TimeBucket.THIS_WEEK ->
                DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

            TimeBucket.THIS_YEAR -> DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
            TimeBucket.OLDER -> DateTimeFormatter.ofPattern("dd/MM/yy", Locale.getDefault())
        }
        return formatter.withZone(zone).format(instant)
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        conversations.getOrNull(position)?.threadId ?: position.toLong()

    override fun hasStableIds(): Boolean = true

    private companion object {
        const val MAX_ROWS = 25
    }
}
