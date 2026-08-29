package app.pingu.messages.domain.model

/** A phone number belonging to a contact, with the label the user gave it ("Mobile", "Work"). */
data class ContactPhone(
    val number: String,
    val normalizedNumber: String,
    val typeLabel: String?,
)

/**
 * A device contact.
 *
 * Only the fields the app actually shows are read: id, lookup key, name, photo and phone numbers.
 * E-mail addresses, notes, birthdays and everything else in the contacts provider are never
 * queried, so a contacts permission grant gives the app the minimum it needs.
 */
data class Contact(
    val id: Long,
    val lookupKey: String,
    val displayName: String,
    val photoUri: String?,
    val thumbnailUri: String?,
    val phones: List<ContactPhone> = emptyList(),
    val isStarred: Boolean = false,
) {
    val primaryNumber: String? get() = phones.firstOrNull()?.number
}
