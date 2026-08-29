package app.pingu.messages.data.mms.pdu

import java.nio.charset.Charset

/**
 * IANA MIBenum values used by WSP to identify a text encoding, and the mapping to the JVM charsets
 * we can actually decode with.
 */
object PduCharsets {

    const val ANY_CHARSET = 0x00
    const val US_ASCII = 0x03
    const val ISO_8859_1 = 0x04
    const val ISO_8859_2 = 0x05
    const val ISO_8859_3 = 0x06
    const val ISO_8859_4 = 0x07
    const val ISO_8859_5 = 0x08
    const val ISO_8859_6 = 0x09
    const val ISO_8859_7 = 0x0A
    const val ISO_8859_8 = 0x0B
    const val ISO_8859_9 = 0x0C
    const val SHIFT_JIS = 0x11
    const val UTF_8 = 0x6A
    const val BIG5 = 0x07EA
    const val UCS2 = 0x03E8
    const val UTF_16 = 0x03F7

    private val mibToName = mapOf(
        US_ASCII to "US-ASCII",
        ISO_8859_1 to "ISO-8859-1",
        ISO_8859_2 to "ISO-8859-2",
        ISO_8859_3 to "ISO-8859-3",
        ISO_8859_4 to "ISO-8859-4",
        ISO_8859_5 to "ISO-8859-5",
        ISO_8859_6 to "ISO-8859-6",
        ISO_8859_7 to "ISO-8859-7",
        ISO_8859_8 to "ISO-8859-8",
        ISO_8859_9 to "ISO-8859-9",
        SHIFT_JIS to "Shift_JIS",
        UTF_8 to "UTF-8",
        BIG5 to "Big5",
        UCS2 to "UTF-16BE",
        UTF_16 to "UTF-16",
    )

    /** Falls back to UTF-8, which decodes ASCII correctly and never throws on malformed input. */
    fun charsetFor(mib: Int): Charset = runCatching {
        Charset.forName(mibToName[mib] ?: "UTF-8")
    }.getOrElse { Charsets.UTF_8 }

    fun decode(bytes: ByteArray, mib: Int): String = String(bytes, charsetFor(mib))
}
