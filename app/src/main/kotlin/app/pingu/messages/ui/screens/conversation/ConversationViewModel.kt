package app.pingu.messages.ui.screens.conversation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pingu.messages.core.text.Tapbacks
import app.pingu.messages.data.preferences.SettingsStore
import app.pingu.messages.data.repository.BlockedNumberRepository
import app.pingu.messages.data.repository.ConversationRepository
import app.pingu.messages.data.repository.DraftRepository
import app.pingu.messages.data.repository.MessageRepository
import app.pingu.messages.data.repository.ScheduledMessageRepository
import app.pingu.messages.data.repository.SyncRepository
import app.pingu.messages.data.telephony.AttachmentMetadataReader
import app.pingu.messages.data.telephony.SimDataSource
import app.pingu.messages.domain.model.AppError
import app.pingu.messages.domain.model.AppSettings
import app.pingu.messages.domain.model.Attachment
import app.pingu.messages.domain.model.Conversation
import app.pingu.messages.domain.model.Draft
import app.pingu.messages.domain.model.Message
import app.pingu.messages.domain.model.MessageStatus
import app.pingu.messages.domain.model.Outcome
import app.pingu.messages.domain.model.ScheduledMessage
import app.pingu.messages.domain.model.SimCard
import app.pingu.messages.platform.media.LocationSharing
import app.pingu.messages.platform.media.StorageMaintenance
import app.pingu.messages.platform.media.VoiceRecorder
import app.pingu.messages.platform.messaging.MessageSender
import app.pingu.messages.platform.messaging.SendRequest
import app.pingu.messages.platform.mms.MmsReceiveCoordinator
import app.pingu.messages.platform.notification.MessageNotifier
import app.pingu.messages.platform.scheduling.ScheduledMessageScheduler
import app.pingu.messages.platform.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Composer contents, kept separate from the message list so typing does not recompose the list. */
data class ComposerState(
    val text: String = "",
    val subject: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val replyToMessageId: Long? = null,
    val replyToSnippet: String? = null,
    val subscriptionId: Int = -1,
    val isSending: Boolean = false,
) {
    val canSend: Boolean get() = text.isNotBlank() || attachments.isNotEmpty()
    val requiresMms: Boolean get() = attachments.isNotEmpty() || !subject.isNullOrBlank()
}

data class ConversationUiState(
    val conversation: Conversation? = null,
    val messages: List<Message> = emptyList(),
    val scheduled: List<ScheduledMessage> = emptyList(),
    val selectedMessageIds: Set<Long> = emptySet(),
    val sims: List<SimCard> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val hasMoreToLoad: Boolean = false,
    val isLoading: Boolean = true,
    val jumpToMessageId: Long? = null,
    val searchQuery: String = "",
) {
    val selectionMode: Boolean get() = selectedMessageIds.isNotEmpty()

    val selectedMessages: List<Message>
        get() = messages.filter { it.id in selectedMessageIds }
}

/** One-shot feedback for the conversation screen. */
sealed interface ConversationEvent {
    data class Error(val error: AppError) : ConversationEvent
    data class Info(val message: String) : ConversationEvent
    data object Sent : ConversationEvent
}

/**
 * A single conversation.
 *
 * Messages are loaded in a growing window rather than all at once, so opening a thread with years
 * of history costs one indexed query of the most recent few hundred rows. Scrolling to the top
 * enlarges the window; jumping to a search result enlarges it just enough to include the target.
 */
