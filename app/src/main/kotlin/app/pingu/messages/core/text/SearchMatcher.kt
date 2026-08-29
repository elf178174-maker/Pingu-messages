package app.pingu.messages.core.text

import java.text.Normalizer
import java.util.Locale

/**
 * Case- and accent-insensitive substring search that reports positions in the *original* string so
 * results can be highlighted without shifting by a character.
 *
 * Folding is done per character (decompose, drop combining marks, keep the first base character)
 * which keeps a strict one-to-one mapping between the folded and the original text. That is what
 * makes highlight offsets trustworthy for names like "Renee" matching "Renée".
 */
object SearchMatcher {

    private val combiningMarks = Regex("\\p{Mn}+")

    fun fold(text: String): String {
        val builder = StringBuilder(text.length)
        for (character in text) {
            val decomposed = Normalizer.normalize(character.toString(), Normalizer.Form.NFD)
            val stripped = combiningMarks.replace(decomposed, "")
            builder.append(if (stripped.isEmpty()) character else stripped.first())
        }
        return builder.toString().lowercase(Locale.ROOT)
    }

    /** True when [haystack] contains [needle], ignoring case and accents. */
    fun contains(haystack: String?, needle: String): Boolean {
        if (needle.isBlank()) return false
        if (haystack.isNullOrEmpty()) return false
        return fold(haystack).contains(fold(needle))
    }

    /** All match ranges of [needle] inside [haystack], expressed as offsets into [haystack]. */
    fun findRanges(haystack: String, needle: String, limit: Int = 32): List<IntRange> {
        if (needle.isBlank() || haystack.isEmpty()) return emptyList()
        val foldedHaystack = fold(haystack)
        val foldedNeedle = fold(needle)
        if (foldedNeedle.isEmpty()) return emptyList()

        val ranges = ArrayList<IntRange>()
        var index = foldedHaystack.indexOf(foldedNeedle)
        while (index >= 0 && ranges.size < limit) {
            ranges.add(index until index + foldedNeedle.length)
            index = foldedHaystack.indexOf(foldedNeedle, index + foldedNeedle.length)
        }
        return ranges
    }

    /**
     * A short excerpt of [text] centred on the first match, for search result rows. Returns the
     * excerpt together with the match range inside it.
     */
    data class Excerpt(val text: String, val matchRange: IntRange?, val truncatedStart: Boolean)

    fun excerpt(text: String, needle: String, maxLength: Int = 120): Excerpt {
        val condensed = text.replace(Regex("\\s+"), " ").trim()
        if (condensed.length <= maxLength) {
            return Excerpt(condensed, findRanges(condensed, needle).firstOrNull(), false)
        }
        val first = findRanges(condensed, needle).firstOrNull()
            ?: return Excerpt(condensed.take(maxLength).trimEnd() + "…", null, false)

        val contextBefore = 24
        val start = (first.first - contextBefore).coerceAtLeast(0)
        val end = (start + maxLength).coerceAtMost(condensed.length)
        val slice = condensed.substring(start, end)
        val suffix = if (end < condensed.length) "…" else ""
        val shifted = (first.first - start) until (first.last + 1 - start).coerceAtMost(slice.length)
        return Excerpt(
            text = slice + suffix,
            matchRange = if (shifted.first in slice.indices) shifted else null,
            truncatedStart = start > 0,
        )
    }
}
