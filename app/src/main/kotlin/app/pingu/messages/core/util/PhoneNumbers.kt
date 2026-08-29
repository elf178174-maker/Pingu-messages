package app.pingu.messages.core.util

/**
 * Phone number handling that does not depend on the Android framework.
 *
 * Two numbers are considered the same conversation participant when their significant digits
 * match. "Significant" means the last [MATCH_DIGITS] digits, which is the same heuristic the
 * platform's own telephony stack uses: it survives the difference between `+44 7700 900123`,
 * `07700 900123` and `00447700900123` without needing a country database, and it is deliberately
 * conservative about short codes (which must match exactly).
 */
object PhoneNumbers {

    /** Number of trailing digits compared when matching two numbers. */
    const val MATCH_DIGITS = 9

    /** Numbers at or below this length are short codes and must match exactly. */
    private const val SHORT_CODE_MAX_LENGTH = 6

    private val digitsOnly = Regex("[^0-9]")

    /**
     * Reduces a number to the digits that identify it, dropping formatting, a leading `+` and any
     * international/trunk prefix. Alphanumeric senders (`GOOGLE`, `Amazon`) have no digits and are
     * returned upper-cased and trimmed instead, because carriers treat them as opaque identifiers.
     */
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()
        val digits = digitsOnly.replace(trimmed, "")
        if (digits.isEmpty()) return trimmed.uppercase()
        return digits
    }

    /** The comparison key used for matching and for grouping recipients into a thread. */
    fun matchKey(raw: String?): String {
        val normalized = normalize(raw)
        if (normalized.isEmpty()) return ""
        if (!normalized.all { it.isDigit() }) return normalized
        if (normalized.length <= SHORT_CODE_MAX_LENGTH) return normalized
        return normalized.takeLast(MATCH_DIGITS)
    }

    /** True when both values plausibly address the same person. */
    fun sameNumber(first: String?, second: String?): Boolean {
        val a = matchKey(first)
        val b = matchKey(second)
        return a.isNotEmpty() && a == b
    }

    /** True when the value looks like a telephone number rather than an alphanumeric sender. */
    fun isDiallable(raw: String?): Boolean {
        val normalized = normalize(raw)
        return normalized.length >= 3 && normalized.all { it.isDigit() }
    }

    /** True when the sender is an alphanumeric short-code style identifier such as `MYBANK`. */
    fun isAlphanumericSender(raw: String?): Boolean =
        !raw.isNullOrBlank() && !isDiallable(raw)

    /**
     * Light formatting for display when the platform formatter is unavailable (unit tests, widget
     * rendering). Keeps a leading `+`, groups the rest in readable chunks and leaves anything
     * unusual untouched rather than mangling it.
     */
    fun formatForDisplay(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()
        val hasPlus = trimmed.startsWith("+")
        val digits = digitsOnly.replace(trimmed, "")
        if (digits.isEmpty() || digits.length < 7 || digits.length > 15) return trimmed
        val grouped = digits.reversed().chunked(3).joinToString(" ").reversed()
        return if (hasPlus) "+$grouped" else grouped
    }

    /** Splits a raw recipient list as stored by the telephony provider (space separated). */
    fun splitRecipients(raw: String?): List<String> =
        raw.orEmpty()
            .split(' ', ',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
