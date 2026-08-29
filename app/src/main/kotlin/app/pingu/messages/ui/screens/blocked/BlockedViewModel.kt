package app.pingu.messages.ui.screens.blocked

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pingu.messages.data.local.entity.SpamKeywordEntity
import app.pingu.messages.data.repository.BlockedNumberRepository
import app.pingu.messages.data.repository.ConversationFilter
import app.pingu.messages.data.repository.ConversationRepository
import app.pingu.messages.domain.model.BlockedNumber
import app.pingu.messages.domain.model.Conversation
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BlockedUiState(
    val blocked: List<BlockedNumber> = emptyList(),
    val spamConversations: List<Conversation> = emptyList(),
    val keywords: List<SpamKeywordEntity> = emptyList(),
    val systemBlockingAvailable: Boolean = false,
)

/**
 * Blocked numbers and spam.
 *
 * Two lists that people think of as one screen: the numbers they blocked, and the conversations the
 * spam filter moved aside. Unblocking from here restores the conversation to the inbox, which is
 * the only way back that does not require the user to find the thread again.
 */
class BlockedViewModel(
    private val blocked: BlockedNumberRepository,
    private val conversations: ConversationRepository,
) : ViewModel() {

    val uiState: StateFlow<BlockedUiState> = combine(
        blocked.observeBlocked(),
        conversations.observe(ConversationFilter.BLOCKED_AND_SPAM),
        blocked.observeSpamKeywords(),
    ) { blockedNumbers, spam, keywords ->
        BlockedUiState(
            blocked = blockedNumbers,
            spamConversations = spam,
            keywords = keywords,
            systemBlockingAvailable = blocked.canUseSystemBlockList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), BlockedUiState())

    init {
        viewModelScope.launch { runCatching { blocked.importSystemBlockList() } }
    }

    fun block(address: String) {
        viewModelScope.launch { blocked.block(address) }
    }

    fun unblock(address: String) {
        viewModelScope.launch {
            blocked.unblock(address)
            val affected = uiState.value.spamConversations
                .filter { conversation ->
                    conversation.recipients.any { it.address == address }
                }
                .map { it.threadId }
            if (affected.isNotEmpty()) {
                conversations.setBlocked(affected, false)
                conversations.setSpam(affected, false)
            }
        }
    }

    fun restoreFromSpam(threadId: Long) {
        viewModelScope.launch {
            conversations.setSpam(listOf(threadId), false)
            conversations.setBlocked(listOf(threadId), false)
            conversations.setArchived(listOf(threadId), false)
        }
    }

    fun addKeyword(keyword: String) {
        viewModelScope.launch { blocked.addSpamKeyword(keyword) }
    }

    fun removeKeyword(id: Long) {
        viewModelScope.launch { blocked.removeSpamKeyword(id) }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
