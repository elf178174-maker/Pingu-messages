package app.pingu.messages.data.mms.pdu

import java.util.Locale

/**
 * Encodes the PDUs Pingu Messages has to transmit.
 *
 * Outgoing MMS uses `SmsManager.sendMultimediaMessage`, which takes a fully formed M-Send.req: the
 * platform handles the carrier connection and the MMSC URL, the app is responsible for the bytes.
 * Acknowledgements are sent the same way after a successful download, which is what stops a
 * carrier re-notifying about a message that has already been retrieved.
 */
object PduComposer {

    private const val DEFAULT_EXPIRY_SECONDS = 7L * 24 * 60 * 60
    private const val SMIL_CONTENT_ID = "smil"
    private const val SMIL_LOCATION = "smil.xml"

    /**
     * Builds an M-Send.req.
     *
     * @param parts message content; a SMIL presentation part is generated and prepended.
     */
    fun composeSendRequest(
        transactionId: String,
        recipients: List<String>,
        parts: List<MmsPart>,
        subject: String? = null,
        requestDeliveryReport: Boolean = false,
        requestReadReport: Boolean = false,
        expirySeconds: Long = DEFAULT_EXPIRY_SECONDS,
        mmsVersion: Int = PduHeaders.MMS_VERSION_1_2,
    ): ByteArray {
        require(recipients.isNotEmpty()) { "an MMS needs at least one recipient" }

        val namedParts = nameParts(parts)
        val smil = MmsPart(
            contentType = PduContentTypes.APPLICATION_SMIL,
            data = SmilBuilder.build(namedParts).toByteArray(Charsets.UTF_8),
            contentId = SMIL_CONTENT_ID,
            contentLocation = SMIL_LOCATION,
        )
        val allParts = listOf(smil) + namedParts

        val writer = PduWriter()
        writer.writeOctetHeader(PduHeaders.MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_SEND_REQ)
        writer.writeTextHeader(PduHeaders.TRANSACTION_ID, transactionId)
        writer.writeOctet(PduHeaders.MMS_VERSION)
        writer.writeShortInteger(mmsVersion)

        // From: let the MMSC insert the subscriber number. Sending our own would be both a privacy
        // leak on multi-SIM devices and frequently wrong.
        writer.writeOctet(PduHeaders.FROM)
        writer.writeValueLength(1)
        writer.writeOctet(PduHeaders.FROM_INSERT_ADDRESS_TOKEN)

        recipients.forEach { recipient ->
            writer.writeEncodedHeader(PduHeaders.TO, encodeAddress(recipient))
        }

        if (!subject.isNullOrBlank()) {
            writer.writeEncodedHeader(PduHeaders.SUBJECT, subject)
        }

        writer.writeOctetHeader(
            PduHeaders.DELIVERY_REPORT,
            if (requestDeliveryReport) PduHeaders.VALUE_YES else PduHeaders.VALUE_NO,
        )
        writer.writeOctetHeader(
            PduHeaders.READ_REPLY,
            if (requestReadReport) PduHeaders.VALUE_YES else PduHeaders.VALUE_NO,
        )

        // Expiry, relative to now.
        val expiryWriter = PduWriter()
        expiryWriter.writeOctet(RELATIVE_TOKEN)
        expiryWriter.writeLongInteger(expirySeconds)
        val expiryBytes = expiryWriter.toByteArray()
        writer.writeOctet(PduHeaders.EXPIRY)
        writer.writeValueLength(expiryBytes.size.toLong())
        writer.writeBytes(expiryBytes)

        writer.writeOctetHeader(PduHeaders.PRIORITY, PduHeaders.PRIORITY_NORMAL)

        // Content-Type must be the last header; the body follows immediately.
        writer.writeOctet(PduHeaders.CONTENT_TYPE)
        writer.writeContentType(
            PduContentTypes.MULTIPART_RELATED,
            listOf(
                PduParameters.TYPE_MULTIPART to PduContentTypes.APPLICATION_SMIL,
                PduParameters.START_DEPRECATED to "<$SMIL_CONTENT_ID>",
            ),
        )
        writeMultipartBody(writer, allParts)
        return writer.toByteArray()
    }

    /**
     * M-NotifyResp.ind: tells the MMSC what happened to a notification. Sent when the app declines
     * to download a message (for example a blocked sender, or auto-download turned off).
     */
    fun composeNotifyResponse(
        transactionId: String,
        status: Int,
        mmsVersion: Int = PduHeaders.MMS_VERSION_1_2,
    ): ByteArray {
        val writer = PduWriter()
        writer.writeOctetHeader(PduHeaders.MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_NOTIFYRESP_IND)
        writer.writeTextHeader(PduHeaders.TRANSACTION_ID, transactionId)
        writer.writeOctet(PduHeaders.MMS_VERSION)
        writer.writeShortInteger(mmsVersion)
        writer.writeOctetHeader(PduHeaders.STATUS, status)
        writer.writeOctetHeader(PduHeaders.REPORT_ALLOWED, PduHeaders.VALUE_YES)
        return writer.toByteArray()
    }

