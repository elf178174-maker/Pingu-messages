package app.pingu.messages.domain.model

/** Whether a message travelled as SMS or as MMS. */
enum class MessageTransport { SMS, MMS }

/**
 * A message as the app understands it, assembled from the system telephony provider plus the
 * app-local metadata (reactions, reply links) that SMS itself cannot carry.
 */
data class Message(
    /** Local row id in the app's mirror database. Stable for the lifetime of the message. */
    val id: Long,
    val threadId: Long,
    /** `_id` in `content://sms` or `content://mms`. Zero for messages not yet written there. */
    val systemId: Long,
    val transport: MessageTransport,
    val address: String?,
    val body: String?,
    val subject: String? = null,
    /** When the message was received, or when an outgoing message was created. */
    val timestamp: Long,
    /** When the sender says they sent it, where the network provides it. */
    val sentTimestamp: Long = 0L,
    val isOutgoing: Boolean,
    val isRead: Boolean,
    val status: MessageStatus,
    /** Carrier/platform error code kept for the message-details sheet. Zero when there is none. */
    val errorCode: Int = 0,
    /** Subscription id of the SIM used, or -1 when unknown / single SIM. */
    val subscriptionId: Int = -1,
    val attachments: List<Attachment> = emptyList(),
    val reactions: List<Reaction> = emptyList(),
    /** Local reply link to another message in the same thread. */
    val replyToMessageId: Long? = null,
    /** Snapshot of the quoted text, so a reply survives deletion of the original. */
    val replyToSnippet: String? = null,
    /** Size of the whole MMS in bytes, as reported by the provider. */
    val sizeBytes: Long = 0L,
) {
    val hasAttachments: Boolean get() = attachments.isNotEmpty()

    val hasText: Boolean get() = !body.isNullOrBlank()

    val isFailed: Boolean get() = status.isFailure

    val visualAttachments: List<Attachment>
        get() = attachments.filter { it.kind.isVisualMedia }

    /** True when the message is an MMS notification whose body still has to be fetched. */
    val needsDownload: Boolean
        get() = status == MessageStatus.PENDING_DOWNLOAD || status == MessageStatus.DOWNLOAD_FAILED
}
