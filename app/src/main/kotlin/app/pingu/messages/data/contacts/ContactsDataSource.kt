package app.pingu.messages.data.contacts

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import app.pingu.messages.core.util.PhoneNumbers
import app.pingu.messages.data.telephony.CursorUtils.booleanOr
import app.pingu.messages.data.telephony.CursorUtils.intOr
import app.pingu.messages.data.telephony.CursorUtils.longOr
import app.pingu.messages.data.telephony.CursorUtils.queryAll
import app.pingu.messages.data.telephony.CursorUtils.stringOrNull
import app.pingu.messages.domain.model.Contact
import app.pingu.messages.domain.model.ContactPhone

/** The subset of a contact the app needs to label a conversation. */
data class ContactSummary(
    val contactId: Long,
    val lookupKey: String,
    val displayName: String,
    val photoUri: String?,
)

/**
 * Reads the device contact list.
 *
 * Only identity fields and phone numbers are queried. Nothing else in the contacts provider is
 * touched, and the app works without the permission at all: conversations then show formatted phone
 * numbers instead of names, which is a degraded experience rather than a broken one.
 */
class ContactsDataSource(private val context: Context) {

    private val resolver get() = context.contentResolver

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * An index from normalised phone number to contact, built with a single query.
     *
     * Building the whole index once per sync is dramatically cheaper than the alternative of a
     * `PhoneLookup` query per conversation, which is an IPC round trip each time.
     */
    fun loadIndex(): Map<String, ContactSummary> {
        if (!hasPermission()) return emptyMap()
        val index = HashMap<String, ContactSummary>()
        resolver.queryAll(
            uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
            ),
            mapper = { cursor ->
                val number = cursor.stringOrNull(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    ?: cursor.stringOrNull(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)
                val name =
                    cursor.stringOrNull(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                if (number.isNullOrBlank() || name.isNullOrBlank()) return@queryAll null
                val key = PhoneNumbers.matchKey(number)
                if (key.isEmpty()) return@queryAll null
                index.putIfAbsent(
                    key,
                    ContactSummary(
                        contactId = cursor.longOr(ContactsContract.CommonDataKinds.Phone.CONTACT_ID),
                        lookupKey = cursor.stringOrNull(
                            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                        ).orEmpty(),
                        displayName = name,
                        photoUri = cursor.stringOrNull(
                            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                        ),
                    ),
                )
                null
            },
        )
        return index
    }

    /** Contacts matching a name or number fragment, for the recipient picker and global search. */
    fun search(query: String, limit: Int = 30): List<Contact> {
        if (!hasPermission() || query.isBlank()) return emptyList()
        val uri = Uri.withAppendedPath(
            ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
            Uri.encode(query),
        )
        val byContact = LinkedHashMap<Long, MutableList<ContactPhone>>()
        val details = HashMap<Long, Contact>()

        resolver.queryAll(
            uri = uri,
            projection = PHONE_PROJECTION,
            sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC",
            mapper = { cursor ->
                val contactId = cursor.longOr(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val number = cursor.stringOrNull(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    ?: return@queryAll null
                val phone = ContactPhone(
                    number = number,
                    normalizedNumber = PhoneNumbers.normalize(number),
                    typeLabel = phoneTypeLabel(cursor),
                )
                byContact.getOrPut(contactId) { ArrayList() }.add(phone)
                details.getOrPut(contactId) {
                    Contact(
                        id = contactId,
                        lookupKey = cursor.stringOrNull(
                            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                        ).orEmpty(),
                        displayName = cursor.stringOrNull(
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                        ).orEmpty(),
                        photoUri = cursor.stringOrNull(
                            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                        ),
                        thumbnailUri = cursor.stringOrNull(
                            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                        ),
                        isStarred = cursor.booleanOr(ContactsContract.CommonDataKinds.Phone.STARRED),
                    )
                }
                null
            },
        )

        return byContact.entries.take(limit).mapNotNull { (contactId, phones) ->
            details[contactId]?.copy(phones = phones.distinctBy { it.normalizedNumber })
        }
    }

    /** Frequently used contacts, shown before the user types anything in the recipient picker. */
    fun frequent(limit: Int = 12): List<Contact> {
        if (!hasPermission()) return emptyList()
        val byContact = LinkedHashMap<Long, Contact>()
        resolver.queryAll(
            uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection = PHONE_PROJECTION,
            selection = "${ContactsContract.CommonDataKinds.Phone.STARRED} = 1",
            sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC",
            mapper = { cursor ->
                val contactId = cursor.longOr(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val number = cursor.stringOrNull(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    ?: return@queryAll null
                if (!byContact.containsKey(contactId)) {
                    byContact[contactId] = Contact(
                        id = contactId,
                        lookupKey = cursor.stringOrNull(
                            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
                        ).orEmpty(),
                        displayName = cursor.stringOrNull(
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                        ).orEmpty(),
                        photoUri = cursor.stringOrNull(ContactsContract.CommonDataKinds.Phone.PHOTO_URI),
                        thumbnailUri = cursor.stringOrNull(
                            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
                        ),
                        phones = listOf(
                            ContactPhone(number, PhoneNumbers.normalize(number), phoneTypeLabel(cursor)),
                        ),
                        isStarred = true,
                    )
                }
                null
            },
        )
        return byContact.values.take(limit)
    }

    /** The `content://` URI of a contact's vCard, used to attach a contact card to a message. */
    fun vCardUri(lookupKey: String): Uri? = if (lookupKey.isBlank()) {
        null
    } else {
        Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, lookupKey)
    }

    /** The URI that opens a contact in the system contacts app. */
    fun contactUri(contactId: Long): Uri =
        ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)

    private fun phoneTypeLabel(cursor: android.database.Cursor): String? {
        val type = cursor.intOr(ContactsContract.CommonDataKinds.Phone.TYPE, -1)
        if (type < 0) return null
        val custom = cursor.stringOrNull(ContactsContract.CommonDataKinds.Phone.LABEL)
        return ContactsContract.CommonDataKinds.Phone
            .getTypeLabel(context.resources, type, custom)
            ?.toString()
    }

    private companion object {
        val PHONE_PROJECTION = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
            ContactsContract.CommonDataKinds.Phone.STARRED,
        )
    }
}
