package app.pingu.messages.ui.screens.newmessage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pingu.messages.core.util.PhoneNumbers
import app.pingu.messages.data.contacts.ContactsDataSource
import app.pingu.messages.data.repository.ConversationRepository
import app.pingu.messages.data.repository.DraftRepository
import app.pingu.messages.data.repository.MessageRepository
import app.pingu.messages.domain.model.Draft
import app.pingu.messages.domain.model.Contact
import app.pingu.messages.domain.model.Recipient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NewMessageUiState(
    val query: String = "",
    val recipients: List<Recipient> = emptyList(),
    val suggestions: List<Contact> = emptyList(),
    val hasContactsPermission: Boolean = false,
    val isSearching: Boolean = false,
) {
    /** True when the typed text is itself a usable destination, e.g. a raw phone number. */
    val queryIsDiallable: Boolean get() = PhoneNumbers.isDiallable(query)

    val canContinue: Boolean get() = recipients.isNotEmpty()
}

/**
 * Choosing who a new message goes to.
 *
 * Contact search is debounced, because the contacts provider is queried through IPC and firing one
 * per keystroke makes the field feel sticky on a phone with thousands of contacts.
 */
class NewMessageViewModel(
    private val contacts: ContactsDataSource,
    private val conversations: ConversationRepository,
    private val drafts: DraftRepository,
    private val messages: MessageRepository,
    /** Messages being forwarded, if the screen was opened from "Forward". */
    private val forwardMessageIds: List<Long> = emptyList(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        NewMessageUiState(hasContactsPermission = contacts.hasPermission()),
    )
    val uiState: StateFlow<NewMessageUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadFrequent()
    }

    fun refreshPermission() {
        _uiState.value = _uiState.value.copy(hasContactsPermission = contacts.hasPermission())
        loadFrequent()
    }

    private fun loadFrequent() {
        viewModelScope.launch {
            val frequent = runCatching { contacts.frequent() }.getOrDefault(emptyList())
            if (_uiState.value.query.isBlank()) {
                _uiState.value = _uiState.value.copy(suggestions = frequent)
            }
        }
    }

    fun onQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            loadFrequent()
            _uiState.value = _uiState.value.copy(isSearching = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            _uiState.value = _uiState.value.copy(isSearching = true)
            val results = runCatching { contacts.search(query) }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(suggestions = results, isSearching = false)
        }
    }

    fun addRecipient(address: String, displayName: String? = null, contactId: Long? = null) {
        val trimmed = address.trim()
        if (trimmed.isEmpty()) return
        val existing = _uiState.value.recipients
        if (existing.any { PhoneNumbers.sameNumber(it.address, trimmed) }) return
        _uiState.value = _uiState.value.copy(
            recipients = existing + Recipient(
                address = trimmed,
                displayName = displayName,
                contactId = contactId,
            ),
            query = "",
        )
        loadFrequent()
    }

    fun removeRecipient(recipient: Recipient) {
        _uiState.value = _uiState.value.copy(
            recipients = _uiState.value.recipients.filterNot { it.address == recipient.address },
        )
    }

    /**
     * Resolves (creating if needed) the thread for the chosen recipients.
     *
     * When the screen was opened to forward messages, their text and attachments are written into
     * that thread's draft first, so the conversation opens with the content already in the composer
     * and the user gets a last look before it is sent.
     */
    fun resolveThread(onResolved: (Long) -> Unit) {
        val addresses = _uiState.value.recipients.map { it.address }
        if (addresses.isEmpty()) return
        viewModelScope.launch {
            val threadId = conversations.threadIdFor(addresses)
            if (threadId == null || threadId <= 0) return@launch
            conversations.ensureMetadata(threadId)
            if (forwardMessageIds.isNotEmpty()) {
                prepareForwardDraft(threadId)
            }
            onResolved(threadId)
        }
    }

    private suspend fun prepareForwardDraft(threadId: Long) {
        val forwarded = messages.getMessages(forwardMessageIds).sortedBy { it.timestamp }
        if (forwarded.isEmpty()) return
        val existing = drafts.get(threadId)
        val text = listOfNotNull(
            existing?.text?.takeIf { it.isNotBlank() },
            forwarded.mapNotNull { it.body?.takeIf { body -> body.isNotBlank() } }
                .joinToString("\n")
                .takeIf { it.isNotBlank() },
        ).joinToString("\n")

        drafts.save(
            Draft(
                threadId = threadId,
                text = text,
                attachments = existing?.attachments.orEmpty() + forwarded.flatMap { it.attachments },
            ),
        )
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 220L
    }
}
