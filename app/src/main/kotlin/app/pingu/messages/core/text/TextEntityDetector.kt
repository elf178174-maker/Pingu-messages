package app.pingu.messages.core.text

/**
 * Finds links, e-mail addresses, phone numbers and street addresses in message bodies.
 *
 * This replaces `android.text.util.Linkify`, which cannot be unit tested, no longer offers address
 * detection at all, and is happy to turn any run of digits into a phone number. The rules here are
 * deliberately conservative: a false positive turns readable text into a tappable link that does
 * the wrong thing, which is worse than missing one.
 *
 * Address detection is a documented heuristic (house number followed by a street-type keyword),
 * not a geocoder. It exists so "Open in Maps" can be offered when it is very likely correct.
 */
object TextEntityDetector {

    private const val MIN_PHONE_DIGITS = 7
    private const val MAX_PHONE_DIGITS = 15

    private val urlRegex = Regex(
        """\b(?:https?://|www\.)[-\w@:%+.~#?&/=]{2,}""",
        RegexOption.IGNORE_CASE,
    )

    private val emailRegex = Regex(
        """\b[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}\b""",
    )

    private val phoneRegex = Regex(
        """(?<![\w])\+?\d(?:[\d\s().\-]{4,20})\d(?![\w])""",
    )

    private const val STREET_TYPES =
        "Street|St|Avenue|Ave|Road|Rd|Boulevard|Blvd|Lane|Ln|Drive|Dr|Court|Ct|Way|Square|Sq|" +
            "Place|Pl|Terrace|Parkway|Pkwy|Highway|Hwy|Close|Crescent|Gardens|Grove|Walk"

    private val addressRegex = Regex(
        """\b\d{1,5}[A-Za-z]?\s+(?:[A-Z][\w'\-]*\s+){0,3}(?:$STREET_TYPES)\b\.?""",
    )

    /**
     * Returns non-overlapping entities ordered by position. When two patterns match the same
     * region the more specific one wins, in the order URL, e-mail, address, phone number.
     */
    fun detect(text: String): List<TextEntity> {
        if (text.isEmpty()) return emptyList()

        val accepted = ArrayList<TextEntity>()

        fun consider(candidate: TextEntity) {
            val overlaps = accepted.any {
                candidate.start < it.endExclusive && it.start < candidate.endExclusive
            }
            if (!overlaps) accepted.add(candidate)
        }

        urlRegex.findAll(text).forEach {
            consider(TextEntity(TextEntity.Type.URL, it.range.first, it.range.last + 1, it.value))
        }
        emailRegex.findAll(text).forEach {
            consider(TextEntity(TextEntity.Type.EMAIL, it.range.first, it.range.last + 1, it.value))
        }
        addressRegex.findAll(text).forEach {
            consider(TextEntity(TextEntity.Type.ADDRESS, it.range.first, it.range.last + 1, it.value))
        }
        phoneRegex.findAll(text).forEach { match ->
            val digits = match.value.count { it.isDigit() }
            if (digits in MIN_PHONE_DIGITS..MAX_PHONE_DIGITS) {
                consider(
                    TextEntity(TextEntity.Type.PHONE, match.range.first, match.range.last + 1, match.value),
                )
            }
        }

        return accepted.sortedBy { it.start }
    }

    /** Normalises a matched URL into something an intent can open. */
    fun toUri(entity: TextEntity): String = when (entity.type) {
        TextEntity.Type.URL ->
            if (entity.text.startsWith("www.", ignoreCase = true)) "https://${entity.text}" else entity.text
        TextEntity.Type.EMAIL -> "mailto:${entity.text}"
        TextEntity.Type.PHONE -> "tel:${entity.text.filter { it.isDigit() || it == '+' }}"
        TextEntity.Type.ADDRESS -> "geo:0,0?q=${entity.text.trim()}"
    }
}
