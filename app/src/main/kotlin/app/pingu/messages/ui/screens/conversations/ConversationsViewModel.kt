package app.pingu.messages.ui.screens.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pingu.messages.core.util.Avatars
import app.pingu.messages.data.local.dao.FolderWithCount
import app.pingu.messages.data.preferences.SettingsStore
import app.pingu.messages.data.repository.BlockedNumberRepository
import app.pingu.messages.data.repository.ConversationFilter
import app.pingu.messages.data.repository.ConversationRepository
import app.pingu.messages.data.repository.FolderRepository
import app.pingu.messages.data.repository.SyncRepository
import app.pingu.messages.domain.model.AppSettings
import app.pingu.messages.domain.model.BlockOrigin
import app.pingu.messages.domain.model.Conversation
import app.pingu.messages.domain.model.SwipeAction
import app.pingu.messages.platform.notification.MessageNotifier
import app.pingu.messages.platform.shortcut.ConversationShortcutManager
import app.pingu.messages.platform.system.DefaultSmsAppManager
import app.pingu.messages.platform.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A one-shot message for the UI to show in a snackbar. */
sealed interface ConversationsEvent {
    data class Undoable(val message: String, val undo: suspend () -> Unit) : ConversationsEvent
    data class Info(val message: String) : ConversationsEvent
}

data class ConversationsUiState(
    val conversations: List<Conversation> = emptyList(),
    val filter: ConversationFilter = ConversationFilter.INBOX,
    val selectedThreadIds: Set<Long> = emptySet(),
    val folders: List<FolderWithCount> = emptyList(),
    val activeFolderId: Long? = null,
    val archivedCount: Int = 0,
    val isDefaultSmsApp: Boolean = true,
    val isLoading: Boolean = true,
    val swipeRightAction: SwipeAction = SwipeAction.ARCHIVE,
    val swipeLeftAction: SwipeAction = SwipeAction.MARK_READ_UNREAD,
) {
    val selectionMode: Boolean get() = selectedThreadIds.isNotEmpty()

    val selectedConversations: List<Conversation>
        get() = conversations.filter { it.threadId in selectedThreadIds }

    val allSelectedArePinned: Boolean
        get() = selectedConversations.isNotEmpty() && selectedConversations.all { it.isPinned }

    val allSelectedAreMuted: Boolean
        get() = selectedConversations.isNotEmpty() && selectedConversations.all { it.isMuted }

    val allSelectedAreRead: Boolean
        get() = selectedConversations.isNotEmpty() && selectedConversations.none { it.hasUnread }
}

/**
 * The conversation list.
 *
 * The list itself is a database flow combined with the contact index, so it updates without any
 * manual refresh when a message arrives, a contact is saved or a thread is pinned. Syncing with the
 * telephony provider is separate and explicit, triggered on entry and on pull-to-refresh.
 */
