package app.pingu.messages.ui.screens.conversations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pingu.messages.R
import app.pingu.messages.data.local.dao.FolderWithCount
import app.pingu.messages.data.repository.ConversationFilter
import app.pingu.messages.domain.model.Conversation
import app.pingu.messages.ui.components.ConfirmDialog
import app.pingu.messages.ui.components.ConversationListItem
import app.pingu.messages.ui.components.DefaultSmsAppBanner
import app.pingu.messages.ui.components.EmptyState
import app.pingu.messages.ui.components.SwipeableConversationItem
import kotlinx.coroutines.launch

/**
 * The conversation list: the app's home screen.
 *
 * Three lists share this screen - the inbox, the archive and spam - because the row, the swipe
 * gestures and the multi-select bar are identical in all three, and a user who learns them once
 * should not have to learn them again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    viewModel: ConversationsViewModel,
    onOpenConversation: (Long) -> Unit,
    onCompose: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onScheduled: () -> Unit,
    onBlocked: () -> Unit,
    onRequestDefaultSmsApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var menuExpanded by remember { mutableStateOf(false) }
    var pendingDeletion by remember { mutableStateOf<List<Long>?>(null) }
    var showMoveToFolder by remember { mutableStateOf(false) }
    var showManageFolders by remember { mutableStateOf(false) }
    var folderBeingRenamed by remember { mutableStateOf<FolderWithCount?>(null) }
    var folderBeingDeleted by remember { mutableStateOf<FolderWithCount?>(null) }
    var creatingFolder by remember { mutableStateOf(false) }
    var pendingBlock by remember { mutableStateOf<List<Long>?>(null) }

    val undoLabel = stringResource(R.string.action_undo)
    val archivedMessage = stringResource(R.string.action_archive)
    val unarchivedMessage = stringResource(R.string.action_unarchive)
    val deleteConfirmMessage = stringResource(R.string.action_delete)

    LaunchedEffect(event) {
        val current = event ?: return@LaunchedEffect
        when (current) {
            is ConversationsEvent.Info -> snackbarHostState.showSnackbar(current.message)
            is ConversationsEvent.Undoable -> {
                val result = snackbarHostState.showSnackbar(
                    message = current.message,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) current.undo()
            }
        }
        viewModel.consumeEvent()
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (state.selectionMode) {
                SelectionTopBar(
                    count = state.selectedThreadIds.size,
                    allPinned = state.allSelectedArePinned,
                    allMuted = state.allSelectedAreMuted,
                    allRead = state.allSelectedAreRead,
                    isArchiveList = state.filter == ConversationFilter.ARCHIVED,
                    onClear = viewModel::clearSelection,
                    onPin = { viewModel.togglePin() },
                    onMute = { viewModel.toggleMute() },
                    onArchive = {
                        viewModel.archive(
                            archived = state.filter != ConversationFilter.ARCHIVED,
                            message = if (state.filter == ConversationFilter.ARCHIVED) {
                                unarchivedMessage
                            } else {
                                archivedMessage
                            },
                        )
                    },
                    onToggleRead = {
                        if (state.allSelectedAreRead) viewModel.markUnread() else viewModel.markRead()
                    },
                    onDelete = { pendingDeletion = state.selectedThreadIds.toList() },
                    onBlock = { pendingBlock = state.selectedThreadIds.toList() },
                    onSelectAll = viewModel::selectAll,
                    onMoveToFolder = { showMoveToFolder = true },
                )
            } else {
                TopAppBar(
                    title = { Text(titleFor(state.filter)) },
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(onClick = onSearch) {
                            Icon(Icons.Outlined.Search, stringResource(R.string.action_search))
                        }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Outlined.MoreVert, stringResource(R.string.cd_more_options))
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.scheduled_messages_title)) },
                                leadingIcon = { Icon(Icons.Outlined.Schedule, null) },
                                onClick = {
                                    menuExpanded = false
                                    onScheduled()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.blocked_numbers_title)) },
                                leadingIcon = { Icon(Icons.Outlined.Block, null) },
                                onClick = {
                                    menuExpanded = false
                                    onBlocked()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.settings_folders)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, null) },
                                onClick = {
                                    menuExpanded = false
                                    showManageFolders = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_settings)) },
                                leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                                onClick = {
                                    menuExpanded = false
                                    onSettings()
                                },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_retry)) },
                                leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.refresh()
                                },
                            )
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            AnimatedVisibility(visible = !state.selectionMode) {
                ExtendedFloatingActionButton(
                    onClick = onCompose,
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text(stringResource(R.string.action_new_message)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (!state.isDefaultSmsApp) {
                DefaultSmsAppBanner(onAction = onRequestDefaultSmsApp)
            }

            FilterRow(
                filter = state.filter,
                onFilterChange = viewModel::setFilter,
                folders = state.folders,
                activeFolderId = state.activeFolderId,
                onFolderChange = viewModel::setFolder,
            )

            if (state.conversations.isEmpty() && !state.isLoading) {
                EmptyStateFor(state.filter, onCompose)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    items(
                        items = state.conversations,
                        key = { it.threadId },
                    ) { conversation ->
                        SwipeableConversationItem(
                            conversation = conversation,
                            startAction = state.swipeRightAction,
                            endAction = state.swipeLeftAction,
                            onAction = { action ->
                                viewModel.applySwipe(
                                    conversation = conversation,
                                    action = action,
                                    labels = SwipeLabels(
                                        archived = archivedMessage,
                                        unarchived = unarchivedMessage,
                                        deleteNeedsConfirmation = deleteConfirmMessage,
                                    ),
                                )
                                if (action == app.pingu.messages.domain.model.SwipeAction.DELETE) {
                                    pendingDeletion = listOf(conversation.threadId)
                                }
                            },
                        ) {
                            ConversationListItem(
                                conversation = conversation,
                                selected = conversation.threadId in state.selectedThreadIds,
                                selectionMode = state.selectionMode,
                                onClick = {
                                    if (state.selectionMode) {
                                        viewModel.toggleSelection(conversation.threadId)
                                    } else {
                                        onOpenConversation(conversation.threadId)
                                    }
                                },
                                onLongClick = { viewModel.toggleSelection(conversation.threadId) },
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDeletion?.let { threadIds ->
        val message = pluralStringResource(R.plurals.conversations_deleted, threadIds.size, threadIds.size)
        ConfirmDialog(
            title = pluralStringResource(R.plurals.delete_conversation_title, threadIds.size, threadIds.size),
            body = stringResource(R.string.delete_conversation_body),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                viewModel.delete(threadIds, message)
                pendingDeletion = null
            },
            onDismiss = { pendingDeletion = null },
        )
    }

    pendingBlock?.let { threadIds ->
        val names = state.conversations
            .filter { it.threadId in threadIds }
            .joinToString(", ") { it.title }
        val message = stringResource(R.string.blocked_added, names)
        ConfirmDialog(
            title = stringResource(R.string.block_dialog_title, names),
            body = stringResource(R.string.block_dialog_body),
            confirmLabel = stringResource(R.string.action_block),
            destructive = true,
            onConfirm = {
                viewModel.block(threadIds, reportSpam = false, message = message)
                pendingBlock = null
            },
            onDismiss = { pendingBlock = null },
        )
    }

    if (showMoveToFolder) {
        val moved = stringResource(R.string.folder_moved)
        val targets = state.selectedThreadIds.toList()
        MoveToFolderDialog(
            folders = state.folders,
            onMove = { folderId ->
                showMoveToFolder = false
                viewModel.moveToFolder(targets, folderId, moved)
            },
            onCreateFolder = {
                showMoveToFolder = false
                creatingFolder = true
            },
            onDismiss = { showMoveToFolder = false },
        )
    }

    if (showManageFolders) {
        ManageFoldersDialog(
            folders = state.folders,
            onCreateFolder = {
                showManageFolders = false
                creatingFolder = true
            },
            onRenameFolder = {
                showManageFolders = false
                folderBeingRenamed = it
            },
            onDeleteFolder = {
                showManageFolders = false
                folderBeingDeleted = it
            },
            onDismiss = { showManageFolders = false },
        )
    }

    if (creatingFolder) {
        // Whatever was selected when the dialog opened moves into the new folder straight away.
        val created = stringResource(R.string.folder_created)
        val targets = state.selectedThreadIds.toList()
        FolderNameDialog(
            title = stringResource(R.string.folder_new),
            initialName = "",
            onConfirm = { name ->
                creatingFolder = false
                viewModel.createFolder(name, targets, created)
            },
            onDismiss = { creatingFolder = false },
        )
    }

    folderBeingRenamed?.let { folder ->
        FolderNameDialog(
            title = stringResource(R.string.folder_rename),
            initialName = folder.name,
            onConfirm = { name ->
                folderBeingRenamed = null
                viewModel.renameFolder(folder, name)
            },
            onDismiss = { folderBeingRenamed = null },
        )
    }

    folderBeingDeleted?.let { folder ->
        ConfirmDialog(
            title = stringResource(R.string.folder_delete_title),
            body = stringResource(R.string.folder_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                viewModel.deleteFolder(folder)
                folderBeingDeleted = null
            },
            onDismiss = { folderBeingDeleted = null },
        )
    }

    LaunchedEffect(state.filter) {
        scope.launch { listState.scrollToItem(0) }
    }
}

@Composable
private fun titleFor(filter: ConversationFilter): String = when (filter) {
    ConversationFilter.INBOX -> stringResource(R.string.conversations_title)
    ConversationFilter.ARCHIVED -> stringResource(R.string.conversations_archived)
    ConversationFilter.BLOCKED_AND_SPAM -> stringResource(R.string.conversations_spam)
}

@Composable
private fun EmptyStateFor(filter: ConversationFilter, onCompose: () -> Unit) {
    when (filter) {
        ConversationFilter.INBOX -> EmptyState(
            icon = Icons.Outlined.Inbox,
            title = stringResource(R.string.empty_conversations_title),
            body = stringResource(R.string.empty_conversations_body),
            actionLabel = stringResource(R.string.empty_conversations_action),
            onAction = onCompose,
        )

        ConversationFilter.ARCHIVED -> EmptyState(
            icon = Icons.Outlined.Archive,
            title = stringResource(R.string.empty_archived_title),
            body = stringResource(R.string.empty_archived_body),
        )

        ConversationFilter.BLOCKED_AND_SPAM -> EmptyState(
            icon = Icons.Outlined.Block,
            title = stringResource(R.string.empty_blocked_title),
            body = stringResource(R.string.empty_blocked_body),
        )
    }
}

@Composable
private fun FilterRow(
    filter: ConversationFilter,
    onFilterChange: (ConversationFilter) -> Unit,
    folders: List<app.pingu.messages.data.local.dao.FolderWithCount>,
    activeFolderId: Long?,
    onFolderChange: (Long?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = filter == ConversationFilter.INBOX && activeFolderId == null,
            onClick = {
                onFolderChange(null)
                onFilterChange(ConversationFilter.INBOX)
            },
            label = { Text(stringResource(R.string.conversations_title)) },
        )
        FilterChip(
            selected = filter == ConversationFilter.ARCHIVED,
            onClick = {
                onFolderChange(null)
                onFilterChange(ConversationFilter.ARCHIVED)
            },
            label = { Text(stringResource(R.string.conversations_archived)) },
        )
        FilterChip(
            selected = filter == ConversationFilter.BLOCKED_AND_SPAM,
            onClick = {
                onFolderChange(null)
                onFilterChange(ConversationFilter.BLOCKED_AND_SPAM)
            },
            label = { Text(stringResource(R.string.conversations_spam)) },
        )
        folders.forEach { folder ->
            FilterChip(
                selected = activeFolderId == folder.id,
                onClick = {
                    onFilterChange(ConversationFilter.INBOX)
                    onFolderChange(if (activeFolderId == folder.id) null else folder.id)
                },
                label = { Text(folder.name) },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, null) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    allPinned: Boolean,
    allMuted: Boolean,
    allRead: Boolean,
    isArchiveList: Boolean,
    onClear: () -> Unit,
    onPin: () -> Unit,
    onMute: () -> Unit,
    onArchive: () -> Unit,
    onToggleRead: () -> Unit,
    onDelete: () -> Unit,
    onBlock: () -> Unit,
    onSelectAll: () -> Unit,
    onMoveToFolder: () -> Unit,
) {
    var overflow by remember { mutableStateOf(false) }
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
            IconButton(onClick = onPin) {
                Icon(
                    Icons.Outlined.PushPin,
                    stringResource(if (allPinned) R.string.action_unpin else R.string.action_pin),
                )
            }
            IconButton(onClick = onToggleRead) {
                Icon(
                    if (allRead) Icons.Outlined.MarkEmailUnread else Icons.Outlined.MarkEmailRead,
                    stringResource(if (allRead) R.string.action_mark_unread else R.string.action_mark_read),
                )
            }
            IconButton(onClick = onArchive) {
                Icon(
                    if (isArchiveList) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                    stringResource(if (isArchiveList) R.string.action_unarchive else R.string.action_archive),
                )
            }
            IconButton(onClick = { overflow = true }) {
                Icon(Icons.Outlined.MoreVert, stringResource(R.string.cd_more_options))
            }
            DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                DropdownMenuItem(
                    text = {
                        Text(stringResource(if (allMuted) R.string.action_unmute else R.string.action_mute))
                    },
                    leadingIcon = { Icon(Icons.Outlined.NotificationsOff, null) },
                    onClick = {
                        overflow = false
                        onMute()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_block)) },
                    leadingIcon = { Icon(Icons.Outlined.Block, null) },
                    onClick = {
                        overflow = false
                        onBlock()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.folder_move_title)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, null) },
                    onClick = {
                        overflow = false
                        onMoveToFolder()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_select_all)) },
                    onClick = {
                        overflow = false
                        onSelectAll()
                    },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                    onClick = {
                        overflow = false
                        onDelete()
                    },
                )
            }
        },
    )
}
