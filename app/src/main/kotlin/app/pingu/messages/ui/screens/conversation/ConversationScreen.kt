package app.pingu.messages.ui.screens.conversation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pingu.messages.R
import app.pingu.messages.core.text.TextEntity
import app.pingu.messages.core.text.TextEntityDetector
import app.pingu.messages.domain.model.Attachment
import app.pingu.messages.domain.model.Message
import app.pingu.messages.platform.permission.AppPermissions
import app.pingu.messages.platform.permission.PermissionGroup
import app.pingu.messages.ui.components.BlockedConversationBanner
import app.pingu.messages.ui.components.ConfirmDialog
import app.pingu.messages.ui.components.PermissionRationaleDialog
import app.pingu.messages.ui.components.ContactAvatar
import app.pingu.messages.ui.components.EmojiPicker
import app.pingu.messages.ui.components.EmptyState
import app.pingu.messages.ui.components.GroupAvatar
import app.pingu.messages.ui.components.InfoBanner
import app.pingu.messages.ui.components.MessageBubble
import app.pingu.messages.ui.components.MessageBubbleState
import app.pingu.messages.ui.components.rememberAudioPlaybackController
import app.pingu.messages.ui.util.IntentActions
import app.pingu.messages.ui.util.errorMessage
import app.pingu.messages.ui.util.rememberDownloadsSaver
import kotlinx.coroutines.launch

