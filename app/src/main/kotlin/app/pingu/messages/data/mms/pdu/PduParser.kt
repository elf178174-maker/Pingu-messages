package app.pingu.messages.data.mms.pdu

/**
 * Decodes an MMS PDU.
 *
 * Two shapes matter to the app:
 *
 *  * **M-Notification.ind**, delivered by the carrier as a WAP push. It carries no content, only
 *    the sender, the size and the URL to fetch the message from.
 *  * **M-Retrieve.conf**, the message itself, downloaded from that URL by the platform on the
 *    app's behalf. Its multipart body holds the text and the media.
 *
 * Delivery and read reports (M-Delivery.ind, M-Read-Orig.ind) are also decoded so status can be
 * reported truthfully rather than guessed.
 */
object PduParser {

    /** Parses a complete PDU, returning null when the bytes are not a PDU this app understands. */
    fun parse(data: ByteArray): DecodedPdu? = try {
        parseOrThrow(data)
    } catch (error: PduFormatException) {
        null
    } catch (error: IndexOutOfBoundsException) {
        null
    }

    fun parseOrThrow(data: ByteArray): DecodedPdu {
        val reader = PduReader(data)
        var messageType = -1
        var transactionId: String? = null
        var messageId: String? = null
        var mmsVersion = PduHeaders.MMS_VERSION_1_2
        var from: String? = null
        val to = ArrayList<String>()
        val cc = ArrayList<String>()
        val bcc = ArrayList<String>()
        var subject: String? = null
        var subjectCharset = PduCharsets.UTF_8
        var date = 0L
        var contentLocation: String? = null
        var contentType: ContentTypeValue? = null
        var messageSize = 0L
        var expiry = 0L
        var deliveryReport: Int? = null
        var readReport: Int? = null
        var status: Int? = null
        var retrieveStatus: Int? = null
        var responseStatus: Int? = null
        var messageClass: String? = null
        var priority: Int? = null
        var parts: List<MmsPart> = emptyList()

        while (reader.hasMore()) {
            val field = reader.readOctet()
            if (field and 0x80 == 0) {
                // An application-specific header written as text; skip name and value.
                reader.seek(reader.position - 1)
                reader.readTextString()
                skipValue(reader)
                continue
            }

            when (field) {
                PduHeaders.MESSAGE_TYPE -> messageType = reader.readOctet()
                PduHeaders.TRANSACTION_ID -> transactionId = reader.readTextString()
                PduHeaders.MESSAGE_ID -> messageId = reader.readTextString()
                PduHeaders.MMS_VERSION -> mmsVersion = reader.readOctet() and 0x7F
                PduHeaders.FROM -> from = readFrom(reader)
                PduHeaders.TO -> to += reader.readEncodedText().text
                PduHeaders.CC -> cc += reader.readEncodedText().text
                PduHeaders.BCC -> bcc += reader.readEncodedText().text
                PduHeaders.SUBJECT -> {
                    val encoded = reader.readEncodedText()
                    subject = encoded.text
                    subjectCharset = encoded.charsetMib
                }

                PduHeaders.DATE -> date = reader.readLongInteger()
                PduHeaders.CONTENT_LOCATION -> contentLocation = reader.readTextString()
                PduHeaders.MESSAGE_SIZE -> messageSize = reader.readLongInteger()
                PduHeaders.EXPIRY -> expiry = readTimeValue(reader)
                PduHeaders.DELIVERY_TIME -> readTimeValue(reader)
                PduHeaders.DELIVERY_REPORT -> deliveryReport = reader.readOctet()
                PduHeaders.READ_REPLY -> readReport = reader.readOctet()
                PduHeaders.REPORT_ALLOWED -> reader.readOctet()
                PduHeaders.SENDER_VISIBILITY -> reader.readOctet()
                PduHeaders.READ_STATUS -> status = reader.readOctet()
                PduHeaders.STATUS -> status = reader.readOctet()
                PduHeaders.RETRIEVE_STATUS -> retrieveStatus = reader.readOctet()
                PduHeaders.RESPONSE_STATUS -> responseStatus = reader.readOctet()
                PduHeaders.RESPONSE_TEXT -> reader.readEncodedText()
                PduHeaders.RETRIEVE_TEXT -> reader.readEncodedText()
                PduHeaders.PRIORITY -> priority = reader.readOctet()
                PduHeaders.REPLY_CHARGING -> reader.readOctet()
                PduHeaders.MESSAGE_REPORT -> reader.readOctet()
                PduHeaders.CONTENT_CLASS -> reader.readOctet()
                PduHeaders.MESSAGE_CLASS -> messageClass = readMessageClass(reader)

                PduHeaders.CONTENT_TYPE -> {
                    contentType = reader.readContentType()
                    parts = parseBody(reader, contentType)
                    // Content-Type is the final header; the body follows it.
                    break
                }

                else -> skipValue(reader)
            }
        }

        if (messageType == -1) throw PduFormatException("PDU has no message type")

        return DecodedPdu(
            messageType = messageType,
            transactionId = transactionId,
            messageId = messageId,
            mmsVersion = mmsVersion,
            from = from,
            to = to,
            cc = cc,
            bcc = bcc,
            subject = subject,
            subjectCharsetMib = subjectCharset,
            date = date,
            contentLocation = contentLocation,
            contentType = contentType,
            messageSize = messageSize,
            expiry = expiry,
            deliveryReport = deliveryReport,
            readReport = readReport,
            status = status,
            retrieveStatus = retrieveStatus,
            responseStatus = responseStatus,
            messageClass = messageClass,
            priority = priority,
            parts = parts,
        )
    }

