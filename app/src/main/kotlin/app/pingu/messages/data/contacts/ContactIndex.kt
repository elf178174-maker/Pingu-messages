package app.pingu.messages.data.contacts

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import app.pingu.messages.core.util.PhoneNumbers
import app.pingu.messages.domain.model.Recipient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * An in-memory index from phone number to contact, refreshed when the contacts provider changes.
 *
 * Resolving names this way turns what would be one `PhoneLookup` IPC per conversation row into a
 * single map lookup, which is the difference between a list that scrolls and one that stutters.
 * The index is also what makes a newly saved contact appear in every open screen at once.
 */
class ContactIndex(
    private val context: Context,
    private val dataSource: ContactsDataSource,
    private val scope: CoroutineScope,
) {

    private val entries = MutableStateFlow<Map<String, ContactSummary>>(emptyMap())

    /** Emits a new map whenever the contact list changes, so the UI recomposes with real names. */
    val state: StateFlow<Map<String, ContactSummary>> = entries.asStateFlow()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            scope.launch { refresh() }
        }
    }

    private var registered = false

    fun start() {
        scope.launch {
            refresh()
            if (!registered && dataSource.hasPermission()) {
                runCatching {
                    context.contentResolver.registerContentObserver(
                        ContactsContract.Contacts.CONTENT_URI,
                        true,
                        observer,
                    )
                    registered = true
                }
            }
        }
    }

    fun stop() {
        if (registered) {
            runCatching { context.contentResolver.unregisterContentObserver(observer) }
            registered = false
        }
    }

    suspend fun refresh() {
        val loaded = withContext(Dispatchers.IO) { dataSource.loadIndex() }
        entries.value = loaded
    }

    fun lookup(address: String): ContactSummary? {
        val key = PhoneNumbers.matchKey(address)
        if (key.isEmpty()) return null
        return entries.value[key]
    }

    /** Builds a [Recipient], attaching contact details when the number is known. */
    fun toRecipient(address: String): Recipient {
        val contact = lookup(address)
        return Recipient(
            address = address,
            contactId = contact?.contactId,
            contactLookupKey = contact?.lookupKey,
            displayName = contact?.displayName,
            photoUri = contact?.photoUri,
        )
    }

    fun toRecipients(addresses: List<String>): List<Recipient> = addresses.map(::toRecipient)
}
