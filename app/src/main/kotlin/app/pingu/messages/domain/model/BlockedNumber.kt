package app.pingu.messages.domain.model

/** Why a number ended up on the block list, which drives what the UI offers to undo. */
enum class BlockOrigin {
    /** The user blocked it explicitly. */
    MANUAL,

    /** The user reported the conversation as spam. */
    REPORTED_SPAM,

    /** Mirrored from Android's system-wide blocked number list. */
    SYSTEM,
}

/**
 * A blocked sender.
 *
 * When Pingu Messages holds the default SMS role it can also read and write Android's system
 * blocked-number list, so blocking here blocks calls too and survives switching messaging apps.
 * The local list is kept in step with it and is the fallback when the platform refuses access.
 */
data class BlockedNumber(
    val id: Long = 0L,
    val address: String,
    /** Normalised comparison key; see [app.pingu.messages.core.util.PhoneNumbers.matchKey]. */
    val matchKey: String,
    val origin: BlockOrigin = BlockOrigin.MANUAL,
    val blockedAt: Long = 0L,
    /** Present when the number is also in the platform's blocked list. */
    val syncedToSystem: Boolean = false,
    val note: String? = null,
)
