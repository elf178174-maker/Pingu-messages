package app.pingu.messages.data.mms.pdu

/**
 * Cursor over a PDU with the WSP primitive types from WAP-230-WSP section 8.4.2.1.
 *
 * Every read advances the position; a malformed field throws [PduFormatException] rather than
 * returning a plausible-looking wrong value, because silently mis-parsing a carrier PDU is how
 * messages end up attributed to the wrong sender.
 */
internal class PduReader(private val data: ByteArray, startPosition: Int = 0) {

    var position: Int = startPosition
        private set

    val size: Int get() = data.size

    fun hasMore(): Boolean = position < data.size

    fun remaining(): Int = data.size - position

    fun peek(): Int {
        require(position)
        return data[position].toInt() and 0xFF
    }

    fun readOctet(): Int {
        require(position)
        return data[position++].toInt() and 0xFF
    }

    fun skip(count: Int) {
        if (count <= 0) return
        require(position + count - 1)
        position += count
    }

    fun seek(newPosition: Int) {
        if (newPosition < 0 || newPosition > data.size) {
            throw PduFormatException("seek out of range: $newPosition")
        }
        position = newPosition
    }

    fun readBytes(count: Int): ByteArray {
        if (count < 0 || position + count > data.size) {
            throw PduFormatException("read of $count bytes past end at $position")
        }
        val result = data.copyOfRange(position, position + count)
        position += count
        return result
    }

    /** Variable length unsigned integer: seven bits per octet, high bit marks continuation. */
    fun readUintvar(): Long {
        var result = 0L
        var octets = 0
        while (true) {
            val octet = readOctet()
            result = (result shl 7) or (octet and 0x7F).toLong()
            octets++
            if (octet and 0x80 == 0) break
            if (octets > 5) throw PduFormatException("uintvar longer than five octets")
        }
        return result
    }

    /** Short-integer: a single octet with the high bit set. */
    fun readShortInteger(): Int {
        val octet = readOctet()
        if (octet and 0x80 == 0) throw PduFormatException("not a short integer: $octet")
        return octet and 0x7F
    }

    /** Long-integer: a length octet followed by that many big-endian value octets. */
    fun readLongInteger(): Long {
        val length = readOctet()
        if (length > 30) throw PduFormatException("long integer length $length exceeds 30")
        var value = 0L
        repeat(length) { value = (value shl 8) or readOctet().toLong() }
        return value
    }

    /** Integer-value: either form. */
    fun readIntegerValue(): Long {
        val first = peek()
        return if (first and 0x80 != 0) readShortInteger().toLong() else readLongInteger()
    }

    /**
     * Value-length: a short length (0..30) or the quote octet 31 followed by a uintvar.
     */
    fun readValueLength(): Long {
        val first = readOctet()
        return when {
            first <= 30 -> first.toLong()
            first == LENGTH_QUOTE -> readUintvar()
            else -> throw PduFormatException("invalid value-length octet $first")
        }
    }

    /**
     * Text-string: an optional quote octet, then bytes up to a terminating NUL. Returns the raw
     * bytes so the caller can apply the right character set.
     */
    fun readTextStringBytes(): ByteArray {
        if (hasMore() && (peek() == QUOTE || peek() == DOUBLE_QUOTE)) {
            position++
        }
        val start = position
        while (hasMore() && data[position].toInt() != 0) {
            position++
        }
        val end = position
        if (hasMore()) position++ // consume the terminator
        return data.copyOfRange(start, end)
    }

    fun readTextString(): String = String(readTextStringBytes(), Charsets.UTF_8)

    /**
     * Encoded-string-value: either a plain text string, or a length-prefixed pair of character set
     * and text. Carriers send subjects and sender names in both forms.
     */
    fun readEncodedText(): EncodedText {
        val first = peek()
        if (first <= LENGTH_QUOTE) {
            // Length-prefixed form: value-length, character set, then the text.
            val startAfterLength: Int
            val length = readValueLength()
            startAfterLength = position
            val charsetMib = if (hasMore() && (peek() and 0x80 != 0 || peek() <= 30)) {
                readIntegerValue().toInt()
            } else {
                PduCharsets.UTF_8
            }
            val consumed = position - startAfterLength
            val textLength = (length - consumed).toInt().coerceAtLeast(0)
            val bytes = readBytesUntilNulOrLimit(textLength)
            return EncodedText(bytes, charsetMib)
        }
        return EncodedText(readTextStringBytes(), PduCharsets.UTF_8)
    }

