package app.pingu.messages.core.text

/**
 * SMS and MMS have no reaction field. Apple's Messages, and Google Messages when it falls back to
 * SMS, therefore send reactions as ordinary text such as `Liked "see you at six"`.
 *
 * Pingu Messages recognises those messages and folds them into a real reaction on the quoted
 * message instead of showing a confusing extra bubble, and it emits the same wire format when the
 * user asks for a reaction to be sent as text (Settings > Messaging). Everything else about
 * reactions is local to the device, which is documented in the app and in the README.
 */
object Tapbacks {

    const val THUMBS_UP = "👍"
    const val HEART = "❤️"
    const val THUMBS_DOWN = "👎"
    const val LAUGH = "😂"
    const val EXCLAMATION = "‼️"
    const val QUESTION = "❓"

    /** The reaction palette offered in the UI. */
    val palette: List<String> = listOf(THUMBS_UP, HEART, LAUGH, "😮", "😢", EXCLAMATION)

    data class Parsed(
        val emoji: String,
        /** The text of the message being reacted to, as quoted by the sender. */
        val quotedText: String,
        /** True when the sender removed a previously sent reaction. */
        val removal: Boolean,
    )

    private const val OPEN_QUOTES = "\"“”‘’"

    private val verbToEmoji = mapOf(
        "Liked" to THUMBS_UP,
        "Loved" to HEART,
        "Disliked" to THUMBS_DOWN,
        "Laughed at" to LAUGH,
        "Emphasized" to EXCLAMATION,
        "Emphasised" to EXCLAMATION,
        "Questioned" to QUESTION,
    )

    private val removalNounToEmoji = mapOf(
        "like" to THUMBS_UP,
        "heart" to HEART,
        "dislike" to THUMBS_DOWN,
        "laugh" to LAUGH,
        "exclamation" to EXCLAMATION,
        "question mark" to QUESTION,
    )

    private val verbRegex = Regex(
        "^(Liked|Loved|Disliked|Laughed at|Emphasized|Emphasised|Questioned)\\s+[$OPEN_QUOTES](.+)[$OPEN_QUOTES]\$",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )

    private val reactedRegex = Regex(
        "^Reacted\\s+(\\S+)\\s+to\\s+[$OPEN_QUOTES](.+)[$OPEN_QUOTES]\$",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )

    private val removalRegex = Regex(
        "^Removed (?:a|an) (like|heart|dislike|laugh|exclamation|question mark) from\\s+" +
            "[$OPEN_QUOTES](.+)[$OPEN_QUOTES]\$",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )

    /** Returns the reaction encoded in [body], or null when it is an ordinary message. */
    fun parse(body: String?): Parsed? {
        val text = body?.trim().orEmpty()
        if (text.isEmpty() || text.length > 1_000) return null

        removalRegex.find(text)?.let { match ->
            val emoji = removalNounToEmoji[match.groupValues[1]] ?: return@let
            return Parsed(emoji, match.groupValues[2], removal = true)
        }
        verbRegex.find(text)?.let { match ->
            val emoji = verbToEmoji[match.groupValues[1]] ?: return@let
            return Parsed(emoji, match.groupValues[2], removal = false)
        }
        reactedRegex.find(text)?.let { match ->
            val emoji = match.groupValues[1]
            if (EmojiText.countStandaloneEmoji(emoji) != null) {
                return Parsed(emoji, match.groupValues[2], removal = false)
            }
        }
        return null
    }

    /** Builds the outgoing text for a reaction, in the format other messengers understand. */
    fun format(emoji: String, quotedText: String, maxQuoteLength: Int = 120): String {
        val quote = quotedText.trim().let {
            if (it.length <= maxQuoteLength) it else it.take(maxQuoteLength - 1).trimEnd() + "…"
        }
        return "Reacted $emoji to “$quote”"
    }

    /** Builds the outgoing text for removing a reaction. */
    fun formatRemoval(emoji: String, quotedText: String, maxQuoteLength: Int = 120): String {
        val noun = removalNounToEmoji.entries.firstOrNull { it.value == emoji }?.key ?: "reaction"
        val article = if (noun.first() in "aeiou") "an" else "a"
        val quote = quotedText.trim().let {
            if (it.length <= maxQuoteLength) it else it.take(maxQuoteLength - 1).trimEnd() + "…"
        }
        return "Removed $article $noun from “$quote”"
    }
}
