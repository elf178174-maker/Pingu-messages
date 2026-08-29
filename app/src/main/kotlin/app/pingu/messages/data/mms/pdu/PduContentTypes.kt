package app.pingu.messages.data.mms.pdu

/**
 * The WSP well-known content type table (WAP-230-WSP, Assigned Numbers).
 *
 * A content type in a PDU is usually a single byte referring to this table; anything not in it is
 * written out as a plain string instead. Only the assignments that can realistically appear in an
 * MMS are listed, plus the multipart types the composer emits.
 */
object PduContentTypes {

    const val MULTIPART_MIXED = "application/vnd.wap.multipart.mixed"
    const val MULTIPART_RELATED = "application/vnd.wap.multipart.related"
    const val APPLICATION_SMIL = "application/smil"
    const val TEXT_PLAIN = "text/plain"

    /** Well-known value to MIME type. */
    private val values: Map<Int, String> = mapOf(
        0x00 to "*/*",
        0x01 to "text/*",
        0x02 to "text/html",
        0x03 to "text/plain",
        0x04 to "text/x-hdml",
        0x05 to "text/x-ttml",
        0x06 to "text/x-vCalendar",
        0x07 to "text/x-vCard",
        0x08 to "text/vnd.wap.wml",
        0x09 to "text/vnd.wap.wmlscript",
        0x0A to "text/vnd.wap.wta-event",
        0x0B to "multipart/*",
        0x0C to "multipart/mixed",
        0x0D to "multipart/form-data",
        0x0E to "multipart/byterantes",
        0x0F to "multipart/alternative",
        0x10 to "application/*",
        0x11 to "application/java-vm",
        0x12 to "application/x-www-form-urlencoded",
        0x13 to "application/x-hdmlc",
        0x14 to "application/vnd.wap.wmlc",
        0x15 to "application/vnd.wap.wmlscriptc",
        0x16 to "application/vnd.wap.wta-eventc",
        0x17 to "application/vnd.wap.uaprof",
        0x18 to "application/vnd.wap.wtls-ca-certificate",
        0x19 to "application/vnd.wap.wtls-user-certificate",
        0x1A to "application/x-x509-ca-cert",
        0x1B to "application/x-x509-user-cert",
        0x1C to "image/*",
        0x1D to "image/gif",
        0x1E to "image/jpeg",
        0x1F to "image/tiff",
        0x20 to "image/png",
        0x21 to "image/vnd.wap.wbmp",
        0x22 to "application/vnd.wap.multipart.*",
        0x23 to MULTIPART_MIXED,
        0x24 to "application/vnd.wap.multipart.form-data",
        0x25 to "application/vnd.wap.multipart.byteranges",
        0x26 to "application/vnd.wap.multipart.alternative",
        0x27 to "application/xml",
        0x28 to "text/xml",
        0x29 to "application/vnd.wap.wbxml",
        0x2A to "application/x-x968-cross-cert",
        0x2B to "application/x-x968-ca-cert",
        0x2C to "application/x-x968-user-cert",
        0x2D to "text/vnd.wap.si",
        0x2E to "application/vnd.wap.sic",
        0x2F to "text/vnd.wap.sl",
        0x30 to "application/vnd.wap.slc",
        0x31 to "text/vnd.wap.co",
        0x32 to "application/vnd.wap.coc",
        0x33 to MULTIPART_RELATED,
        0x34 to "application/vnd.wap.sia",
        0x35 to "text/vnd.wap.connectivity-xml",
        0x36 to "application/vnd.wap.connectivity-wbxml",
        0x37 to "application/pkcs7-mime",
        0x38 to "application/vnd.wap.hashed-certificate",
        0x39 to "application/vnd.wap.signed-certificate",
        0x3A to "application/vnd.wap.cert-response",
        0x3B to "application/xhtml+xml",
        0x3C to "application/wml+xml",
        0x3D to "text/css",
        0x3E to "application/vnd.wap.mms-message",
        0x3F to "application/vnd.wap.rollover-certificate",
        0x40 to "application/vnd.wap.locc+wbxml",
        0x41 to "application/vnd.wap.loc+xml",
        0x42 to "application/vnd.syncml.dm+wbxml",
        0x43 to "application/vnd.syncml.dm+xml",
        0x44 to "application/vnd.syncml.notification",
        0x45 to "application/vnd.wap.xhtml+xml",
        0x46 to "application/vnd.wv.csp.cir",
        0x47 to "application/vnd.oma.dd+xml",
        0x48 to "application/vnd.oma.drm.message",
        0x49 to "application/vnd.oma.drm.content",
        0x4A to "application/vnd.oma.drm.rights+xml",
        0x4B to "application/vnd.oma.drm.rights+wbxml",
        0x4C to "application/vnd.wv.csp+xml",
        0x4D to "application/vnd.wv.csp+wbxml",
        0x4E to "application/vnd.syncml.ds.notification",
        0x4F to "audio/*",
        0x50 to "video/*",
        0x51 to "application/vnd.oma.dd2+xml",
        0x52 to "application/mikey",
        0x53 to "application/vnd.oma.dcd",
        0x54 to "application/vnd.oma.dcdc",
    )

    private val byName: Map<String, Int> =
        values.entries.associate { (key, value) -> value.lowercase() to key }

    fun nameOf(wellKnownValue: Int): String? = values[wellKnownValue]

    fun wellKnownValueOf(mimeType: String): Int? = byName[mimeType.lowercase()]

    /** Content types that never benefit from being re-compressed or scaled. */
    fun isImage(mimeType: String): Boolean = mimeType.startsWith("image/", ignoreCase = true)
}