    /**
     * From-value: a length, then either an address-present token followed by the address, or an
     * insert-address token meaning "the MMSC knows my number".
     */
    private fun readFrom(reader: PduReader): String? {
        val length = reader.readValueLength().toInt()
        val end = reader.position + length
        if (reader.position >= end) return null
        return when (reader.readOctet()) {
            PduHeaders.FROM_ADDRESS_PRESENT_TOKEN -> {
                val address = reader.readEncodedText().text
                reader.seek(end.coerceAtMost(reader.size))
                stripAddressType(address)
            }

            else -> {
                reader.seek(end.coerceAtMost(reader.size))
                null
            }
        }
    }

    /** Expiry and delivery time: a length, an absolute/relative token, then a long integer. */
    private fun readTimeValue(reader: PduReader): Long {
        val length = reader.readValueLength().toInt()
        val end = reader.position + length
        if (reader.position >= end) return 0L
        val token = reader.readOctet()
        val value = reader.readLongInteger()
        reader.seek(end.coerceAtMost(reader.size))
        return when (token) {
            ABSOLUTE_TOKEN -> value
            RELATIVE_TOKEN -> System.currentTimeMillis() / 1000L + value
            else -> value
        }
    }

    private fun readMessageClass(reader: PduReader): String {
        val first = reader.peek()
        return if (first and 0x80 != 0) {
            when (reader.readOctet()) {
                0x80 -> "personal"
                0x81 -> "advertisement"
                0x82 -> "informational"
                0x83 -> "auto"
                else -> "personal"
            }
        } else {
            reader.readTextString()
        }
    }

    /**
     * Multipart body: an entry count, then for each entry a header length, a data length, the
     * content type, the entry headers and finally the data.
     */
    private fun parseBody(reader: PduReader, contentType: ContentTypeValue?): List<MmsPart> {
        if (!reader.hasMore()) return emptyList()
        val type = contentType?.type.orEmpty()
        val isMultipart = type.contains("multipart", ignoreCase = true) ||
            type.equals(PduContentTypes.MULTIPART_RELATED, ignoreCase = true)

        if (!isMultipart) {
            val data = reader.readBytes(reader.remaining())
            return listOf(
                MmsPart(
                    contentType = type.ifEmpty { "application/octet-stream" },
                    data = data,
                    charsetMib = contentType?.charsetMib ?: PduCharsets.UTF_8,
                ),
            )
        }

        val entryCount = reader.readUintvar().toInt()
        if (entryCount <= 0 || entryCount > MAX_PARTS) return emptyList()

        val parts = ArrayList<MmsPart>(entryCount)
        repeat(entryCount) {
            if (!reader.hasMore()) return parts
            val headerLength = reader.readUintvar().toInt()
            val dataLength = reader.readUintvar().toInt()
            val headerStart = reader.position
            val headerEnd = headerStart + headerLength

            val partContentType = reader.readContentType()
            var contentId: String? = null
            var partContentLocation: String? = null
            var fileName: String? = null

            while (reader.position < headerEnd && reader.hasMore()) {
                val before = reader.position
                val field = reader.readOctet()
                when (field and 0x7F) {
                    PduPartHeaders.CONTENT_ID -> contentId = trimAngleBrackets(reader.readTextString())
                    PduPartHeaders.CONTENT_LOCATION -> partContentLocation = reader.readTextString()
                    PduPartHeaders.CONTENT_DISPOSITION,
                    PduPartHeaders.CONTENT_DISPOSITION_V1_4,
                    -> fileName = readContentDisposition(reader, headerEnd)

                    else -> skipValue(reader)
                }
                if (reader.position <= before) break
            }
            reader.seek(headerEnd.coerceAtMost(reader.size))

            val data = reader.readBytes(dataLength.coerceAtMost(reader.remaining()))
            parts.add(
                MmsPart(
                    contentType = partContentType.type,
                    data = data,
                    name = partContentType.name,
                    fileName = fileName ?: partContentType.name,
                    contentId = contentId,
                    contentLocation = partContentLocation,
                    charsetMib = partContentType.charsetMib ?: PduCharsets.UTF_8,
                ),
            )
        }
        return parts
    }

    /** Content-disposition carries the suggested filename for file attachments. */
    private fun readContentDisposition(reader: PduReader, headerEnd: Int): String? {
        val length = reader.readValueLength().toInt()
        val end = (reader.position + length).coerceAtMost(headerEnd)
        if (reader.position < end) reader.readOctet() // disposition token
        val parameters = reader.readParameters(end)
        reader.seek(end.coerceAtMost(reader.size))
        return (parameters[PduParameters.FILENAME] as? String)
            ?: (parameters[PduParameters.FILENAME_DEPRECATED] as? String)
            ?: (parameters[PduParameters.NAME] as? String)
    }

    /**
     * Best-effort skip of a header value whose type this parser does not model, so one unknown
     * field cannot derail the rest of the PDU.
     */
    private fun skipValue(reader: PduReader) {
        if (!reader.hasMore()) return
        val first = reader.peek()
        when {
            first and 0x80 != 0 -> reader.skip(1)
            first <= PduReader.LENGTH_QUOTE -> {
                val length = reader.readValueLength().toInt()
                reader.skip(length.coerceAtMost(reader.remaining()))
            }

            else -> reader.readTextString()
        }
    }

    /** Carriers append "/TYPE=PLMN" to phone numbers inside PDUs. */
    fun stripAddressType(address: String?): String? {
        if (address == null) return null
        val index = address.indexOf('/')
        return if (index > 0) address.substring(0, index) else address
    }

    private fun trimAngleBrackets(value: String): String =
        value.removePrefix("<").removeSuffix(">")

    private const val ABSOLUTE_TOKEN = 0x80
    private const val RELATIVE_TOKEN = 0x81

    /** Sanity bound; no legitimate MMS has thousands of parts. */
    private const val MAX_PARTS = 512
}
