package app.pingu.messages.core.text

/**
 * Emoji helpers used to decide when a message should be rendered as large standalone emoji
 * (the convention every modern messenger follows for short reactions like a single thumbs up).
 */
object EmojiText {

    /** Messages of at most this many emoji, and nothing else, are drawn oversized. */
    const val MAX_LARGE_EMOJI = 3

    private const val ZERO_WIDTH_JOINER = 0x200D
    private const val VARIATION_SELECTOR_16 = 0xFE0F
    private const val VARIATION_SELECTOR_15 = 0xFE0E
    private const val COMBINING_KEYCAP = 0x20E3

    private val emojiRanges = listOf(
        0x00A9..0x00A9, // copyright
        0x00AE..0x00AE, // registered
        0x203C..0x2049, // punctuation emoji
        0x2122..0x2122,
        0x2194..0x21AA, // arrows
        0x231A..0x231B,
        0x2328..0x2328,
        0x23CF..0x23FA,
        0x24C2..0x24C2,
        0x25AA..0x25FE,
        0x2600..0x27BF, // misc symbols and dingbats
        0x2934..0x2935,
        0x2B00..0x2BFF,
        0x3030..0x3030,
        0x303D..0x303D,
        0x3297..0x3299,
        0x1F000..0x1F02F,
        0x1F0A0..0x1F0FF,
        0x1F100..0x1F1FF, // enclosed characters and regional indicators
        0x1F200..0x1F2FF,
        0x1F300..0x1F5FF, // misc symbols and pictographs
        0x1F600..0x1F64F, // emoticons
        0x1F650..0x1F67F,
        0x1F680..0x1F6FF, // transport
        0x1F700..0x1F77F,
        0x1F780..0x1F7FF,
        0x1F900..0x1F9FF, // supplemental symbols
        0x1FA00..0x1FAFF,
    )

    private fun isEmojiCodePoint(codePoint: Int): Boolean =
        emojiRanges.any { codePoint in it }

    private fun isModifier(codePoint: Int): Boolean =
        codePoint == ZERO_WIDTH_JOINER ||
            codePoint == VARIATION_SELECTOR_15 ||
            codePoint == VARIATION_SELECTOR_16 ||
            codePoint == COMBINING_KEYCAP ||
            codePoint in 0x1F3FB..0x1F3FF // skin tone modifiers

    /**
     * Counts visible emoji clusters, treating ZWJ sequences (family emoji), keycaps and skin-tone
     * modifiers as a single unit. Returns null when the text contains anything that is not emoji
     * or whitespace.
     */
    fun countStandaloneEmoji(text: String): Int? {
        if (text.isEmpty()) return null
        var index = 0
        var clusters = 0
        var previousWasEmoji = false
        var joinPending = false

        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val charCount = Character.charCount(codePoint)
            when {
                Character.isWhitespace(codePoint) -> {
                    previousWasEmoji = false
                    joinPending = false
                }

                codePoint == ZERO_WIDTH_JOINER -> {
                    if (!previousWasEmoji) return null
                    joinPending = true
                }

                isModifier(codePoint) -> {
                    if (!previousWasEmoji) return null
                }

                isEmojiCodePoint(codePoint) -> {
                    if (joinPending) {
                        joinPending = false
                    } else {
                        clusters++
                    }
                    previousWasEmoji = true
                }

                Character.isDigit(codePoint) || codePoint == '#'.code || codePoint == '*'.code -> {
                    // Only valid as a keycap base, e.g. "1" + VS16 + COMBINING KEYCAP.
                    val next = index + charCount
                    val followedByKeycap = next < text.length &&
                        (text.codePointAt(next) == VARIATION_SELECTOR_16 || text.codePointAt(next) == COMBINING_KEYCAP)
                    if (!followedByKeycap) return null
                    clusters++
                    previousWasEmoji = true
                }

                else -> return null
            }
            index += charCount
        }
        return if (clusters == 0) null else clusters
    }

    /** True when the message is short enough and made only of emoji to warrant large rendering. */
    fun isLargeEmojiMessage(text: String): Boolean {
        val count = countStandaloneEmoji(text.trim()) ?: return false
        return count in 1..MAX_LARGE_EMOJI
    }
}