    /**
     * M-Acknowledge.ind: confirms a successful retrieval so the carrier stops re-notifying.
     */
    fun composeAcknowledge(
        transactionId: String,
        mmsVersion: Int = PduHeaders.MMS_VERSION_1_2,
        reportAllowed: Boolean = true,
    ): ByteArray {
        val writer = PduWriter()
        writer.writeOctetHeader(PduHeaders.MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_ACKNOWLEDGE_IND)
        writer.writeTextHeader(PduHeaders.TRANSACTION_ID, transactionId)
        writer.writeOctet(PduHeaders.MMS_VERSION)
        writer.writeShortInteger(mmsVersion)
        writer.writeOctetHeader(
            PduHeaders.REPORT_ALLOWED,
            if (reportAllowed) PduHeaders.VALUE_YES else PduHeaders.VALUE_NO,
        )
        return writer.toByteArray()
    }

    /**
     * M-Read-Rec.ind: an explicit read report, sent only when the user has turned them on and the
     * sender asked for one.
     */
    fun composeReadReport(
        messageId: String,
        recipient: String,
        readAtSeconds: Long,
        mmsVersion: Int = PduHeaders.MMS_VERSION_1_2,
    ): ByteArray {
        val writer = PduWriter()
        writer.writeOctetHeader(PduHeaders.MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_READ_REC_IND)
        writer.writeOctet(PduHeaders.MMS_VERSION)
        writer.writeShortInteger(mmsVersion)
        writer.writeTextHeader(PduHeaders.MESSAGE_ID, messageId)
        writer.writeEncodedHeader(PduHeaders.TO, encodeAddress(recipient))
        writer.writeOctet(PduHeaders.FROM)
        writer.writeValueLength(1)
        writer.writeOctet(PduHeaders.FROM_INSERT_ADDRESS_TOKEN)
        writer.writeLongIntegerHeader(PduHeaders.DATE, readAtSeconds)
        writer.writeOctetHeader(PduHeaders.READ_STATUS, READ_STATUS_READ)
        return writer.toByteArray()
    }

    /** Gives every part a stable content id and location so SMIL can reference them. */
    private fun nameParts(parts: List<MmsPart>): List<MmsPart> =
        parts.mapIndexed { index, part ->
            if (!part.contentLocation.isNullOrEmpty() && !part.contentId.isNullOrEmpty()) {
                part
            } else {
                val extension = extensionFor(part.contentType)
                val base = sanitize(part.fileName ?: part.name ?: "part$index")
                val location = if (base.contains('.')) base else "$base$extension"
                part.copy(
                    contentId = part.contentId ?: location,
                    contentLocation = part.contentLocation ?: location,
                )
            }
        }

    private fun writeMultipartBody(writer: PduWriter, parts: List<MmsPart>) {
        writer.writeUintvar(parts.size.toLong())
        for (part in parts) {
            val headerWriter = PduWriter()
            val parameters = ArrayList<Pair<Int, Any>>()
            if (part.isText) parameters.add(PduParameters.CHARSET to part.charsetMib)
            part.contentLocation?.let { parameters.add(PduParameters.NAME to it) }
            headerWriter.writeContentType(part.contentType, parameters)

            part.contentId?.let {
                headerWriter.writeOctet(PduPartHeaders.CONTENT_ID or 0x80)
                headerWriter.writeTextString("<$it>")
            }
            part.contentLocation?.let {
                headerWriter.writeOctet(PduPartHeaders.CONTENT_LOCATION or 0x80)
                headerWriter.writeTextString(it)
            }

            val headerBytes = headerWriter.toByteArray()
            writer.writeUintvar(headerBytes.size.toLong())
            writer.writeUintvar(part.data.size.toLong())
            writer.writeBytes(headerBytes)
            writer.writeBytes(part.data)
        }
    }

    /** Carriers expect a phone number to be tagged with its numbering plan. */
    fun encodeAddress(address: String): String {
        val trimmed = address.trim()
        if (trimmed.contains('@') || trimmed.endsWith(PduHeaders.PHONE_NUMBER_SUFFIX)) return trimmed
        return trimmed + PduHeaders.PHONE_NUMBER_SUFFIX
    }

    private fun sanitize(name: String): String =
        name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9._-]"), "_").take(40).ifEmpty { "part" }

    private fun extensionFor(contentType: String): String = when {
        contentType.equals("image/jpeg", true) -> ".jpg"
        contentType.equals("image/png", true) -> ".png"
        contentType.equals("image/gif", true) -> ".gif"
        contentType.equals("image/webp", true) -> ".webp"
        contentType.equals("video/mp4", true) -> ".mp4"
        contentType.equals("video/3gpp", true) -> ".3gp"
        contentType.equals("audio/mpeg", true) -> ".mp3"
        contentType.equals("audio/amr", true) -> ".amr"
        contentType.equals("audio/mp4", true) -> ".m4a"
        contentType.equals("audio/ogg", true) -> ".ogg"
        contentType.equals(PduContentTypes.TEXT_PLAIN, true) -> ".txt"
        contentType.equals("text/x-vcard", true) -> ".vcf"
        contentType.equals("application/pdf", true) -> ".pdf"
        else -> ".dat"
    }

    private const val RELATIVE_TOKEN = 0x81
    private const val READ_STATUS_READ = 0x80
}
