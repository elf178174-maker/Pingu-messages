package app.pingu.messages.core.util

/**
 * Segment accounting for outgoing SMS.
 *
 * GSM 03.38 encodes most Latin text in 7 bits, giving 160 characters per single-part message and
 * 153 per part once a concatenation header is added. A single character outside that alphabet
 * forces the whole message to UCS-2, which drops the limits to 70 and 67. The composer shows this
 * live so a stray curly quote turning one segment into three is never a surprise.
 *
 * The alphabet is spelled with `\\u` escapes so this file stays pure ASCII and cannot be corrupted
 * by a tool guessing the wrong source encoding.
 */
object SmsMessageSize {

    private const val GSM_SINGLE = 160
    private const val GSM_MULTIPART = 153
    private const val UNICODE_SINGLE = 70
    private const val UNICODE_MULTIPART = 67

    /** GSM 03.38 default alphabet: one septet per character. */
    private const val GSM_BASIC_CHARS =
        "@£\$¥èéùìòÇ" +
            "\nØø\rÅå" +
            "Δ_ΦΓΛΩΠΨΣΘΞ" +
            "ÆæßÉ" +
            " !\"#¤%&'()*+,-./0123456789:;<=>?" +
            "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§" +
            "¿abcdefghijklmnopqrstuvwxyzäöñüà"

    /** GSM 03.38 extension table: each of these costs an escape septet plus one. */
    private const val GSM_EXTENDED_CHARS = "\u000C^{}\\[~]|€"

    private val gsmBasic: Set<Char> = GSM_BASIC_CHARS.toSet()
    private val gsmExtended: Set<Char> = GSM_EXTENDED_CHARS.toSet()

    enum class Encoding { GSM_7BIT, UNICODE }

    data class Result(
        val encoding: Encoding,
        /** Encoded units used: septets for GSM, UTF-16 code units for Unicode. */
        val units: Int,
        val segments: Int,
        /** Units still available inside the current segment. */
        val remainingInSegment: Int,
    )

    fun measure(text: String): Result {
        val gsmUnits = countGsmUnits(text)
        return if (gsmUnits != null) {
            build(Encoding.GSM_7BIT, gsmUnits, GSM_SINGLE, GSM_MULTIPART)
        } else {
            build(Encoding.UNICODE, text.length, UNICODE_SINGLE, UNICODE_MULTIPART)
        }
    }

    private fun build(encoding: Encoding, units: Int, single: Int, multi: Int): Result {
        if (units == 0) return Result(encoding, 0, 0, single)
        return if (units <= single) {
            Result(encoding, units, 1, single - units)
        } else {
            val segments = (units + multi - 1) / multi
            Result(encoding, units, segments, segments * multi - units)
        }
    }

    /** Returns the septet count, or null when the text needs UCS-2. */
    private fun countGsmUnits(text: String): Int? {
        var units = 0
        for (character in text) {
            when {
                gsmBasic.contains(character) -> units += 1
                gsmExtended.contains(character) -> units += 2
                else -> return null
            }
        }
        return units
    }
}
