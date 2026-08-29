package app.pingu.messages.data.telephony

import android.provider.Telephony
import app.pingu.messages.data.local.entity.AttachmentEntity
import app.pingu.messages.data.local.entity.MessageEntity
import app.pingu.messages.data.mms.pdu.PduHeaders
import app.pingu.messages.domain.model.MessageStatus
import app.pingu.messages.domain.model.MessageTransport

/**
 * Translates telephony provider rows into the app's mirror entities.
 *
 * The interesting part is the status mapping: the provider spreads what a person thinks of as one
 * "state" across the message box, the `status` column and, for MMS, the message type and retrieve
 * status. Getting this wrong is what makes other SMS apps show "sent" for a message that failed.
 */
object TelephonyMapper {

    const val TRANSPORT_SMS = "SMS"
    const val TRANSPORT_MMS = "MMS"

    fun statusOf(row: SmsRow): MessageStatus = when (row.type) {
        Telephony.Sms.MESSAGE_TYPE_INBOX -> MessageStatus.RECEIVED
        Telephony.Sms.MESSAGE_TYPE_DRAFT -> MessageStatus.DRAFT
        Telephony.Sms.MESSAGE_TYPE_OUTBOX, Telephony.Sms.MESSAGE_TYPE_QUEUED -> MessageStatus.SENDING
        Telephony.Sms.MESSAGE_TYPE_FAILED -> MessageStatus.FAILED
        Telephony.Sms.MESSAGE_TYPE_SENT -> when (row.status) {
            Telephony.Sms.STATUS_COMPLETE -> MessageStatus.DELIVERED
            Telephony.Sms.STATUS_FAILED -> MessageStatus.FAILED
            else -> MessageStatus.SENT
        }

        else -> if (row.isOutgoing) MessageStatus.SENT else MessageStatus.RECEIVED
    }

    fun statusOf(row: MmsRow): MessageStatus = when (row.messageBox) {
        Telephony.Mms.MESSAGE_BOX_INBOX ->
            if (row.messageType == PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND) {
                when {
                    row.retrieveStatus == 0 -> MessageStatus.PENDING_DOWNLOAD
                    row.retrieveStatus == RETRIEVE_STATUS_EXPIRED -> MessageStatus.EXPIRED
                    else -> MessageStatus.DOWNLOAD_FAILED
                }
            } else {
                MessageStatus.RECEIVED
            }

        Telephony.Mms.MESSAGE_BOX_DRAFTS -> MessageStatus.DRAFT
        Telephony.Mms.MESSAGE_BOX_OUTBOX -> MessageStatus.SENDING
        Telephony.Mms.MESSAGE_BOX_FAILED -> MessageStatus.FAILED
        Telephony.Mms.MESSAGE_BOX_SENT -> when (row.status) {
            PduHeaders.STATUS_RETRIEVED -> MessageStatus.DELIVERED
            PduHeaders.STATUS_REJECTED, PduHeaders.STATUS_EXPIRED -> MessageStatus.FAILED
            else -> MessageStatus.SENT
        }

        else -> if (row.isOutgoing) MessageStatus.SENT else MessageStatus.RECEIVED
    }

    fun toEntity(row: SmsRow): MessageEntity = MessageEntity(
        threadId = row.threadId,
        transport = TRANSPORT_SMS,
        systemId = row.id,
        address = row.address,
        body = row.body,
        subject = row.subject,
        timestamp = row.dateMillis,
        sentTimestamp = row.dateSentMillis,
        isOutgoing = row.isOutgoing,
        isRead = row.read,
        status = statusOf(row).name,
        errorCode = row.errorCode,
        subscriptionId = row.subscriptionId,
        sizeBytes = 0L,
        hasAttachments = false,
    )

    fun toEntity(
        row: MmsRow,
        senderAddress: String?,
        bodyText: String?,
        hasAttachments: Boolean,
    ): MessageEntity = MessageEntity(
        threadId = row.threadId,
        transport = TRANSPORT_MMS,
        systemId = row.id,
        address = senderAddress,
        body = bodyText,
        subject = row.subject,
        timestamp = row.dateMillis,
        sentTimestamp = row.dateSentMillis,
        isOutgoing = row.isOutgoing,
        isRead = row.read,
        status = statusOf(row).name,
        errorCode = 0,
        subscriptionId = row.subscriptionId,
        sizeBytes = row.messageSize,
        contentLocation = row.contentLocation,
        transactionId = row.transactionId,
        expiryTimestamp = row.expirySeconds * 1000L,
        hasAttachments = hasAttachments,
    )

    /** MMS parts that carry content, i.e. everything except the SMIL layout and the body text. */
    fun attachmentParts(parts: List<MmsPartRow>): List<MmsPartRow> =
        parts.filterNot { it.isSmil || it.isText }

    fun bodyTextOf(parts: List<MmsPartRow>): String =
        parts.filter { it.isText && !it.isSmil }
            .mapNotNull { it.text }
            .joinToString("\n")
            .trim()

    fun toAttachmentEntity(messageId: Long, part: MmsPartRow): AttachmentEntity = AttachmentEntity(
        messageId = messageId,
        uri = part.uri.toString(),
        mimeType = part.contentType,
        fileName = part.fileName ?: part.name ?: part.contentLocation,
    )

    val transportOf: (MessageTransport) -> String = { transport ->
        when (transport) {
            MessageTransport.SMS -> TRANSPORT_SMS
            MessageTransport.MMS -> TRANSPORT_MMS
        }
    }

    fun transportFrom(value: String): MessageTransport =
        if (value == TRANSPORT_MMS) MessageTransport.MMS else MessageTransport.SMS

    /** `RETRIEVE_STATUS` value the provider uses for a message the carrier already expired. */
    private const val RETRIEVE_STATUS_EXPIRED = 0x80
}
