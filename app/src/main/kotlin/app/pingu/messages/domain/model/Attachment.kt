package app.pingu.messages.domain.model

/** How an attachment should be presented, derived from its MIME type. */
enum class AttachmentKind {
    IMAGE,
    GIF,
    VIDEO,
    AUDIO,

    /** Audio recorded in-app with the hold-to-record button. */
    VOICE,
    CONTACT_CARD,
    LOCATION,
    FILE,
    ;

    val isVisualMedia: Boolean get() = this == IMAGE || this == GIF || this == VIDEO
    val isPlayable: Boolean get() = this == AUDIO || this == VOICE || this == VIDEO
}

/**
 * A single MMS part, or a pending attachment in the composer.
 *
 * [uri] is always a `content://` URI: either an MMS part URI owned by the telephony provider, or a
 * URI the user granted through the photo/document picker. The app never copies attachments to a
 * world-readable location and never hands out `file://` URIs.
 */
data class Attachment(
    val id: Long = 0L,
    val messageId: Long = 0L,
    val uri: String,
    val mimeType: String,
    val fileName: String? = null,
    val sizeBytes: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val durationMillis: Long = 0L,
    /** Set for the location parts the app generates, so the map link can be reconstructed. */
    val extra: String? = null,
) {
    val kind: AttachmentKind get() = kindOf(mimeType, fileName, extra)

    val hasKnownDimensions: Boolean get() = width > 0 && height > 0

    /** Aspect ratio used to reserve space before the image is decoded. */
    val aspectRatio: Float
        get() = if (hasKnownDimensions) width.toFloat() / height.toFloat() else DEFAULT_ASPECT_RATIO

    companion object {
        const val DEFAULT_ASPECT_RATIO = 4f / 3f
        const val EXTRA_VOICE_MESSAGE = "voice"
        const val EXTRA_LOCATION_PREFIX = "location:"

        fun kindOf(mimeType: String, fileName: String? = null, extra: String? = null): AttachmentKind {
            val type = mimeType.lowercase()
            return when {
                extra == EXTRA_VOICE_MESSAGE -> AttachmentKind.VOICE
                extra?.startsWith(EXTRA_LOCATION_PREFIX) == true -> AttachmentKind.LOCATION
                type == "image/gif" -> AttachmentKind.GIF
                type.startsWith("image/") -> AttachmentKind.IMAGE
                type.startsWith("video/") -> AttachmentKind.VIDEO
                type.startsWith("audio/") -> AttachmentKind.AUDIO
                type == "text/x-vcard" || type == "text/vcard" -> AttachmentKind.CONTACT_CARD
                fileName?.endsWith(".vcf", ignoreCase = true) == true -> AttachmentKind.CONTACT_CARD
                else -> AttachmentKind.FILE
            }
        }
    }
}
