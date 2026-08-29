package app.pingu.messages.data.mms.pdu

/** A text value together with the character set it was transmitted in. */
data class EncodedText(val bytes: ByteArray, val charsetMib: Int = PduCharsets.UTF_8) {

    val text: String get() = PduCharsets.decode(bytes, charsetMib)

    override fun equals(other: Any?): Boolean =
        other is EncodedText && charsetMib == other.charsetMib && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + charsetMib

    override fun toString(): String = text

    companion object {
        fun of(value: String, charsetMib: Int = PduCharsets.UTF_8): EncodedText =
            EncodedText(value.toByteArray(PduCharsets.charsetFor(charsetMib)), charsetMib)
    }
}

/** A WSP content type: the MIME type plus its typed parameters. */
data class ContentTypeValue(
    val type: String,
    val parameters: Map<Int, Any> = emptyMap(),
) {
    val charsetMib: Int? get() = parameters[PduParameters.CHARSET] as? Int
    val name: String? get() = parameters[PduParameters.NAME] as? String
        ?: parameters[PduParameters.NAME_DEPRECATED] as? String
    val start: String? get() = parameters[PduParameters.START] as? String
        ?: parameters[PduParameters.START_DEPRECATED] as? String
}

/** Well-known WSP parameter tokens used inside content types and content dispositions. */
object PduParameters {
    const val Q = 0x00
    const val CHARSET = 0x01
    const val LEVEL = 0x02
    const val TYPE = 0x03
    const val NAME_DEPRECATED = 0x05
    const val FILENAME_DEPRECATED = 0x06
    const val DIFFERENCES = 0x07
    const val PADDING = 0x08
    const val TYPE_MULTIPART = 0x09
    const val START_DEPRECATED = 0x0A
    const val START_INFO_DEPRECATED = 0x0B
    const val COMMENT = 0x0C
    const val DOMAIN = 0x0D
    const val MAX_AGE = 0x0E
    const val PATH = 0x0F
    const val SECURE = 0x10
    const val SEC = 0x11
    const val MAC = 0x12
    const val CREATION_DATE = 0x13
    const val MODIFICATION_DATE = 0x14
    const val READ_DATE = 0x15
    const val SIZE = 0x16
    const val NAME = 0x17
    const val FILENAME = 0x18
    const val START = 0x19
    const val START_INFO = 0x1A
}

/** Well-known field names that may appear in the header block of a multipart entry. */
object PduPartHeaders {
    const val CONTENT_LOCATION = 0x0E
    const val CONTENT_ID = 0x40
    const val CONTENT_DISPOSITION = 0x2E
    const val CONTENT_DISPOSITION_V1_4 = 0x45
}

/**
 * One entry of an MMS multipart body: a media item, a text run, or the SMIL presentation part.
 */
data class MmsPart(
    val contentType: String,
    val data: ByteArray,
    val name: String? = null,
    val fileName: String? = null,
    val contentId: String? = null,
    val contentLocation: String? = null,
    val charsetMib: Int = PduCharsets.UTF_8,
) {
    val isText: Boolean get() = contentType.startsWith("text/", ignoreCase = true)

    val isSmil: Boolean
        get() = contentType.equals(PduContentTypes.APPLICATION_SMIL, ignoreCase = true)

    /** Decoded text for text parts; empty for binary ones. */
    val text: String get() = if (isText) PduCharsets.decode(data, charsetMib) else ""

    /** The best available display name for the part. */
    val displayName: String?
        get() = fileName ?: name ?: contentLocation ?: contentId

    override fun equals(other: Any?): Boolean = other is MmsPart &&
        contentType == other.contentType &&
        data.contentEquals(other.data) &&
        name == other.name &&
        fileName == other.fileName &&
        contentId == other.contentId &&
        contentLocation == other.contentLocation &&
        charsetMib == other.charsetMib

    override fun hashCode(): Int {
        var result = contentType.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + (fileName?.hashCode() ?: 0)
        result = 31 * result + (contentId?.hashCode() ?: 0)
        result = 31 * result + (contentLocation?.hashCode() ?: 0)
        result = 31 * result + charsetMib
        return result
    }
}

/**
 * A decoded MMS PDU.
 *
 * One class covers every message type the app has to understand, because the header set overlaps
 * almost entirely and a sealed hierarchy would only add casting at every call site. [messageType]
 * says which fields are meaningful; the accessors below name the useful combinations.
 */
data class DecodedPdu(
    val messageType: Int,
    val transactionId: String? = null,
    val messageId: String? = null,
    val mmsVersion: Int = PduHeaders.MMS_VERSION_1_2,
    val from: String? = null,
    val to: List<String> = emptyList(),
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String? = null,
    val subjectCharsetMib: Int = PduCharsets.UTF_8,
    /** Seconds since the epoch, as MMS transmits it. */
    val date: Long = 0L,
    val contentLocation: String? = null,
    val contentType: ContentTypeValue? = null,
    val messageSize: Long = 0L,
    /** Seconds since the epoch after which the MMSC discards an undelivered message. */
    val expiry: Long = 0L,
    val deliveryReport: Int? = null,
    val readReport: Int? = null,
    val status: Int? = null,
    val retrieveStatus: Int? = null,
    val responseStatus: Int? = null,
    val messageClass: String? = null,
    val priority: Int? = null,
    val parts: List<MmsPart> = emptyList(),
) {
    val isNotification: Boolean get() = messageType == PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND
    val isRetrieveConf: Boolean get() = messageType == PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF
    val isDeliveryInd: Boolean get() = messageType == PduHeaders.MESSAGE_TYPE_DELIVERY_IND
    val isReadOrigInd: Boolean get() = messageType == PduHeaders.MESSAGE_TYPE_READ_ORIG_IND
    val isSendConf: Boolean get() = messageType == PduHeaders.MESSAGE_TYPE_SEND_CONF

    /** Parts excluding the SMIL presentation, which is layout rather than content. */
    val contentParts: List<MmsPart> get() = parts.filterNot { it.isSmil }

    /** All text of the message joined in part order. */
    val bodyText: String
        get() = contentParts.filter { it.isText }.joinToString("\n") { it.text }.trim()
}
