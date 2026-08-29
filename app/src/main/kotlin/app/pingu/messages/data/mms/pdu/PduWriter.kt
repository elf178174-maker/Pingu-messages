package app.pingu.messages.data.mms.pdu

import java.io.ByteArrayOutputStream

/** Writes the WSP primitive types. The mirror image of [PduReader]. */
internal class PduWriter {

    private val out = ByteArrayOutputStream()

    val size: Int get() = out.size()

    fun toByteArray(): ByteArray = out.toByteArray()

    fun writeOctet(value: Int) {
        out.write(value and 0xFF)
    }

    fun writeBytes(bytes: ByteArray) {
        out.write(bytes, 0, bytes.size)
    }

    fun writeUintvar(value: Long) {
        var remaining = value
        var octets = 0
        val buffer = LongArray(5)
        do {
            buffer[octets++] = remaining and 0x7F
            remaining = remaining ushr 7
        } while (remaining != 0L && octets < buffer.size)
        for (index in octets - 1 downTo 0) {
            val continuation = if (index == 0) 0L else 0x80L
            writeOctet((buffer[index] or continuation).toInt())
        }
    }

    fun writeShortInteger(value: Int) {
        writeOctet(value or 0x80)
    }

    fun writeLongInteger(value: Long) {
        val bytes = ArrayList<Int>(8)
        var remaining = value
        if (remaining == 0L) {
            bytes.add(0)
        } else {
            while (remaining > 0) {
                bytes.add((remaining and 0xFF).toInt())
                remaining = remaining ushr 8
            }
        }
        writeOctet(bytes.size)
        for (index in bytes.indices.reversed()) writeOctet(bytes[index])
    }

    fun writeIntegerValue(value: Long) {
        if (value in 0..127) writeShortInteger(value.toInt()) else writeLongInteger(value)
    }

    fun writeValueLength(length: Long) {
        if (length <= 30) {
            writeOctet(length.toInt())
        } else {
            writeOctet(PduReader.LENGTH_QUOTE)
            writeUintvar(length)
        }
    }

    /** Text-string, quoting when the first byte would otherwise look like a token. */
    fun writeTextString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        if (bytes.isNotEmpty() && (bytes[0].toInt() and 0xFF) >= 0x80) {
            writeOctet(PduReader.QUOTE)
        }
        writeBytes(bytes)
        writeOctet(0)
    }

    /** Encoded-string-value in the length-prefixed form, which is always safe to emit. */
    fun writeEncodedString(value: String, charsetMib: Int = PduCharsets.UTF_8) {
        val textBytes = value.toByteArray(PduCharsets.charsetFor(charsetMib))
        val charsetWriter = PduWriter().apply { writeIntegerValue(charsetMib.toLong()) }
        val charsetBytes = charsetWriter.toByteArray()
        writeValueLength((charsetBytes.size + textBytes.size + 1).toLong())
        writeBytes(charsetBytes)
        writeBytes(textBytes)
        writeOctet(0)
    }

    /**
     * Content-type-value. Well-known types are written as a single token where possible, which is
     * what carriers expect and what keeps the PDU small.
     */
    fun writeContentType(type: String, parameters: List<Pair<Int, Any>> = emptyList()) {
        if (parameters.isEmpty()) {
            val wellKnown = PduContentTypes.wellKnownValueOf(type)
            if (wellKnown != null) writeShortInteger(wellKnown) else writeTextString(type)
            return
        }
        val payload = PduWriter()
        val wellKnown = PduContentTypes.wellKnownValueOf(type)
        if (wellKnown != null) payload.writeShortInteger(wellKnown) else payload.writeTextString(type)
        for ((token, value) in parameters) {
            payload.writeShortInteger(token)
            when (value) {
                is Int -> payload.writeIntegerValue(value.toLong())
                is Long -> payload.writeIntegerValue(value)
                is String -> payload.writeTextString(value)
                else -> payload.writeTextString(value.toString())
            }
        }
        val bytes = payload.toByteArray()
        writeValueLength(bytes.size.toLong())
        writeBytes(bytes)
    }

    /** Header field id followed by a single octet value. */
    fun writeOctetHeader(field: Int, value: Int) {
        writeOctet(field)
        writeOctet(value)
    }

    fun writeTextHeader(field: Int, value: String) {
        writeOctet(field)
        writeTextString(value)
    }

    fun writeEncodedHeader(field: Int, value: String, charsetMib: Int = PduCharsets.UTF_8) {
        writeOctet(field)
        writeEncodedString(value, charsetMib)
    }

    fun writeLongIntegerHeader(field: Int, value: Long) {
        writeOctet(field)
        writeLongInteger(value)
    }
}