@Suppress("TooManyFunctions", "LongParameterList")
class ConversationViewModel(
    val threadId: Long,
    private val conversations: ConversationRepository,
    private val messages: MessageRepository,
    private val drafts: DraftRepository,
    private val scheduledMessages: ScheduledMessageRepository,
    private val scheduler: ScheduledMessageScheduler,
    private val blocked: BlockedNumberRepository,
    private val sender: MessageSender,
    private val sync: SyncRepository,
    private val settings: SettingsStore,
    private val sims: SimDataSource,
    private val notifier: MessageNotifier,
    private val widgets: WidgetUpdater,
    private val storage: StorageMaintenance,
    private val metadataReader: AttachmentMetadataReader,
    private val mmsCoordinator: MmsReceiveCoordinator,
    private val recorder: VoiceRecorder,
    private val location: LocationSharing,
) : ViewModel() {

    private val windowSize = MutableStateFlow(INITIAL_WINDOW)
    private val selection = MutableStateFlow<Set<Long>>(emptySet())
    private val loading = MutableStateFlow(true)
    private val jumpTarget = MutableStateFlow<Long?>(null)
    private val searchQuery = MutableStateFlow("")
    private val totalMessages = MutableStateFlow(0)
    private val simCards = MutableStateFlow<List<SimCard>>(emptyList())

    private val _composer = MutableStateFlow(ComposerState())
    val composer: StateFlow<ComposerState> = _composer

    private val _events = MutableStateFlow<ConversationEvent?>(null)
    val events: StateFlow<ConversationEvent?> = _events

    private val _recordingElapsed = MutableStateFlow<Long?>(null)

    /** Elapsed recording time, or null when nothing is being recorded. */
    val recordingElapsed: StateFlow<Long?> = _recordingElapsed

    private val _recordingCancelArmed = MutableStateFlow(false)

    /** True once the finger has travelled far enough that releasing will discard the recording. */
    val recordingCancelArmed: StateFlow<Boolean> = _recordingCancelArmed

    private var recordingTicker: kotlinx.coroutines.Job? = null

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val messageFlow = windowSize.flatMapLatest { size ->
        messages.observeWindow(threadId, size)
    }

    val uiState: StateFlow<ConversationUiState> = combine(
        conversations.observeConversation(threadId),
        messageFlow,
        scheduledMessages.observeForThread(threadId),
        settings.settings,
        combine(selection, loading, jumpTarget, searchQuery, simCards) {
            selected, isLoading, jump, query, cards ->
            ScreenState(selected, isLoading, jump, query, cards)
        },
    ) { conversation, list, scheduled, currentSettings, screen ->
        ConversationUiState(
            conversation = conversation,
            messages = list,
            scheduled = scheduled.filter { it.isPending },
            selectedMessageIds = screen.selected.intersect(list.map { it.id }.toSet()),
            sims = screen.sims,
            settings = currentSettings,
            hasMoreToLoad = list.size >= windowSize.value,
            isLoading = screen.isLoading,
            jumpToMessageId = screen.jumpTarget,
            searchQuery = screen.searchQuery,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), ConversationUiState())

    private data class ScreenState(
        val selected: Set<Long>,
        val isLoading: Boolean,
        val jumpTarget: Long?,
        val searchQuery: String,
        val sims: List<SimCard>,
    )

    init {
        viewModelScope.launch {
            simCards.value = runCatching { sims.availableSims() }.getOrDefault(emptyList())
            runCatching { sync.syncThread(threadId) }
            totalMessages.value = messages.countInThread(threadId)
            loadDraft()
            loading.value = false
            markRead()
        }
    }

    // ---- Reading ------------------------------------------------------------------------------

    fun markRead() {
        viewModelScope.launch {
            conversations.markRead(threadId)
            notifier.cancelConversation(threadId)
            widgets.requestUpdate()
        }
    }

    fun loadOlder() {
        val current = windowSize.value
        if (current >= totalMessages.value) return
        windowSize.value = current + WINDOW_INCREMENT
        viewModelScope.launch {
            runCatching { sync.syncThread(threadId, windowSize.value) }
            totalMessages.value = messages.countInThread(threadId)
        }
    }

    /** Grows the window so a specific message is loaded, then asks the UI to scroll to it. */
    fun jumpToMessage(messageId: Long) {
        viewModelScope.launch {
            val needed = messages.windowSizeToInclude(threadId, messageId)
            if (needed > windowSize.value) windowSize.value = needed
            jumpTarget.value = messageId
        }
    }

    fun consumeJumpTarget() {
        jumpTarget.value = null
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    // ---- Composer -----------------------------------------------------------------------------

    private suspend fun loadDraft() {
        val draft = drafts.get(threadId) ?: return
        _composer.value = ComposerState(
            text = draft.text,
            subject = draft.subject,
            attachments = draft.attachments,
            replyToMessageId = draft.replyToMessageId,
            replyToSnippet = draft.replyToSnippet,
            subscriptionId = draft.subscriptionId,
        )
    }

    fun onTextChanged(text: String) {
        _composer.value = _composer.value.copy(text = text)
    }

    fun onSubjectChanged(subject: String?) {
        _composer.value = _composer.value.copy(subject = subject)
    }

    fun setSubscription(subscriptionId: Int) {
        _composer.value = _composer.value.copy(subscriptionId = subscriptionId)
        viewModelScope.launch { conversations.setSubscription(threadId, subscriptionId) }
    }

    fun startReply(message: Message) {
        _composer.value = _composer.value.copy(
            replyToMessageId = message.id,
            replyToSnippet = message.body?.take(REPLY_SNIPPET_LENGTH)
                ?: message.attachments.firstOrNull()?.fileName,
        )
        clearSelection()
    }

    fun cancelReply() {
        _composer.value = _composer.value.copy(replyToMessageId = null, replyToSnippet = null)
    }

    /**
     * Adds a picked or captured file to the composer.
     *
     * The file is copied into the app's own cache first. A picker grant does not survive the
     * process being killed, and a draft does, so keeping only the original URI would leave the user
     * with a draft whose attachment can no longer be read.
     */
    fun addAttachment(uri: Uri, mimeTypeHint: String? = null, extra: String? = null) {
        viewModelScope.launch {
            val mimeType = mimeTypeHint ?: "application/octet-stream"
            val displayName = metadataReader.displayNameOf(uri)
            val copied = storage.materializeToContentUri(uri, extensionFor(mimeType)).getOrNull()
            if (copied == null) {
                _events.value = ConversationEvent.Error(AppError.AttachmentUnreadable(uri.toString()))
                return@launch
            }
            val info = metadataReader.inspect(Uri.parse(copied), mimeType)
            val attachment = Attachment(
                uri = copied,
                mimeType = mimeType,
                fileName = displayName,
                sizeBytes = info.sizeBytes,
                width = info.width,
                height = info.height,
                durationMillis = info.durationMillis,
                extra = extra,
            )
            _composer.value = _composer.value.copy(
                attachments = _composer.value.attachments + attachment,
            )
            saveDraft()
        }
    }

    fun removeAttachment(attachment: Attachment) {
        _composer.value = _composer.value.copy(
            attachments = _composer.value.attachments.filterNot { it.uri == attachment.uri },
        )
        viewModelScope.launch { saveDraft() }
    }

    fun saveDraft() {
        val state = _composer.value
        viewModelScope.launch {
            drafts.save(
                Draft(
                    threadId = threadId,
                    text = state.text,
                    subject = state.subject,
                    attachments = state.attachments,
                    replyToMessageId = state.replyToMessageId,
                    replyToSnippet = state.replyToSnippet,
                    subscriptionId = state.subscriptionId,
                ),
            )
        }
    }

    // ---- Sending ------------------------------------------------------------------------------

    fun send() {
        val state = _composer.value
        val conversation = uiState.value.conversation ?: return
        if (!state.canSend || state.isSending) return

        _composer.value = state.copy(isSending = true)
        viewModelScope.launch {
            val outcome = sender.send(
                SendRequest(
                    threadId = threadId,
                    recipients = conversation.recipients.map { it.address },
                    body = state.text,
                    subject = state.subject,
                    attachments = state.attachments,
                    subscriptionId = state.subscriptionId,
                    replyToMessageId = state.replyToMessageId,
                    replyToSnippet = state.replyToSnippet,
                ),
            )
            when (outcome) {
                is Outcome.Success -> {
                    _composer.value = ComposerState(subscriptionId = state.subscriptionId)
                    drafts.clear(threadId)
                    totalMessages.value = messages.countInThread(threadId)
                    _events.value = ConversationEvent.Sent
                    widgets.requestUpdate()
                }

                is Outcome.Failure -> {
                    _composer.value = state.copy(isSending = false)
                    _events.value = ConversationEvent.Error(outcome.error)
                }
            }
        }
    }

    fun schedule(sendAtMillis: Long) {
        val state = _composer.value
        val conversation = uiState.value.conversation ?: return
        if (!state.canSend) return

        viewModelScope.launch {
            val id = scheduledMessages.schedule(
                ScheduledMessage(
                    threadId = threadId,
                    recipients = conversation.recipients.map { it.address },
                    body = state.text,
                    subject = state.subject,
                    attachments = state.attachments,
                    scheduledAt = sendAtMillis,
                    subscriptionId = state.subscriptionId,
                ),
            )
            scheduler.schedule(id, sendAtMillis)
            _composer.value = ComposerState(subscriptionId = state.subscriptionId)
            drafts.clear(threadId)
        }
    }

    fun cancelScheduled(message: ScheduledMessage) {
        viewModelScope.launch {
            scheduler.cancel(message.id)
            scheduledMessages.cancel(message.id)
        }
    }

    fun retry(message: Message) {
        val conversation = uiState.value.conversation ?: return
        viewModelScope.launch {
            messages.setStatus(message.id, MessageStatus.SENDING)
            val outcome = sender.retry(
                SendRequest(
                    threadId = threadId,
                    recipients = conversation.recipients.map { it.address },
                    body = message.body.orEmpty(),
                    subject = message.subject,
                    attachments = message.attachments,
                    subscriptionId = message.subscriptionId,
                ),
            )
            if (outcome is Outcome.Failure) {
                messages.setStatus(message.id, MessageStatus.FAILED)
                _events.value = ConversationEvent.Error(outcome.error)
            } else {
                messages.delete(listOf(message.id))
            }
        }
    }

    /** Fetches an MMS whose notification arrived but whose body was not downloaded. */
    fun downloadMms(message: Message) {
        viewModelScope.launch {
            val row = messages.getMessage(message.id) ?: return@launch
            val location = contentLocationFor(row)
            if (location.isNullOrBlank()) {
                _events.value = ConversationEvent.Error(AppError.Unexpected())
                return@launch
            }
            mmsCoordinator.startDownload(
                systemId = row.systemId,
                transactionId = null,
                contentLocation = location,
                subscriptionId = row.subscriptionId,
            )
        }
    }

    // ---- Voice messages -----------------------------------------------------------------------

    /**
     * Starts recording. The microphone is held only while the button is held, and the timer is
     * driven from the recorder's own clock rather than from a counter that could drift.
     */
    fun startRecording() {
        if (_recordingElapsed.value != null) return
        val started = recorder.start()
        if (started.isFailure) {
            _events.value = ConversationEvent.Error(AppError.Unexpected(started.exceptionOrNull()))
            return
        }
        _recordingCancelArmed.value = false
        _recordingElapsed.value = 0L
        recordingTicker = viewModelScope.launch {
            val state = started.getOrNull() ?: return@launch
            while (_recordingElapsed.value != null) {
                _recordingElapsed.value = state.elapsedMillis()
                kotlinx.coroutines.delay(RECORDING_TICK_MILLIS)
            }
        }
    }

    /** Stops recording and attaches the result, unless it was too short to be intentional. */
    fun finishRecording() {
        if (_recordingElapsed.value == null) return
        stopTicker()
        val file = recorder.stop().getOrNull()
        if (file == null) return
        val uri = storage.contentUriFor(file)
        viewModelScope.launch {
            val info = metadataReader.inspect(uri, VOICE_MIME_TYPE)
            val attachment = Attachment(
                uri = uri.toString(),
                mimeType = VOICE_MIME_TYPE,
                fileName = file.name,
                sizeBytes = info.sizeBytes,
                durationMillis = info.durationMillis,
                extra = Attachment.EXTRA_VOICE_MESSAGE,
            )
            _composer.value = _composer.value.copy(
                attachments = _composer.value.attachments + attachment,
            )
            send()
        }
    }

    fun cancelRecording() {
        if (_recordingElapsed.value == null) return
        stopTicker()
        recorder.cancel()
    }

    fun onRecordingDrag(horizontalTravelPx: Float) {
        _recordingCancelArmed.value = horizontalTravelPx < -RECORDING_CANCEL_ARM_PX
    }

    private fun stopTicker() {
        recordingTicker?.cancel()
        recordingTicker = null
        _recordingElapsed.value = null
        _recordingCancelArmed.value = false
    }

    /**
     * Attaches the user's current location as a map link.
     *
     * MMS has no structured location part, so the honest implementation is a link every phone can
     * open, including an iPhone. The position is read once and never stored.
     */
    fun attachLocation() {
        viewModelScope.launch {
            if (!location.hasPermission()) {
                _events.value = ConversationEvent.Error(
                    AppError.PermissionRequired(android.Manifest.permission.ACCESS_COARSE_LOCATION),
                )
                return@launch
            }
            if (!location.isLocationEnabled()) {
                _events.value = ConversationEvent.Error(AppError.Unexpected())
                return@launch
            }
            val fix = location.currentLocation()
            if (fix == null) {
                _events.value = ConversationEvent.Error(AppError.Unexpected())
                return@launch
            }
            val link = location.formatShareText(fix)
            val text = _composer.value.text
            _composer.value = _composer.value.copy(
                text = if (text.isBlank()) link else "$text\n$link",
            )
            saveDraft()
        }
    }

    /** True when the platform will fire a scheduled message at exactly the chosen minute. */
    val exactAlarmsAvailable: Boolean get() = scheduler.canScheduleExactAlarms()

    /** Opens the system screen where exact alarms can be granted, when one exists. */
    fun exactAlarmSettingsIntent(): android.content.Intent? = scheduler.exactAlarmSettingsIntent()

    /** A content URI the system camera app can write a capture into. */
    fun newCaptureUri(video: Boolean): android.net.Uri =
        storage.newCaptureUri(if (video) ".mp4" else ".jpg")

    // ---- Message actions ----------------------------------------------------------------------

    fun toggleSelection(messageId: Long) {
        selection.value = selection.value.toMutableSet().apply {
            if (!add(messageId)) remove(messageId)
        }
    }

    fun clearSelection() {
        selection.value = emptySet()
    }

    fun deleteSelected() {
        val ids = selection.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            messages.delete(ids)
            clearSelection()
            totalMessages.value = messages.countInThread(threadId)
            widgets.requestUpdate()
        }
    }

    /**
     * Deletes messages from this phone.
     *
     * [confirmation] is passed in already localised because the plural depends on the count and the
     * view model has no resources of its own.
     */
    fun delete(messageIds: List<Long>, confirmation: String? = null) {
        if (messageIds.isEmpty()) return
        viewModelScope.launch {
            messages.delete(messageIds)
            clearSelection()
            totalMessages.value = messages.countInThread(threadId)
            if (confirmation != null) _events.value = ConversationEvent.Info(confirmation)
        }
    }

    /**
     * Applies a reaction.
     *
     * The reaction is stored locally, always. When the user has enabled the text fallback it is
     * also sent as the plain-text tapback other messengers understand, and only then is it marked
     * as transmitted - so the UI never suggests the other person saw something they did not.
     */
    fun react(message: Message, emoji: String) {
        viewModelScope.launch {
            val existing = message.reactions.firstOrNull { it.isFromMe }
            val removing = existing?.emoji == emoji
            val current = settings.settings.first()
            val conversation = uiState.value.conversation

            messages.setOwnReaction(
                messageId = message.id,
                emoji = if (removing) null else emoji,
                transmitted = false,
            )

            if (current.reactionTextFallback && conversation != null) {
                val quoted = message.body.orEmpty().ifBlank {
                    message.attachments.firstOrNull()?.fileName.orEmpty()
                }
                val body = if (removing) {
                    Tapbacks.formatRemoval(emoji, quoted)
                } else {
                    Tapbacks.format(emoji, quoted)
                }
                val outcome = sender.send(
                    SendRequest(
                        threadId = threadId,
                        recipients = conversation.recipients.map { it.address },
                        body = body,
                        subscriptionId = conversation.subscriptionId,
                        isReactionFallback = true,
                    ),
                )
                if (outcome is Outcome.Success && !removing) {
                    messages.setOwnReaction(message.id, emoji, transmitted = true)
                }
            }
        }
    }

    // ---- Conversation actions -----------------------------------------------------------------

    fun setMuted(muted: Boolean) {
        viewModelScope.launch { conversations.setMuted(listOf(threadId), muted) }
    }

    fun setArchived(archived: Boolean) {
        viewModelScope.launch { conversations.setArchived(listOf(threadId), archived) }
    }

    fun setPinned(pinned: Boolean) {
        viewModelScope.launch { conversations.setPinned(listOf(threadId), pinned) }
    }

    fun rename(title: String?) {
        viewModelScope.launch { conversations.setCustomTitle(threadId, title) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { conversations.setNotificationsEnabled(threadId, enabled) }
    }

    fun block(reportSpam: Boolean) {
        val conversation = uiState.value.conversation ?: return
        viewModelScope.launch {
            conversation.recipients.forEach { blocked.block(it.address) }
            conversations.setBlocked(listOf(threadId), true)
            if (reportSpam) conversations.setSpam(listOf(threadId), true)
            notifier.cancelConversation(threadId)
        }
    }

    fun unblock() {
        val conversation = uiState.value.conversation ?: return
        viewModelScope.launch {
            conversation.recipients.forEach { blocked.unblock(it.address) }
            conversations.setBlocked(listOf(threadId), false)
            conversations.setSpam(listOf(threadId), false)
        }
    }

    fun deleteConversation() {
        viewModelScope.launch {
            conversations.delete(listOf(threadId))
            notifier.cancelConversation(threadId)
            widgets.requestUpdate()
        }
    }

    fun saveAttachment(attachment: Attachment, successMessage: String, failureMessage: String) {
        viewModelScope.launch {
            val result = storage.saveToDownloads(attachment)
            _events.value = ConversationEvent.Info(
                if (result.isSuccess) successMessage else failureMessage,
            )
        }
    }

    /** Surfaces a message the screen produced, so everything reaches the user through one snackbar. */
    fun report(message: String) {
        _events.value = ConversationEvent.Info(message)
    }

    fun consumeEvent() {
        _events.value = null
    }

    override fun onCleared() {
        recorder.cancel()
        // The draft is written on every pause, but a final save catches text typed in the last
        // fraction of a second before the screen went away.
        saveDraft()
        super.onCleared()
    }

    private suspend fun contentLocationFor(message: Message): String? =
        messages.contentLocationOf(message.id)

    private fun extensionFor(mimeType: String): String = when {
        mimeType.startsWith("image/jpeg") -> ".jpg"
        mimeType.startsWith("image/png") -> ".png"
        mimeType.startsWith("image/gif") -> ".gif"
        mimeType.startsWith("image/webp") -> ".webp"
        mimeType.startsWith("video/mp4") -> ".mp4"
        mimeType.startsWith("video/") -> ".3gp"
        mimeType.startsWith("audio/mp4") || mimeType.startsWith("audio/m4a") -> ".m4a"
        mimeType.startsWith("audio/") -> ".m4a"
        mimeType.contains("vcard") -> ".vcf"
        mimeType == "application/pdf" -> ".pdf"
        else -> ".bin"
    }

    private companion object {
        const val INITIAL_WINDOW = 60
        const val WINDOW_INCREMENT = 120
        const val REPLY_SNIPPET_LENGTH = 160
        const val STOP_TIMEOUT = 5_000L
        const val RECORDING_TICK_MILLIS = 100L
        const val RECORDING_CANCEL_ARM_PX = 140f
        const val VOICE_MIME_TYPE = "audio/mp4"
    }
}
