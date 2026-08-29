package app.pingu.messages.domain.model

import app.pingu.messages.core.util.Avatars
import app.pingu.messages.core.util.PhoneNumbers

/**
 * One participant of a conversation. [address] is whatever the telephony provider stored, which may
 * be a phone number in any format or an alphanumeric sender id.
 */
data class Recipient(
    val address: String,
    val contactId: Long? = null,
    val contactLookupKey: String? = null,
    val displayName: String? = null,
    val photoUri: String? = null,
) {
    /** The name to show: the contact name when known, otherwise a tidied number. */
    val label: String
        get() = displayName?.takeIf { it.isNotBlank() } ?: PhoneNumbers.formatForDisplay(address)

    val hasContact: Boolean get() = contactId != null

    val initials: String get() = Avatars.initials(displayName)

    val colorSlot: Int get() = Avatars.colorSlot(PhoneNumbers.matchKey(address).ifEmpty { address })

    val isDiallable: Boolean get() = PhoneNumbers.isDiallable(address)

    companion object {
        fun fromAddress(address: String): Recipient = Recipient(address = address.trim())
    }
}