@Suppress("TooManyFunctions")
class ConversationsViewModel(
    private val conversations: ConversationRepository,
    private val folders: FolderRepository,
    private val blocked: BlockedNumberRepository,
    private val sync: SyncRepository,
    private val settings: SettingsStore,
    private val defaultSmsApp: DefaultSmsAppManager,
    private val notifier: MessageNotifier,
    private val shortcuts: ConversationShortcutManager,
    private val widgets: WidgetUpdater,
) : ViewModel() {

    private val filter = MutableStateFlow(ConversationFilter.INBOX)
    private val folderId = MutableStateFlow<Long?>(null)
    private val selection = MutableStateFlow<Set<Long>>(emptySet())
    private val loading = MutableStateFlow(true)
    private val defaultApp = MutableStateFlow(defaultSmsApp.isDefault())

    private val _events = MutableStateFlow<ConversationsEvent?>(null)
    val events: StateFlow<ConversationsEvent?> = _events

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val conversationFlow = combine(filter, folderId) { current, folder -> current to folder }
        .flatMapLatest { (current, folder) -> conversations.observe(current, folder) }

    private val archivedCount = conversations.observe(ConversationFilter.ARCHIVED)
        .map { list -> list.count { it.hasUnread } }

    val uiState: StateFlow<ConversationsUiState> = combine(
        conversationFlow,
        filter,
        selection,
        settings.settings,
        combine(folders.observeFolders(), folderId, archivedCount, defaultApp, loading) {
            folderList, folder, archived, isDefault, isLoading ->
            FolderState(folderList, folder, archived, isDefault, isLoading)
        },
    ) { list, currentFilter, selected, currentSettings, folderState ->
        ConversationsUiState(
            conversations = list,
            filter = currentFilter,
            selectedThreadIds = selected.intersect(list.map { it.threadId }.toSet()),
            folders = folderState.folders,
            activeFolderId = folderState.activeFolderId,
            archivedCount = folderState.archivedUnread,
            isDefaultSmsApp = folderState.isDefaultSmsApp,
            isLoading = folderState.isLoading,
            swipeRightAction = currentSettings.swipeRightAction,
            swipeLeftAction = currentSettings.swipeLeftAction,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ConversationsUiState())

    private data class FolderState(
        val folders: List<FolderWithCount>,
        val activeFolderId: Long?,
        val archivedUnread: Int,
        val isDefaultSmsApp: Boolean,
        val isLoading: Boolean,
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            defaultApp.value = defaultSmsApp.isDefault()
            if (defaultApp.value) {
                loading.value = true
                runCatching { sync.syncAll() }
                runCatching {
                    shortcuts.publishRecent(conversations.recent(SHORTCUT_LIMIT))
                }
            }
            loading.value = false
        }
    }

    fun setFilter(newFilter: ConversationFilter) {
        filter.value = newFilter
        selection.value = emptySet()
    }

    fun setFolder(id: Long?) {
        folderId.value = id
        selection.value = emptySet()
    }

    fun toggleSelection(threadId: Long) {
        selection.value = selection.value.toMutableSet().apply {
            if (!add(threadId)) remove(threadId)
        }
    }

    fun selectAll() {
        selection.value = uiState.value.conversations.map { it.threadId }.toSet()
    }

    fun clearSelection() {
        selection.value = emptySet()
    }

    fun consumeEvent() {
        _events.value = null
    }

    // ---- Actions ------------------------------------------------------------------------------

    fun togglePin(threadIds: List<Long> = selectedIds()) {
        if (threadIds.isEmpty()) return
        val shouldPin = !uiState.value.allSelectedArePinned ||
            threadIds.any { id -> uiState.value.conversations.none { it.threadId == id && it.isPinned } }
        viewModelScope.launch {
            conversations.setPinned(threadIds, shouldPin)
            clearSelection()
            widgets.requestUpdate()
        }
    }

    fun setPinned(threadId: Long, pinned: Boolean) {
        viewModelScope.launch { conversations.setPinned(listOf(threadId), pinned) }
    }

    fun toggleMute(threadIds: List<Long> = selectedIds()) {
        if (threadIds.isEmpty()) return
        val shouldMute = !uiState.value.allSelectedAreMuted
        viewModelScope.launch {
            conversations.setMuted(threadIds, shouldMute)
            clearSelection()
        }
    }

    fun setMuted(threadId: Long, muted: Boolean) {
        viewModelScope.launch { conversations.setMuted(listOf(threadId), muted) }
    }

    fun archive(threadIds: List<Long> = selectedIds(), archived: Boolean = true, message: String) {
        if (threadIds.isEmpty()) return
        viewModelScope.launch {
            conversations.setArchived(threadIds, archived)
            clearSelection()
            widgets.requestUpdate()
            _events.value = ConversationsEvent.Undoable(message) {
                conversations.setArchived(threadIds, !archived)
            }
        }
    }

    fun markRead(threadIds: List<Long> = selectedIds()) {
        if (threadIds.isEmpty()) return
        viewModelScope.launch {
            threadIds.forEach {
                conversations.markRead(it)
                notifier.cancelConversation(it)
            }
            clearSelection()
            widgets.requestUpdate()
        }
    }

    fun markUnread(threadIds: List<Long> = selectedIds()) {
        if (threadIds.isEmpty()) return
        viewModelScope.launch {
            threadIds.forEach { conversations.markUnread(it) }
            clearSelection()
            widgets.requestUpdate()
        }
    }

    fun toggleReadState(threadId: Long) {
        val conversation = uiState.value.conversations.firstOrNull { it.threadId == threadId } ?: return
        if (conversation.hasUnread) markRead(listOf(threadId)) else markUnread(listOf(threadId))
    }

    /**
     * Deletes conversations from the device.
     *
     * There is no undo: the rows are removed from Android's message store, which no app can put
     * back. The UI therefore always confirms first rather than offering an undo that could not
     * work.
     */
    fun delete(threadIds: List<Long> = selectedIds(), message: String) {
        if (threadIds.isEmpty()) return
        viewModelScope.launch {
            conversations.delete(threadIds)
            threadIds.forEach(notifier::cancelConversation)
            shortcuts.remove(threadIds)
            clearSelection()
            widgets.requestUpdate()
            _events.value = ConversationsEvent.Info(message)
        }
    }

    fun block(threadIds: List<Long> = selectedIds(), reportSpam: Boolean, message: String) {
        if (threadIds.isEmpty()) return
        viewModelScope.launch {
            val targets = uiState.value.conversations.filter { it.threadId in threadIds }
            targets.forEach { conversation ->
                conversation.recipients.forEach { recipient ->
                    blocked.block(
                        address = recipient.address,
                        origin = if (reportSpam) BlockOrigin.REPORTED_SPAM else BlockOrigin.MANUAL,
                    )
                }
            }
            conversations.setBlocked(threadIds, true)
            if (reportSpam) conversations.setSpam(threadIds, true)
            threadIds.forEach(notifier::cancelConversation)
            clearSelection()
            _events.value = ConversationsEvent.Info(message)
        }
    }

    fun unblock(threadIds: List<Long>, message: String) {
        if (threadIds.isEmpty()) return
        viewModelScope.launch {
            val targets = uiState.value.conversations.filter { it.threadId in threadIds }
            targets.forEach { conversation ->
                conversation.recipients.forEach { blocked.unblock(it.address) }
            }
            conversations.setBlocked(threadIds, false)
            conversations.setSpam(threadIds, false)
            clearSelection()
            _events.value = ConversationsEvent.Info(message)
        }
    }

    fun moveToFolder(
        threadIds: List<Long> = selectedIds(),
        targetFolderId: Long?,
        message: String? = null,
    ) {
        if (threadIds.isEmpty()) return
        viewModelScope.launch {
            conversations.setFolder(threadIds, targetFolderId)
            clearSelection()
            if (message != null) _events.value = ConversationsEvent.Info(message)
        }
    }

    /**
     * Creates a folder and, when threads are selected, moves them straight into it: creating a
     * folder from the move dialog and then having to pick it again would be a pointless second step.
     */
    fun createFolder(name: String, moveSelection: List<Long> = emptyList(), message: String? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = folders.create(trimmed, nextFolderColorSlot())
            if (moveSelection.isNotEmpty()) {
                conversations.setFolder(moveSelection, id)
                clearSelection()
            }
            if (message != null) _events.value = ConversationsEvent.Info(message)
        }
    }

    fun renameFolder(folder: FolderWithCount, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { folders.rename(folder, trimmed) }
    }

    /** Deletes a folder. The conversations in it return to the inbox; nothing is deleted with it. */
    fun deleteFolder(folder: FolderWithCount) {
        viewModelScope.launch {
            if (folderId.value == folder.id) folderId.value = null
            folders.delete(folder.id)
        }
    }

    /** Spreads new folders across the accent palette instead of making them all the same colour. */
    private fun nextFolderColorSlot(): Int =
        uiState.value.folders.size % Avatars.COLOR_SLOTS

    fun applySwipe(conversation: Conversation, action: SwipeAction, labels: SwipeLabels) {
        when (action) {
            SwipeAction.NONE -> Unit
            SwipeAction.ARCHIVE -> archive(
                listOf(conversation.threadId),
                archived = !conversation.isArchived,
                message = if (conversation.isArchived) labels.unarchived else labels.archived,
            )

            SwipeAction.DELETE -> _events.value = ConversationsEvent.Info(labels.deleteNeedsConfirmation)
            SwipeAction.MARK_READ_UNREAD -> toggleReadState(conversation.threadId)
            SwipeAction.PIN -> setPinned(conversation.threadId, !conversation.isPinned)
            SwipeAction.MUTE -> setMuted(conversation.threadId, !conversation.isMuted)
        }
    }

    private fun selectedIds(): List<Long> = uiState.value.selectedThreadIds.toList()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val SHORTCUT_LIMIT = 8
    }
}

/** Localised strings the view model needs for snackbars, passed in from the composable. */
data class SwipeLabels(
    val archived: String,
    val unarchived: String,
    val deleteNeedsConfirmation: String,
)
