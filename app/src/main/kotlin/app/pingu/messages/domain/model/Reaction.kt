package app.pingu.messages.domain.model

/**
 * A reaction on a message.
 *
 * Reactions are not part of SMS or MMS. A reaction is stored locally and, when the user enables
 * "Send reactions as text", additionally transmitted as the plain-text tapback other messengers
 * understand. [transmitted] records whether that text was actually sent, so the UI never implies a
 * reaction reached the other side when it did not.
 */
data class Reaction(
    val id: Long = 0L,
    val messageId: Long,
    val emoji: String,
    /** Empty for the local user; the participant address for a reaction parsed from an incoming message. */
    val authorAddress: String? = null,
    val timestamp: Long = 0L,
    val transmitted: Boolean = false,
) {
    val isFromMe: Boolean get() = authorAddress.isNullOrEmpty()
}
