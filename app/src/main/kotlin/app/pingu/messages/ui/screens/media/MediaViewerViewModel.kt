package app.pingu.messages.ui.screens.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pingu.messages.data.repository.MessageRepository
import app.pingu.messages.domain.model.Attachment
import app.pingu.messages.platform.media.StorageMaintenance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MediaViewerUiState(
    val attachments: List<Attachment> = emptyList(),
    val initialIndex: Int = 0,
    val messageId: Long = 0L,
    val message: String? = null,
)

/**
 * Backs the media viewer.
 *
 * Loads every visual attachment in the thread so the pager can move through them, and remembers
 * which one was tapped so the viewer opens on it rather than at the start.
 */
class MediaViewerViewModel(
    private val threadId: Long,
    private val messageId: Long,
    private val initialUri: String,
    private val messages: MessageRepository,
    private val storage: StorageMaintenance,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaViewerUiState(messageId = messageId))
    val uiState: StateFlow<MediaViewerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val all = messages.attachmentsInThread(threadId)
                .filter { it.kind.isVisualMedia }
                .sortedBy { it.messageId }
            val index = all.indexOfFirst { it.uri == initialUri }.coerceAtLeast(0)
            _uiState.value = _uiState.value.copy(attachments = all, initialIndex = index)
        }
    }

    fun save(attachment: Attachment, successMessage: String, failureMessage: String) {
        viewModelScope.launch {
            val result = storage.saveToDownloads(attachment)
            _uiState.value = _uiState.value.copy(
                message = if (result.isSuccess) successMessage else failureMessage,
            )
        }
    }

    fun deleteCurrentMessage() {
        viewModelScope.launch { messages.delete(listOf(messageId)) }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
