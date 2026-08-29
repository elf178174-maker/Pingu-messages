package app.pingu.messages.core.text

/**
 * Replies over SMS.
 *
 * There is no reply field in SMS or MMS, so a reply is two things at once: a local link between
 * the two messages (kept in the app database, used for the "Replying to" chip and for scrolling to
 * the original), and an optional quoted prefix in the outgoing text so the recipient sees what is
 * being answered even in an app that knows nothing about Pingu Messages.
 *
 * The wire format is the one people already recognise from e-mail: each quoted line starts with
 * "> ", followed by a blank line and the reply itself.
 */
object QuotedReply {

    private const val QUOTE_MARKER = "> "
    const val DEFAULT_MAX_QUOTE_LENGTH = 120

    /** Wraps [replyText] with a quotation of [originalText]. */
    fun format(
        originalText: String,
        replyText: String,
        maxQuoteLength: Int = DEFAULT_MAX_QUOTE_LENGTH,
    ): String {
        val condensed = originalText.replace(Regex("\\s+"), " ").trim()
        if (condensed.isEmpty()) return replyText
        val quote = if (condensed.length <= maxQuoteLength) {
            condensed
        } else {
            condensed.take(maxQuoteLength - 1).trimEnd() + "…"
        }
        return "$QUOTE_MARKER$quote\n\n$replyText"
    }

    data class Parsed(val quotedText: String, val replyText: String)

    /** Splits an incoming body that uses the quoted format, or returns null when it does not. */
    fun parse(body: String?): Parsed? {
        val text = body ?: return null
        if (!text.startsWith(QUOTE_MARKER)) return null

        val lines = text.lines()
        val quoteLines = lines.takeWhile { it.startsWith(QUOTE_MARKER) }
        if (quoteLines.isEmpty()) return null

        val remainder = lines.drop(quoteLines.size).dropWhile { it.isBlank() }
        if (remainder.isEmpty()) return null

        return Parsed(
            quotedText = quoteLines.joinToString(" ") { it.removePrefix(QUOTE_MARKER) }.trim(),
            replyText = remainder.joinToString("\n").trim(),
        )
    }
}
