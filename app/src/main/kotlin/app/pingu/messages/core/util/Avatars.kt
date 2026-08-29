package app.pingu.messages.core.util

/**
 * Deterministic avatar helpers. The same contact always gets the same initials and the same colour
 * slot, in this session and the next, because both are derived from the identifying string rather
 * than from list position.
 */
object Avatars {

    /** Number of colour slots the theme provides for generated avatars. */
    const val COLOR_SLOTS = 8

    /**
     * One or two initials for a display name. Numbers have no useful initials, so callers should
     * fall back to an icon when this returns an empty string.
     */
    fun initials(displayName: String?): String {
        val name = displayName?.trim().orEmpty()
        if (name.isEmpty()) return ""
        if (PhoneNumbers.isDiallable(name)) return ""
        val words = name.split(' ', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.first().isLetterOrDigit() }
        return when {
            words.isEmpty() -> ""
            words.size == 1 -> words[0].take(1).uppercase()
            else -> (words.first().take(1) + words.last().take(1)).uppercase()
        }
    }

    /** Stable colour slot in `0 until COLOR_SLOTS` for an identity key. */
    fun colorSlot(key: String?): Int {
        if (key.isNullOrEmpty()) return 0
        var hash = 0
        for (character in key) {
            hash = hash * 31 + character.code
        }
        val positive = hash and Int.MAX_VALUE
        return positive % COLOR_SLOTS
    }
}
