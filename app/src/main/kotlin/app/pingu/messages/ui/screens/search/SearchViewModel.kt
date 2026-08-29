package app.pingu.messages.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pingu.messages.core.text.SearchMatcher
import app.pingu.messages.core.util.PhoneNumbers
import app.pingu.messages.data.contacts.ContactIndex
import app.pingu.messages.data.contacts.ContactsDataSource
import app.pingu.messages.data.local.dao.AttachmentWithContext
import app.pingu.messages.data.local.dao.MessageSearchRow
import app.pingu.messages.data.repository.ConversationRepository
import app.pingu.messages.data.repository.MessageRepository
import app.pingu.messages.domain.model.Contact
import app.pingu.messages.domain.model.Conversation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One message hit, with the context needed to render it without a second query. */
data class MessageHit(
    val messageId: Long,
    val threadId: Long,
    val conversationTitle: String,
    val excerpt: SearchMatcher.Excerpt,
    val timestamp: Long,
    val isOutgoing: Boolean,
)

data class SearchUiState(
    val query: String = "",
    val conversations: List<Conversation> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    val messages: List<MessageHit> = emptyList(),
    val attachments: List<AttachmentWithContext> = emptyList(),
    val totalMessageHits: Int = 0,
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
) {
    val isEmpty: Boolean
        get() = conversations.isEmpty() && contacts.isEmpty() &&
            messages.isEmpty() && attachments.isEmpty()
}

/**
 * Global search across conversations, contacts, message bodies and attachment names.
 *
 * Matching is accent- and case-insensitive and reports the position of each hit, so results can
 * highlight the matched words rather than leaving the user to find them. Message results are paged:
 * the first screenful is loaded immediately and more only if the user asks.
 */
class SearchViewModel(
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
    private val contactsDataSource: ContactsDataSource,
    private val contactIndex: ContactIndex,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var loadedMessagePages = 1

    fun onQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.value = SearchUiState()
            return
        }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MILLIS)
            runSearch(query)
        }
    }

    fun loadMoreMessages() {
        val query = _uiState.value.query
        if (query.isBlank()) return
        loadedMessagePages++
        viewModelScope.launch {
            val hits = messages.searchMessages(query, PAGE_SIZE * loadedMessagePages, 0)
            _uiState.value = _uiState.value.copy(messages = hits.map { it.toHit(query) })
        }
    }

    private suspend fun runSearch(query: String) {
        loadedMessagePages = 1
        _uiState.value = _uiState.value.copy(isSearching = true)

        val conversationResults = runCatching { conversations.search(query) }.getOrDefault(emptyList())
        val contactResults = runCatching { contactsDataSource.search(query, CONTACT_LIMIT) }
            .getOrDefault(emptyList())
        val messageRows = runCatching { messages.searchMessages(query, PAGE_SIZE, 0) }
            .getOrDefault(emptyList())
        val totalHits = runCatching { messages.countSearchMessages(query) }.getOrDefault(0)
        val attachmentResults = runCatching { messages.searchAttachments(query, ATTACHMENT_LIMIT) }
            .getOrDefault(emptyList())

        _uiState.value = SearchUiState(
            query = query,
            conversations = conversationResults,
            contacts = contactResults,
            messages = messageRows.map { it.toHit(query) },
            attachments = attachmentResults,
            totalMessageHits = totalHits,
            isSearching = false,
            hasSearched = true,
        )
    }

    private fun MessageSearchRow.toHit(query: String): MessageHit {
        val title = customTitle?.takeIf { it.isNotBlank() }
            ?: PhoneNumbers.splitRecipients(addresses)
                .joinToString(", ") { contactIndex.toRecipient(it).label }
                .ifBlank { address.orEmpty() }
        return MessageHit(
            messageId = messageId,
            threadId = threadId,
            conversationTitle = title,
            excerpt = SearchMatcher.excerpt(body.orEmpty(), query),
            timestamp = timestamp,
            isOutgoing = isOutgoing,
        )
    }

    private companion object {
        const val DEBOUNCE_MILLIS = 250L
        const val PAGE_SIZE = 25
        const val CONTACT_LIMIT = 8
        const val ATTACHMENT_LIMIT = 20
    }
}