    private fun readBytesUntilNulOrLimit(limit: Int): ByteArray {
        val end = (position + limit).coerceAtMost(data.size)
        var cursor = position
        while (cursor < end && data[cursor].toInt() != 0) cursor++
        val bytes = data.copyOfRange(position, cursor)
        position = end
        return bytes
    }

    /**
     * Content-type-value, in either the constrained (single token or string) or the general
     * (length-prefixed, with parameters) form.
     */
    fun readContentType(): ContentTypeValue {
        val first = peek()
        return when {
            first <= LENGTH_QUOTE -> {
                // Content-general-form: value-length, media, parameters.
                val length = readValueLength().toInt()
                val end = position + length
                val type = readMediaType(end)
                val parameters = readParameters(end)
                seek(end.coerceAtMost(data.size))
                ContentTypeValue(type, parameters)
            }

            first and 0x80 != 0 -> {
                val wellKnown = readShortInteger()
                ContentTypeValue(PduContentTypes.nameOf(wellKnown) ?: "application/octet-stream")
            }

            else -> ContentTypeValue(readTextString())
        }
    }

    private fun readMediaType(end: Int): String {
        if (position >= end) return "application/octet-stream"
        val first = peek()
        return if (first and 0x80 != 0) {
            val wellKnown = readShortInteger()
            PduContentTypes.nameOf(wellKnown) ?: "application/octet-stream"
        } else {
            readTextString()
        }
    }

    /** Reads typed and untyped parameters until [end]. Unknown parameters are skipped safely. */
    fun readParameters(end: Int): Map<Int, Any> {
        val parameters = LinkedHashMap<Int, Any>()
        while (position < end && position < data.size) {
            val before = position
            val token = readOctet()
            if (token and 0x80 != 0) {
                val parameterId = token and 0x7F
                when (parameterId) {
                    PduParameters.CHARSET -> {
                        val value = if (peek() == 0x00) {
                            readOctet(); PduCharsets.ANY_CHARSET
                        } else {
                            readIntegerValue().toInt()
                        }
                        parameters[parameterId] = value
                    }

                    PduParameters.TYPE, PduParameters.TYPE_MULTIPART ->
                        parameters[parameterId] = readTextString()

                    PduParameters.NAME, PduParameters.NAME_DEPRECATED,
                    PduParameters.FILENAME, PduParameters.FILENAME_DEPRECATED,
                    PduParameters.START, PduParameters.START_DEPRECATED,
                    PduParameters.START_INFO, PduParameters.START_INFO_DEPRECATED,
                    PduParameters.COMMENT, PduParameters.DOMAIN, PduParameters.PATH,
                    -> parameters[parameterId] = readEncodedText().text

                    PduParameters.SIZE, PduParameters.PADDING, PduParameters.LEVEL,
                    PduParameters.CREATION_DATE, PduParameters.MODIFICATION_DATE,
                    PduParameters.READ_DATE,
                    -> parameters[parameterId] = readIntegerValue().toInt()

                    else -> skipUnknownParameterValue(end)
                }
            } else {
                // Untyped parameter: token text followed by an untyped value.
                seek(before)
                readTextString()
                if (position < end) skipUnknownParameterValue(end)
            }
            if (position <= before) {
                // No progress: bail out rather than loop forever on a malformed PDU.
                seek(end.coerceAtMost(data.size))
                break
            }
        }
        return parameters
    }

    private fun skipUnknownParameterValue(end: Int) {
        if (position >= end) return
        val first = peek()
        when {
            first and 0x80 != 0 -> position++
            first <= 30 -> {
                val length = readValueLength().toInt()
                position = (position + length).coerceAtMost(end)
            }

            else -> readTextString()
        }
    }

    private fun require(index: Int) {
        if (index < 0 || index >= data.size) {
            throw PduFormatException("read past end of PDU at $index of ${data.size}")
        }
    }

    companion object {
        const val LENGTH_QUOTE = 31
        const val QUOTE = 0x7F
        const val DOUBLE_QUOTE = 0x22
    }
}

/** Thrown when a PDU does not follow the encoding rules. */
class PduFormatException(message: String) : Exception(message)
