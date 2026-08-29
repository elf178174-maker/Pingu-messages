package app.pingu.messages.platform.notification

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import app.pingu.messages.R
import app.pingu.messages.core.util.PhoneNumbers
import app.pingu.messages.domain.model.Attachment
import app.pingu.messages.domain.model.Conversation
import app.pingu.messages.domain.model.Message
import app.pingu.messages.domain.model.NotificationPrivacy
import app.pingu.messages.platform.PendingIntents
import app.pingu.messages.platform.shortcut.ConversationShortcutManager
import app.pingu.messages.ui.ConversationWindowActivity
import app.pingu.messages.ui.MainActivity

/**
 * Builds and posts message notifications.
 *
 * The notification is a `MessagingStyle` one, tagged with the conversation's shortcut, which is
 * what makes Android treat it as a conversation: an avatar in the shade, its own entry in system
 * notification settings, an optional bubble, and correct behaviour with Do Not Disturb's "priority
 * conversations".
 *
 * Privacy is applied at build time rather than by hoping the system hides things: when the user
 * asks for sender-only or hidden notifications, the message text is never put into the notification
 * at all, so it cannot leak through a smartwatch, a car display or a screenshot.
 */
class MessageNotifier(
    private val context: Context,
    private val avatars: AvatarBitmaps,
    private val shortcuts: ConversationShortcutManager,
) {

    private val manager = NotificationManagerCompat.from(context)

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** True when the user has not silenced this conversation, globally or individually. */
    fun shouldNotify(conversation: Conversation, now: Long = System.currentTimeMillis()): Boolean =
        conversation.notificationsEnabled &&
            !conversation.isMutedAt(now) &&
            !conversation.isBlocked &&
            !conversation.isSpam

    /**
     * Posts (or updates) the notification for a conversation.
     *
     * @param messages unread incoming messages, oldest first.
     */
    fun notifyConversation(
        conversation: Conversation,
        messages: List<Message>,
        privacy: NotificationPrivacy,
        vibrate: Boolean,
        bubblesEnabled: Boolean,
    ) {
        if (!hasPermission() || messages.isEmpty()) return
        if (privacy == NotificationPrivacy.NONE) {
            // The user asked for no notification content at all; a silent badge is all that is left
            // that does not leak anything.
            postMinimal(conversation)
            return
        }

        NotificationChannels.ensureCreated(context)
        shortcuts.push(conversation)

        val notificationId = notificationIdFor(conversation.threadId)
        val self = Person.Builder().setName(context.getString(R.string.contact_me)).build()
        val style = NotificationCompat.MessagingStyle(self)
            .setGroupConversation(conversation.isGroup)

        if (privacy == NotificationPrivacy.FULL) {
            style.conversationTitle = if (conversation.isGroup) conversation.title else null
            messages.takeLast(MAX_MESSAGES_IN_STYLE).forEach { message ->
                style.addMessage(
                    messageText(message),
                    message.timestamp,
                    personFor(conversation, message),
                )
            }
        } else {
            style.conversationTitle = null
            style.addMessage(
                context.getString(R.string.notification_new_message),
                messages.last().timestamp,
                personFor(conversation, messages.last()),
            )
        }

        val builder = NotificationCompat.Builder(context, NotificationChannels.MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setShowWhen(true)
            .setWhen(messages.last().timestamp)
            .setNumber(messages.size)
            .setGroup(GROUP_KEY)
            .setShortcutId(shortcuts.shortcutIdFor(conversation.threadId))
            .setLocusId(shortcuts.locusIdFor(conversation.threadId))
            .setContentIntent(openConversationIntent(conversation.threadId))
            .setDeleteIntent(dismissIntent(conversation.threadId))
            .setVisibility(visibilityFor(privacy))
            .addAction(replyAction(conversation.threadId))
            .addAction(markReadAction(conversation.threadId))

        if (!vibrate) builder.setVibrate(longArrayOf(0L))

        if (privacy != NotificationPrivacy.FULL) {
            builder.setContentTitle(conversation.title)
            builder.setContentText(context.getString(R.string.notification_hidden_content))
        }

        if (bubblesEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setBubbleMetadata(bubbleMetadata(conversation))
        }

        postSummary()
        runCatching { manager.notify(notificationId, builder.build()) }
    }

    /** A notification with no content at all, for the strictest privacy setting. */
    private fun postMinimal(conversation: Conversation) {
        NotificationChannels.ensureCreated(context)
        val builder = NotificationCompat.Builder(context, NotificationChannels.MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_new_message))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setContentIntent(openConversationIntent(conversation.threadId))
            .setDeleteIntent(dismissIntent(conversation.threadId))
        postSummary()
        runCatching { manager.notify(notificationIdFor(conversation.threadId), builder.build()) }
    }

    /** Reports a message that could not be sent, with a way back into the conversation. */
    fun notifySendFailure(threadId: Long, conversationTitle: String) {
        if (!hasPermission()) return
        NotificationChannels.ensureCreated(context)
        val builder = NotificationCompat.Builder(context, NotificationChannels.FAILURES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_send_failed_title))
            .setContentText(context.getString(R.string.notification_send_failed_body))
            .setSubText(conversationTitle)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(openConversationIntent(threadId))
        runCatching { manager.notify(failureNotificationIdFor(threadId), builder.build()) }
    }

    fun cancelConversation(threadId: Long) {
        runCatching { manager.cancel(notificationIdFor(threadId)) }
        cancelSummaryIfEmpty()
    }

    fun cancelFailure(threadId: Long) {
        runCatching { manager.cancel(failureNotificationIdFor(threadId)) }
    }

    fun cancelAll() {
        runCatching { manager.cancelAll() }
    }

    private fun messageText(message: Message): CharSequence {
        val body = message.body?.trim().orEmpty()
        if (body.isNotEmpty()) return body
        val attachment = message.attachments.firstOrNull()
        return attachment?.let { describeAttachment(it) }
            ?: context.getString(R.string.conversation_attachment)
    }

    private fun describeAttachment(attachment: Attachment): String = when {
        attachment.mimeType.startsWith("image/") -> context.getString(R.string.cd_attachment_image)
        attachment.mimeType.startsWith("video/") -> context.getString(R.string.cd_attachment_video)
        attachment.mimeType.startsWith("audio/") -> context.getString(R.string.cd_attachment_audio)
        else -> context.getString(R.string.cd_attachment_file)
    }

    private fun personFor(conversation: Conversation, message: Message): Person {
        val recipient = conversation.recipients.firstOrNull { candidate ->
            message.address != null &&
                PhoneNumbers.sameNumber(candidate.address, message.address)
        } ?: conversation.recipients.firstOrNull()

        val name = recipient?.label ?: message.address.orEmpty()
        val icon = IconCompat.createWithAdaptiveBitmap(
            avatars.forRecipient(
                displayName = recipient?.displayName,
                identityKey = recipient?.address ?: message.address.orEmpty(),
                photoUri = recipient?.photoUri,
            ),
        )
        return Person.Builder()
            .setName(name)
            .setKey(recipient?.address ?: message.address)
            .setIcon(icon)
            .setUri(recipient?.address?.let { "tel:$it" })
            .build()
    }

    private fun visibilityFor(privacy: NotificationPrivacy): Int = when (privacy) {
        NotificationPrivacy.FULL -> NotificationCompat.VISIBILITY_PUBLIC
        NotificationPrivacy.SENDER_ONLY -> NotificationCompat.VISIBILITY_PRIVATE
        NotificationPrivacy.HIDDEN -> NotificationCompat.VISIBILITY_PRIVATE
        NotificationPrivacy.NONE -> NotificationCompat.VISIBILITY_SECRET
    }

    private fun replyAction(threadId: Long): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY_TEXT)
            .setLabel(context.getString(R.string.notification_reply_label))
            .build()

        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_REPLY
            putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, threadId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            PendingIntents.nextRequestCode(),
            intent,
            PendingIntents.mutable,
        )
        return NotificationCompat.Action.Builder(
            IconCompat.createWithResource(context, R.drawable.ic_shortcut_compose),
            context.getString(R.string.notification_reply_label),
            pendingIntent,
        )
            .addRemoteInput(remoteInput)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .setAllowGeneratedReplies(true)
            .build()
    }

    private fun markReadAction(threadId: Long): NotificationCompat.Action {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_MARK_READ
            putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, threadId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            PendingIntents.nextRequestCode(),
            intent,
            PendingIntents.immutable,
        )
        return NotificationCompat.Action.Builder(
            IconCompat.createWithResource(context, R.drawable.ic_notification),
            context.getString(R.string.action_mark_read),
            pendingIntent,
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
    }

    private fun bubbleMetadata(conversation: Conversation): NotificationCompat.BubbleMetadata {
        val intent = Intent(context, ConversationWindowActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_CONVERSATION
            putExtra(MainActivity.EXTRA_THREAD_ID, conversation.threadId)
            flags = Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            PendingIntents.nextRequestCode(),
            intent,
            PendingIntents.mutable,
        )
        val recipient = conversation.recipients.firstOrNull()
        return NotificationCompat.BubbleMetadata.Builder(
            pendingIntent,
            IconCompat.createWithAdaptiveBitmap(
                avatars.forRecipient(
                    displayName = recipient?.displayName,
                    identityKey = recipient?.address.orEmpty(),
                    photoUri = recipient?.photoUri,
                ),
            ),
        )
            .setDesiredHeight(BUBBLE_HEIGHT_DP)
            .setAutoExpandBubble(false)
            .setSuppressNotification(false)
            .build()
    }

    private fun openConversationIntent(threadId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_CONVERSATION
            putExtra(MainActivity.EXTRA_THREAD_ID, threadId)
            data = Uri.parse("pingu://conversation/$threadId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            PendingIntents.nextRequestCode(),
            intent,
            PendingIntents.immutable,
        )
    }

    private fun dismissIntent(threadId: Long): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISMISS
            putExtra(NotificationActionReceiver.EXTRA_THREAD_ID, threadId)
        }
        return PendingIntent.getBroadcast(
            context,
            PendingIntents.nextRequestCode(),
            intent,
            PendingIntents.immutable,
        )
    }

    /** The group summary keeps several conversations collapsed into one row in the shade. */
    private fun postSummary() {
        val builder = NotificationCompat.Builder(context, NotificationChannels.MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_group_summary))
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(openConversationIntent(0L))
        // On the versions that collapse the group behind the summary row, the count is the only
        // thing the user sees, so it is worth saying how many conversations are waiting.
        val conversations = groupedNotificationCount()
        if (conversations > 0) {
            builder.setContentText(
                context.resources.getQuantityString(
                    R.plurals.new_messages,
                    conversations,
                    conversations,
                ),
            )
        }
        runCatching { manager.notify(SUMMARY_NOTIFICATION_ID, builder.build()) }
    }

    private fun cancelSummaryIfEmpty() {
        if (groupedNotificationCount() == 0) {
            runCatching { manager.cancel(SUMMARY_NOTIFICATION_ID) }
        }
    }

    /**
     * How many conversation notifications are currently in the shade.
     *
     * Returns zero when the platform refuses to say, which is the safe answer: it only ever means
     * the summary loses its subtitle or stays posted a little longer than it needed to.
     */
    private fun groupedNotificationCount(): Int {
        val notificationManager =
            ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return 0
        return runCatching {
            notificationManager.activeNotifications.count {
                it.id != SUMMARY_NOTIFICATION_ID && it.groupKey?.contains(GROUP_KEY) == true
            }
        }.getOrDefault(0)
    }

    companion object {
        private const val GROUP_KEY = "app.pingu.messages.CONVERSATIONS"
        private const val SUMMARY_NOTIFICATION_ID = 1
        private const val MAX_MESSAGES_IN_STYLE = 8
        private const val BUBBLE_HEIGHT_DP = 640

        fun notificationIdFor(threadId: Long): Int = (threadId.hashCode() and 0x00FF_FFFF) or 0x0100_0000

        fun failureNotificationIdFor(threadId: Long): Int =
            (threadId.hashCode() and 0x00FF_FFFF) or 0x0200_0000
    }
}