/**
 * The conversation screen.
 *
 * The list is drawn reversed so new messages appear at the bottom without the app having to measure
 * and scroll after every insertion, which is what makes an arriving message feel instant. Messages
 * are grouped by sender and time, so a burst reads as one block instead of five separate cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    onBack: () -> Unit,
    onOpenMedia: (Long, String) -> Unit,
    onForward: (List<Long>) -> Unit,
    onOpenConversationMedia: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val composer by viewModel.composer.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()
    val recordingElapsed by viewModel.recordingElapsed.collectAsStateWithLifecycle()
    val cancelArmed by viewModel.recordingCancelArmed.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val audio = rememberAudioPlaybackController()

    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<Message?>(null) }
    var detailsTarget by remember { mutableStateOf<Message?>(null) }
    var pendingMessageDeletion by remember { mutableStateOf<List<Long>?>(null) }
    var pendingConversationDeletion by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    val savedMessage = stringResource(R.string.media_viewer_saved)
    val saveFailedMessage = stringResource(R.string.media_viewer_save_failed)

    // ---- Activity results --------------------------------------------------------------------

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICKED_MEDIA),
    ) { uris ->
        uris.forEach { uri ->
            viewModel.addAttachment(uri, context.contentResolver.getType(uri))
        }
    }

    val pickDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.addAttachment(it, context.contentResolver.getType(it)) }
    }

    val pickContact = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact(),
    ) { uri ->
        uri?.let { viewModel.addAttachment(it, "text/x-vcard") }
    }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCaptureUri
        pendingCaptureUri = null
        if (success && uri != null) viewModel.addAttachment(uri, "image/jpeg")
    }

    val captureVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.CaptureVideo(),
    ) { success ->
        val uri = pendingCaptureUri
        pendingCaptureUri = null
        if (success && uri != null) viewModel.addAttachment(uri, "video/mp4")
    }

    val permissionDenied = stringResource(R.string.permission_denied_permanently)

    val requestMicrophone = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) {
            viewModel.startRecording()
        } else {
            viewModel.report(permissionDenied)
        }
    }

    val requestLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) {
            viewModel.attachLocation()
        } else {
            viewModel.report(permissionDenied)
        }
    }

    val permissions = remember(context) { AppPermissions(context) }
    var micRationale by remember { mutableStateOf(false) }
    var locationRationale by remember { mutableStateOf(false) }

    val saveAttachments = rememberDownloadsSaver { attachments ->
        attachments.forEach { viewModel.saveAttachment(it, savedMessage, saveFailedMessage) }
    }

    // ---- Events ------------------------------------------------------------------------------

    LaunchedEffect(event) {
        when (val current = event) {
            is ConversationEvent.Error ->
                snackbarHostState.showSnackbar(errorMessage(context, current.error))

            is ConversationEvent.Info -> snackbarHostState.showSnackbar(current.message)
            ConversationEvent.Sent -> listState.animateScrollToItem(0)
            null -> Unit
        }
        if (event != null) viewModel.consumeEvent()
    }

    LaunchedEffect(state.jumpToMessageId, state.messages.size) {
        val target = state.jumpToMessageId ?: return@LaunchedEffect
        val index = state.messages.indexOfFirst { it.id == target }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            viewModel.consumeJumpTarget()
        }
    }

    val conversation = state.conversation

    Scaffold(
        modifier = modifier,
        topBar = {
            if (state.selectionMode) {
                MessageSelectionBar(
                    count = state.selectedMessageIds.size,
                    onClear = viewModel::clearSelection,
                    onCopy = {
                        IntentActions.copyToClipboard(
                            context,
                            state.selectedMessages.joinToString("\n") { it.body.orEmpty() },
                        )
                        viewModel.clearSelection()
                    },
                    onForward = {
                        onForward(state.selectedMessageIds.toList())
                        viewModel.clearSelection()
                    },
                    onShare = {
                        state.selectedMessages.firstOrNull()?.let {
                            IntentActions.shareMessage(context, it)
                        }
                        viewModel.clearSelection()
                    },
                    onDelete = { pendingMessageDeletion = state.selectedMessageIds.toList() },
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                stringResource(R.string.action_back),
                            )
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (conversation != null) {
                                if (conversation.isGroup) {
                                    GroupAvatar(conversation.recipients, size = 34.dp)
                                } else {
                                    ContactAvatar(conversation.recipients.firstOrNull(), size = 34.dp)
                                }
                            }
                            Column(modifier = Modifier.padding(start = 10.dp)) {
                                Text(
                                    text = conversation?.title.orEmpty(),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (conversation != null && conversation.isGroup) {
                                    Text(
                                        text = pluralStringResource(
                                            R.plurals.group_conversation_participants,
                                            conversation.recipients.size,
                                            conversation.recipients.size,
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(),
                    actions = {
                        val single = conversation?.recipients?.singleOrNull()
                        if (single?.isDiallable == true) {
                            IconButton(onClick = { IntentActions.dial(context, single.address) }) {
                                Icon(Icons.Outlined.Call, stringResource(R.string.action_call))
                            }
                        }
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Outlined.MoreVert, stringResource(R.string.cd_more_options))
                        }
                        ConversationOverflowMenu(
                            expanded = showOverflow,
                            muted = conversation?.isMuted == true,
                            archived = conversation?.isArchived == true,
                            blocked = conversation?.isBlocked == true,
                            hasContact = conversation?.recipients?.firstOrNull()?.hasContact == true,
                            onDismiss = { showOverflow = false },
                            onMedia = {
                                showOverflow = false
                                conversation?.let { onOpenConversationMedia(it.threadId) }
                            },
                            onMute = {
                                showOverflow = false
                                viewModel.setMuted(conversation?.isMuted != true)
                            },
                            onArchive = {
                                showOverflow = false
                                viewModel.setArchived(conversation?.isArchived != true)
                            },
                            onBlock = {
                                showOverflow = false
                                if (conversation?.isBlocked == true) viewModel.unblock() else viewModel.block(false)
                            },
                            onRename = {
                                showOverflow = false
                                renaming = true
                            },
                            onAddContact = {
                                showOverflow = false
                                conversation?.recipients?.firstOrNull()?.let {
                                    IntentActions.addContact(context, it.address)
                                }
                            },
                            onViewContact = {
                                showOverflow = false
                                conversation?.recipients?.firstOrNull()?.contactId?.let {
                                    IntentActions.viewContact(context, it)
                                }
                            },
                            onDelete = {
                                showOverflow = false
                                pendingConversationDeletion = true
                            },
                        )
                    },
                )
            }
        },
        bottomBar = {
            Column(modifier = Modifier.imePadding().navigationBarsPadding()) {
                if (showEmojiPicker) {
                    EmojiPicker(
                        onEmojiSelected = { emoji ->
                            viewModel.onTextChanged(composer.text + emoji)
                        },
                    )
                }
                MessageComposer(
                    state = composer,
                    sims = state.sims,
                    recordingElapsedMillis = recordingElapsed,
                    recordingCancelArmed = cancelArmed,
                    onTextChange = viewModel::onTextChanged,
                    onSubjectChange = viewModel::onSubjectChanged,
                    onSend = viewModel::send,
                    onSendLongPress = { showScheduleDialog = true },
                    onAttach = { showAttachmentSheet = true },
                    onCamera = {
                        val uri = viewModel.newCaptureUri(video = false)
                        pendingCaptureUri = uri
                        takePicture.launch(uri)
                    },
                    onEmoji = { showEmojiPicker = !showEmojiPicker },
                    onRemoveAttachment = viewModel::removeAttachment,
                    onCancelReply = viewModel::cancelReply,
                    onSelectSim = viewModel::setSubscription,
                    onRecordStart = {
                        if (permissions.isGranted(PermissionGroup.MICROPHONE)) {
                            viewModel.startRecording()
                        } else {
                            micRationale = true
                        }
                    },
                    onRecordFinish = viewModel::finishRecording,
                    onRecordCancel = viewModel::cancelRecording,
                    onRecordDrag = viewModel::onRecordingDrag,
                    enabled = conversation?.isBlocked != true,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
      Box(modifier = Modifier.padding(padding).fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (conversation?.isBlocked == true) {
                BlockedConversationBanner(onUnblock = viewModel::unblock)
            } else if (conversation?.isSpam == true) {
                InfoBanner(
                    text = stringResource(R.string.conversation_spam_banner),
                    actionLabel = stringResource(R.string.action_not_spam),
                    onAction = viewModel::unblock,
                )
            }

            if (state.scheduled.isNotEmpty()) {
                InfoBanner(
                    text = pluralStringResource(
                        R.plurals.conversation_scheduled_banner,
                        state.scheduled.size,
                        state.scheduled.size,
                    ),
                    icon = Icons.Outlined.Schedule,
                )
            }

            if (state.messages.isEmpty() && !state.isLoading) {
                EmptyState(
                    icon = Icons.Outlined.Image,
                    title = stringResource(R.string.empty_conversation_title),
                    body = stringResource(R.string.empty_conversation_body),
                )
            } else {
                MessageList(
                    state = state,
                    listState = listState,
                    audio = audio,
                    onMessageClick = { message ->
                        if (state.selectionMode) viewModel.toggleSelection(message.id)
                    },
                    onMessageLongClick = { message -> actionTarget = message },
                    onAttachmentClick = { message, attachment ->
                        if (attachment.kind.isVisualMedia) {
                            onOpenMedia(message.id, attachment.uri)
                        } else {
                            IntentActions.openAttachment(context, attachment)
                        }
                    },
                    onEntityClick = { entity ->
                        IntentActions.openUri(context, TextEntityDetector.toUri(entity))
                    },
                    onReplyPreviewClick = { message ->
                        message.replyToMessageId?.let(viewModel::jumpToMessage)
                    },
                    onRetry = viewModel::retry,
                    onDownload = viewModel::downloadMms,
                    onLoadOlder = viewModel::loadOlder,
                )
            }
        }

        // Scrolled back through a long thread, getting to the latest message should not mean
        // flicking all the way down again.
        val scrolledUp by remember {
            derivedStateOf { listState.firstVisibleItemIndex > SCROLL_TO_BOTTOM_THRESHOLD }
        }
        AnimatedVisibility(
            visible = scrolledUp && !state.selectionMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 88.dp),
        ) {
            SmallFloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowDownward,
                    contentDescription = stringResource(R.string.conversation_scroll_to_bottom),
                )
            }
        }
      }
    }

    // ---- Sheets and dialogs -------------------------------------------------------------------

    if (showAttachmentSheet) {
        AttachmentSheet(
            locationAvailable = true,
            onDismiss = { showAttachmentSheet = false },
            onSelect = { source ->
                showAttachmentSheet = false
                when (source) {
                    AttachmentSource.GALLERY -> pickMedia.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                    )

                    AttachmentSource.FILES -> pickDocument.launch(arrayOf("*/*"))
                    AttachmentSource.AUDIO -> pickDocument.launch(arrayOf("audio/*"))
                    AttachmentSource.CONTACT -> pickContact.launch(null)
                    AttachmentSource.CAMERA_PHOTO -> {
                        val uri = viewModel.newCaptureUri(video = false)
                        pendingCaptureUri = uri
                        takePicture.launch(uri)
                    }

                    AttachmentSource.CAMERA_VIDEO -> {
                        val uri = viewModel.newCaptureUri(video = true)
                        pendingCaptureUri = uri
                        captureVideo.launch(uri)
                    }

                    AttachmentSource.LOCATION ->
                        if (permissions.isPartiallyGranted(PermissionGroup.LOCATION)) {
                            viewModel.attachLocation()
                        } else {
                            locationRationale = true
                        }
                }
            },
        )
    }

    actionTarget?.let { message ->
        MessageActionsSheet(
            message = message,
            reactionsExplained = !state.settings.reactionTextFallback,
            onDismiss = { actionTarget = null },
            onReact = { emoji ->
                viewModel.react(message, emoji)
                actionTarget = null
            },
            onAction = { action ->
                actionTarget = null
                when (action) {
                    MessageAction.REPLY -> viewModel.startReply(message)
                    MessageAction.COPY -> IntentActions.copyToClipboard(context, message.body.orEmpty())
                    MessageAction.FORWARD -> onForward(listOf(message.id))
                    MessageAction.SHARE -> IntentActions.shareMessage(context, message)
                    MessageAction.SAVE_ATTACHMENT -> saveAttachments(message.attachments)

                    MessageAction.SELECT -> viewModel.toggleSelection(message.id)
                    MessageAction.DETAILS -> detailsTarget = message
                    MessageAction.RESEND -> viewModel.retry(message)
                    MessageAction.DELETE -> pendingMessageDeletion = listOf(message.id)
                    MessageAction.REACT -> Unit
                }
            },
        )
    }

    detailsTarget?.let { message ->
        MessageDetailsDialog(
            message = message,
            sims = state.sims,
            onDismiss = { detailsTarget = null },
        )
    }

    if (showScheduleDialog) {
        SchedulePickerDialog(
            exactAlarmsAvailable = viewModel.exactAlarmsAvailable,
            onRequestExactAlarms = {
                viewModel.exactAlarmSettingsIntent()?.let { IntentActions.openSettings(context, it) }
            },
            onSchedule = { millis ->
                showScheduleDialog = false
                viewModel.schedule(millis)
            },
            onDismiss = { showScheduleDialog = false },
        )
    }

    if (micRationale) {
        PermissionRationaleDialog(
            title = stringResource(R.string.permission_microphone_title),
            body = stringResource(R.string.permission_microphone_body),
            onContinue = {
                micRationale = false
                requestMicrophone.launch(PermissionGroup.MICROPHONE.permissions.toTypedArray())
            },
            onDismiss = { micRationale = false },
        )
    }

    if (locationRationale) {
        PermissionRationaleDialog(
            title = stringResource(R.string.permission_location_title),
            body = stringResource(R.string.permission_location_body),
            onContinue = {
                locationRationale = false
                requestLocation.launch(PermissionGroup.LOCATION.permissions.toTypedArray())
            },
            onDismiss = { locationRationale = false },
        )
    }

    pendingMessageDeletion?.let { ids ->
        val deleted = pluralStringResource(R.plurals.messages_deleted, ids.size, ids.size)
        ConfirmDialog(
            title = pluralStringResource(R.plurals.delete_message_title, ids.size, ids.size),
            body = stringResource(R.string.delete_message_body),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                viewModel.delete(ids, deleted)
                pendingMessageDeletion = null
            },
            onDismiss = { pendingMessageDeletion = null },
        )
    }

    if (pendingConversationDeletion) {
        ConfirmDialog(
            title = pluralStringResource(R.plurals.delete_conversation_title, 1, 1),
            body = stringResource(R.string.delete_conversation_body),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                pendingConversationDeletion = false
                viewModel.deleteConversation()
                onBack()
            },
            onDismiss = { pendingConversationDeletion = false },
        )
    }

    if (renaming) {
        RenameConversationDialog(
            currentTitle = conversation?.customTitle.orEmpty(),
            onConfirm = {
                viewModel.rename(it)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
}

@Composable
private fun ConversationOverflowMenu(
    expanded: Boolean,
    muted: Boolean,
    archived: Boolean,
    blocked: Boolean,
    hasContact: Boolean,
    onDismiss: () -> Unit,
    onMedia: () -> Unit,
    onMute: () -> Unit,
    onArchive: () -> Unit,
    onBlock: () -> Unit,
    onRename: () -> Unit,
    onAddContact: () -> Unit,
    onViewContact: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.conversation_menu_media)) },
            leadingIcon = { Icon(Icons.Outlined.Image, null) },
            onClick = onMedia,
        )
        DropdownMenuItem(
            text = {
                Text(stringResource(if (muted) R.string.action_unmute else R.string.action_mute))
            },
            leadingIcon = {
                Icon(if (muted) Icons.Outlined.Notifications else Icons.Outlined.NotificationsOff, null)
            },
            onClick = onMute,
        )
        DropdownMenuItem(
            text = {
                Text(stringResource(if (archived) R.string.action_unarchive else R.string.action_archive))
            },
            leadingIcon = { Icon(Icons.Outlined.Archive, null) },
            onClick = onArchive,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.conversation_menu_rename)) },
            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
            onClick = onRename,
        )
        if (hasContact) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_view_contact)) },
                leadingIcon = { Icon(Icons.Outlined.PersonAdd, null) },
                onClick = onViewContact,
            )
        } else {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_add_contact)) },
                leadingIcon = { Icon(Icons.Outlined.PersonAdd, null) },
                onClick = onAddContact,
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = {
                Text(stringResource(if (blocked) R.string.action_unblock else R.string.action_block))
            },
            leadingIcon = { Icon(Icons.Outlined.Block, null) },
            onClick = onBlock,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_delete)) },
            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
            onClick = onDelete,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageSelectionBar(
    count: Int,
    onClear: () -> Unit,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Outlined.Close, stringResource(R.string.selection_exit))
            }
        },
        title = { Text(pluralStringResource(R.plurals.selected_count, count, count)) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        actions = {
            IconButton(onClick = onCopy) {
                Icon(Icons.Outlined.ContentCopy, stringResource(R.string.action_copy))
            }
            IconButton(onClick = onForward) {
                Icon(
                    Icons.Outlined.Share,
                    stringResource(R.string.action_forward),
                )
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Outlined.Share, stringResource(R.string.action_share))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete))
            }
        },
    )
}

private const val MAX_PICKED_MEDIA = 10

/** How far back through a thread counts as "scrolled away from the latest message". */
private const val SCROLL_TO_BOTTOM_THRESHOLD = 4
