package app.pingu.messages.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pingu.messages.R
import app.pingu.messages.domain.model.Conversation
import app.pingu.messages.domain.model.SwipeAction
import app.pingu.messages.ui.theme.PinguTheme
import app.pingu.messages.ui.util.rememberListTimestamp

/**
 * One row of the conversation list.
 *
 * Everything a person needs to triage a thread without opening it: who it is from, what was said,
 * when, whether it is unread, muted, pinned, or holds an unsent draft, and whether the last message
 * they sent actually arrived.
 *
 * Unread is shown three ways at once - a filled dot, a bolder name and a bolder snippet - because
 * relying on weight alone fails in bright sunlight and relying on colour alone fails for a
 * colour-blind user.
 */
@Composable
fun ConversationListItem(
    conversation: Conversation,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PinguTheme.colors
    val unread = conversation.hasUnread
    val timestamp = rememberListTimestamp(conversation.lastMessageTimestamp)
    val title = conversation.title.ifBlank { stringResource(R.string.contact_unknown) }

    val background = when {
        selected -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = title,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (conversation.isGroup) {
                GroupAvatar(conversation.recipients)
            } else {
                ContactAvatar(conversation.recipients.firstOrNull())
            }
            SelectionBadge(selected = selected)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (conversation.isPinned) {
                    RowIcon(Icons.Outlined.PushPin, stringResource(R.string.conversation_pinned))
                }
                if (conversation.isMuted) {
                    RowIcon(
                        Icons.Outlined.NotificationsOff,
                        stringResource(R.string.conversation_muted),
                    )
                }
            }

            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (conversation.snippetIsOutgoing && conversation.snippetStatus != null &&
                    !conversation.hasDraft
                ) {
                    MessageStatusIcon(
                        status = conversation.snippetStatus,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
                Text(
                    text = snippetText(conversation),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
                    color = when {
                        conversation.hasDraft -> MaterialTheme.colorScheme.error
                        unread -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(
            modifier = Modifier.padding(start = 8.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = if (unread) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (unread) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(colors.unreadIndicator)
                        .semantics {
                            contentDescription = "" // announced through the row description
                        },
                )
            } else {
                Box(modifier = Modifier.size(10.dp))
            }
        }
    }
}

@Composable
private fun snippetText(conversation: Conversation): String {
    val draftPrefix = stringResource(R.string.conversation_draft_prefix)
    val youPrefix = stringResource(R.string.conversation_you_prefix)
    val attachmentLabel = stringResource(R.string.conversation_attachment)
    return when {
        conversation.hasDraft -> "$draftPrefix ${conversation.draftText.orEmpty()}".trim()
        conversation.snippet.isNotBlank() && conversation.snippetIsOutgoing ->
            "$youPrefix ${conversation.snippet}"

        conversation.snippet.isNotBlank() -> conversation.snippet
        conversation.snippetHasAttachment -> attachmentLabel
        else -> ""
    }
}

@Composable
private fun RowIcon(icon: ImageVector, description: String) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 6.dp)
            .size(14.dp),
    )
}

/**
 * Wraps a conversation row in configurable swipe actions.
 *
 * Which action each direction performs comes from settings, and "None" genuinely disables the
 * gesture rather than swiping to nothing. Destructive actions still confirm afterwards through an
 * undo snackbar, so a swipe is never the last word.
 */
@Composable
fun SwipeableConversationItem(
    conversation: Conversation,
    startAction: SwipeAction,
    endAction: SwipeAction,
    onAction: (SwipeAction) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (startAction == SwipeAction.NONE && endAction == SwipeAction.NONE) {
        Box(modifier = modifier) { content() }
        return
    }

    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> startAction != SwipeAction.NONE
                SwipeToDismissBoxValue.EndToStart -> endAction != SwipeAction.NONE
                SwipeToDismissBoxValue.Settled -> false
            }
        },
    )

    LaunchedEffect(state.currentValue) {
        when (state.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                onAction(startAction)
                state.snapTo(SwipeToDismissBoxValue.Settled)
            }

            SwipeToDismissBoxValue.EndToStart -> {
                onAction(endAction)
                state.snapTo(SwipeToDismissBoxValue.Settled)
            }

            SwipeToDismissBoxValue.Settled -> Unit
        }
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = startAction != SwipeAction.NONE,
        enableDismissFromEndToStart = endAction != SwipeAction.NONE,
        backgroundContent = {
            val direction = state.dismissDirection
            val action = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> startAction
                SwipeToDismissBoxValue.EndToStart -> endAction
                SwipeToDismissBoxValue.Settled -> SwipeAction.NONE
            }
            SwipeBackground(action, direction, conversation)
        },
        content = { content() },
    )
}

@Composable
private fun SwipeBackground(
    action: SwipeAction,
    direction: SwipeToDismissBoxValue,
    conversation: Conversation,
) {
    if (action == SwipeAction.NONE || direction == SwipeToDismissBoxValue.Settled) {
        Box(Modifier.fillMaxSize())
        return
    }

    val destructive = action == SwipeAction.DELETE
    val container = if (destructive) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val onContainer = if (destructive) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    val icon = when (action) {
        SwipeAction.ARCHIVE ->
            if (conversation.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive

        SwipeAction.DELETE -> Icons.Outlined.Delete
        SwipeAction.MARK_READ_UNREAD -> Icons.Outlined.MarkEmailRead
        SwipeAction.PIN -> Icons.Outlined.PushPin
        SwipeAction.MUTE -> Icons.Outlined.NotificationsOff
        SwipeAction.NONE -> Icons.Outlined.Archive
    }

    val label = when (action) {
        SwipeAction.ARCHIVE -> stringResource(
            if (conversation.isArchived) R.string.action_unarchive else R.string.action_archive,
        )

        SwipeAction.DELETE -> stringResource(R.string.action_delete)
        SwipeAction.MARK_READ_UNREAD -> stringResource(
            if (conversation.hasUnread) R.string.action_mark_read else R.string.action_mark_unread,
        )

        SwipeAction.PIN -> stringResource(
            if (conversation.isPinned) R.string.action_unpin else R.string.action_pin,
        )

        SwipeAction.MUTE -> stringResource(
            if (conversation.isMuted) R.string.action_unmute else R.string.action_mute,
        )

        SwipeAction.NONE -> ""
    }

    val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
        Alignment.CenterStart
    } else {
        Alignment.CenterEnd
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(container)
            .padding(horizontal = 24.dp)
            .clearAndSetSemantics { },
        contentAlignment = alignment,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = onContainer)
            Box(Modifier.width(10.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = onContainer)
        }
    }
}

/**
 * The check mark drawn over an avatar in multi-select.
 *
 * It lives in its own composable so `AnimatedVisibility` resolves to the plain overload: called
 * inline it would pick up the enclosing `RowScope` and fail to resolve.
 */
@Composable
private fun SelectionBadge(selected: Boolean) {
    AnimatedVisibility(
        visible = selected,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = stringResource(R.string.cd_selected),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
