package app.pingu.messages.data.mms.pdu

/**
 * Builds the SMIL presentation part of an outgoing MMS.
 *
 * SMIL describes how the parts are laid out on screen. It is optional in the specification but not
 * in practice: several carriers and many older handsets only render an MMS as a slideshow when a
 * presentation part is present, and show nothing at all otherwise. The document produced here is
 * the smallest one that displays reliably: one slide per media item, with any accompanying text
 * underneath.
 */
object SmilBuilder {

    private const val SLIDE_DURATION_MS = 5_000

    fun build(parts: List<MmsPart>): String {
        val builder = StringBuilder(512)
        builder.append("<smil><head><layout>")
        builder.append("<root-layout width=\"320px\" height=\"480px\"/>")
        builder.append("<region id=\"Image\" left=\"0\" top=\"0\" width=\"100%\" height=\"80%\" fit=\"meet\"/>")
        builder.append("<region id=\"Text\" left=\"0\" top=\"80%\" width=\"100%\" height=\"20%\"/>")
        builder.append("</layout></head><body>")

        val media = parts.filter { !it.isText && !it.isSmil }
        val texts = parts.filter { it.isText }

        if (media.isEmpty()) {
            builder.append("<par dur=\"").append(SLIDE_DURATION_MS).append("ms\">")
            texts.forEach { part ->
                builder.append("<text src=\"").append(escape(part.contentLocation.orEmpty()))
                    .append("\" region=\"Text\"/>")
            }
            builder.append("</par>")
        } else {
            media.forEachIndexed { index, part ->
                builder.append("<par dur=\"").append(SLIDE_DURATION_MS).append("ms\">")
                builder.append(mediaElement(part))
                if (index == 0) {
                    texts.forEach { text ->
                        builder.append("<text src=\"").append(escape(text.contentLocation.orEmpty()))
                            .append("\" region=\"Text\"/>")
                    }
                }
                builder.append("</par>")
            }
        }

        builder.append("</body></smil>")
        return builder.toString()
    }

    private fun mediaElement(part: MmsPart): String {
        val source = escape(part.contentLocation.orEmpty())
        return when {
            part.contentType.startsWith("image/", ignoreCase = true) ->
                "<img src=\"$source\" region=\"Image\"/>"

            part.contentType.startsWith("video/", ignoreCase = true) ->
                "<video src=\"$source\" region=\"Image\"/>"

            part.contentType.startsWith("audio/", ignoreCase = true) ->
                "<audio src=\"$source\"/>"

            else -> "<ref src=\"$source\" region=\"Image\"/>"
        }
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
