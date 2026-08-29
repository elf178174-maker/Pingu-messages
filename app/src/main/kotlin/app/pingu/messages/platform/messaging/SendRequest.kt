package app.pingu.messages.platform.messaging

import app.pingu.messages.domain.model.Attachment

/** Everything needed to send one message. */
data class SendRequest(
    /** Known thread, or null to resolve (and create) one from [recipients]. */
    val threadId: Long? = null,
    val recipients: List<String>,
    val body: String = "",
    val subject: String? = null,
    val attachments: List<Attachment> = emptyList(),
    /** -1 means "use the configured default subscription". */
    val subscriptionId: Int = -1,
    val replyToMessageId: Long? = null,
    val replyToSnippet: String? = null,
    /** Set when the body is a reaction fallback, so it is not quoted or re-processed. */
    val isReactionFallback: Boolean = false,
)

/** The outcome of a successful send. */
data class SendResult(
    val threadId: Long,
    val localMessageIds: List<Long>,
    val usedMms: Boolean,
)
