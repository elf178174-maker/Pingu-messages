package app.pingu.messages.platform.messaging

import android.net.Uri
import android.util.Log
import app.pingu.messages.core.text.QuotedReply
import app.pingu.messages.core.text.Tapbacks
import app.pingu.messages.core.util.PhoneNumbers
import app.pingu.messages.data.contacts.ContactIndex
import app.pingu.messages.data.preferences.SettingsStore
import app.pingu.messages.data.repository.BlockedNumberRepository
import app.pingu.messages.data.repository.ConversationRepository
import app.pingu.messages.data.repository.MessageRepository
import app.pingu.messages.data.repository.SyncRepository
import app.pingu.messages.domain.model.BlockOrigin
import app.pingu.messages.domain.model.Message
import app.pingu.messages.platform.notification.MessageNotifier
import app.pingu.messages.platform.shortcut.ConversationShortcutManager
import app.pingu.messages.platform.widget.WidgetUpdater
import kotlinx.coroutines.flow.first

/**
 * What happens after a message arrives.
 *
 * Receiving is more than storing bytes: the sender may be blocked, the message may be a reaction
 * another messenger encoded as text, and the user has to be told - or deliberately not told. This
 * class owns that sequence so the SMS and MMS receivers stay thin and behave identically.
 */
class IncomingMessageHandler(
    private val syncRepository: SyncRepository,
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
    private val blocked: BlockedNumberRepository,
    private val contacts: ContactIndex,
    private val settings: SettingsStore,
    private val notifier: MessageNotifier,
    private val shortcuts: ConversationShortcutManager,
    private val widgets: WidgetUpdater,
) {

    /** Handles an SMS that has already been written to the provider. */
    suspend fun onSmsStored(uri: Uri) {
        val localId = syncRepository.syncSingleSms(uri) ?: return
        val message = messages.getMessage(localId) ?: return
        finish(message)
    }

    /** Handles an MMS that has already been written to the provider. */
    suspend fun onMmsStored(systemId: Long) {
        val localId = syncRepository.syncSingleMms(systemId) ?: return
        val message = messages.getMessage(localId) ?: return
        finish(message)
    }

    /**
     * Decides whether a sender is blocked before anything user-visible happens.
     * The message is still stored: hiding it entirely would make "unblock" unable to show history.
     */
    suspend fun isBlocked(address: String?): Boolean =
        address != null && blocked.isBlocked(address)

    private suspend fun finish(message: Message) {
        if (message.isOutgoing) return
        val threadId = message.threadId
        conversations.ensureMetadata(threadId)

        val senderBlocked = isBlocked(message.address)
        if (senderBlocked) {
            conversations.setBlocked(listOf(threadId), true)
            widgets.requestUpdate()
            return
        }

        if (applyIncomingTapback(message)) {
            // The message was a reaction, not a message: it is folded into the target bubble and
            // must not produce a bubble or a notification of its own.
            widgets.requestUpdate()
            return
        }

        applyIncomingQuote(message)

        val current = settings.settings.first()
        if (current.spamFilterEnabled) {
            val knownContact = contacts.lookup(message.address.orEmpty()) != null
            if (blocked.looksLikeSpam(message.body, knownContact)) {
                conversations.setSpam(listOf(threadId), true)
                blocked.block(
                    address = message.address.orEmpty(),
                    origin = BlockOrigin.REPORTED_SPAM,
                    note = SPAM_NOTE,
                )
                widgets.requestUpdate()
                return
            }
        }

        val conversation = conversations.getConversation(threadId) ?: return
        shortcuts.push(conversation)

        if (notifier.shouldNotify(conversation)) {
            val unread = messages.getMessages(unreadIdsFor(threadId))
                .filter { !it.isOutgoing }
                .sortedBy { it.timestamp }
                .ifEmpty { listOf(message) }
            notifier.notifyConversation(
                conversation = conversation,
                messages = unread,
                privacy = current.notificationPrivacy,
                vibrate = current.notificationVibrate,
                bubblesEnabled = current.conversationBubbles,
            )
        }
        widgets.requestUpdate()
    }

    /**
     * Recognises a reaction another messenger sent as plain text and attaches it to the message it
     * refers to. Returns true when the message was consumed as a reaction.
     */
    private suspend fun applyIncomingTapback(message: Message): Boolean {
        val parsed = Tapbacks.parse(message.body) ?: return false
        val target = messages.findMessageByQuotedText(message.threadId, parsed.quotedText)
            ?: return false
        val author = message.address ?: return false
        messages.setRemoteReaction(
            messageId = target.id,
            emoji = if (parsed.removal) null else parsed.emoji,
            authorAddress = author,
        )
        // The carrier message itself is removed so the conversation shows a reaction, not a
        // duplicate line of text.
        messages.delete(listOf(message.id))
        return true
    }

    /** Links an incoming quoted reply to the message it quotes, when we can identify it. */
    private suspend fun applyIncomingQuote(message: Message) {
        val parsed = QuotedReply.parse(message.body) ?: return
        val target = messages.findMessageByQuotedText(message.threadId, parsed.quotedText) ?: return
        runCatching { messages.setReplyLink(message.id, target.id, parsed.quotedText) }
            .onFailure { Log.d(TAG, "Could not link an incoming reply", it) }
    }

    private suspend fun unreadIdsFor(threadId: Long): List<Long> =
        messages.unreadIncomingIds(threadId)

    private companion object {
        const val TAG = "IncomingMessages"
        const val SPAM_NOTE = "Matched a spam keyword"
    }
}

/** Convenience for callers that only have an address, used by the quick-reply service. */
fun normalizeSender(address: String?): String = PhoneNumbers.normalize(address)
