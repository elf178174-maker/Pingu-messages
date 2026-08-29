package app.pingu.messages.platform.shortcut

import android.content.Context
import android.content.Intent
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.pingu.messages.domain.model.Conversation
import app.pingu.messages.platform.notification.AvatarBitmaps
import app.pingu.messages.ui.MainActivity

/**
 * Dynamic conversation shortcuts.
 *
 * These do three jobs at once on modern Android, which is why they are worth publishing even though
 * the launcher is the most visible one:
 *
 *  * long-pressing the launcher icon offers recent conversations;
 *  * a notification that names a shortcut becomes a *conversation* notification, which is what
 *    gives it its own section in system settings, an avatar in the shade, and the option to bubble;
 *  * the share sheet can offer a conversation as a direct share target.
 *
 * The shortcut id is the thread id, so it stays stable as long as the conversation exists.
 */
class ConversationShortcutManager(
    private val context: Context,
    private val avatars: AvatarBitmaps,
) {

    fun shortcutIdFor(threadId: Long): String = "$SHORTCUT_PREFIX$threadId"

    fun locusIdFor(threadId: Long): LocusIdCompat = LocusIdCompat(shortcutIdFor(threadId))

    fun personFor(conversation: Conversation): Person {
        val recipient = conversation.recipients.firstOrNull()
        val icon = IconCompat.createWithAdaptiveBitmap(
            avatars.forRecipient(
                displayName = recipient?.displayName,
                identityKey = recipient?.address.orEmpty(),
                photoUri = recipient?.photoUri,
            ),
        )
        return Person.Builder()
            .setName(conversation.title.ifBlank { recipient?.label.orEmpty() })
            .setKey(conversation.threadId.toString())
            .setIcon(icon)
            .setImportant(conversation.isPinned)
            .build()
    }

    /** Publishes (or refreshes) the shortcut for one conversation. */
    fun push(conversation: Conversation) {
        runCatching {
            ShortcutManagerCompat.pushDynamicShortcut(context, build(conversation))
        }
    }

    /** Replaces the whole set, used after a sync so the launcher list matches the inbox. */
    fun publishRecent(conversations: List<Conversation>) {
        runCatching {
            val limit = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
                .coerceAtLeast(MIN_SHORTCUTS)
            ShortcutManagerCompat.setDynamicShortcuts(
                context,
                conversations.take(limit - RESERVED_STATIC_SHORTCUTS).map(::build),
            )
        }
    }

    fun remove(threadIds: List<Long>) {
        runCatching {
            ShortcutManagerCompat.removeDynamicShortcuts(context, threadIds.map(::shortcutIdFor))
        }
    }

    private fun build(conversation: Conversation): ShortcutInfoCompat {
        val title = conversation.title.ifBlank { "" }
        val recipient = conversation.recipients.firstOrNull()
        val icon = IconCompat.createWithAdaptiveBitmap(
            avatars.forRecipient(
                displayName = recipient?.displayName,
                identityKey = recipient?.address.orEmpty(),
                photoUri = recipient?.photoUri,
            ),
        )
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_CONVERSATION
            putExtra(MainActivity.EXTRA_THREAD_ID, conversation.threadId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return ShortcutInfoCompat.Builder(context, shortcutIdFor(conversation.threadId))
            .setShortLabel(title)
            .setLongLabel(title)
            .setIcon(icon)
            .setIntent(intent)
            .setLongLived(true)
            .setLocusId(locusIdFor(conversation.threadId))
            .setPerson(personFor(conversation))
            .setCategories(setOf(SHARE_TARGET_CATEGORY))
            .build()
    }

    companion object {
        private const val SHORTCUT_PREFIX = "conversation_"
        private const val SHARE_TARGET_CATEGORY = "android.shortcut.conversation"

        /** Two static shortcuts (new message, search) must keep their slots. */
        private const val RESERVED_STATIC_SHORTCUTS = 2
        private const val MIN_SHORTCUTS = 4
    }
}
