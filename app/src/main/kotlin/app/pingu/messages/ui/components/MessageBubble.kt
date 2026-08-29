package app.pingu.messages.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pingu.messages.R
import app.pingu.messages.core.text.EmojiText
import app.pingu.messages.core.text.TextEntity
import app.pingu.messages.domain.model.Attachment
import app.pingu.messages.domain.model.BubbleShape
import app.pingu.messages.domain.model.Message
import app.pingu.messages.domain.model.MessageStatus
import app.pingu.messages.domain.model.Recipient
import app.pingu.messages.ui.theme.BubbleShapes
import app.pingu.messages.ui.theme.PinguTheme
import app.pingu.messages.ui.util.rememberMessageTime

/** Everything the bubble needs that is not on the message itself. */
data class MessageBubbleState(
    val message: Message,
    val sender: Recipient?,
    val showSenderName: Boolean,
    val showAvatar: Boolean,
    val isFirstOfGroup: Boolean,
    val isLastOfGroup: Boolean,
    val isSelected: Boolean,
    val selectionMode: Boolean,
    val replySnippet: String?,
    val highlightRanges: List<IntRange> = emptyList(),
)

/**
 * A message bubble.
 *
 * Consecutive messages from the same person within a few minutes are drawn as one block: only the
 * first shows a name, only the last shows a tail and a timestamp. That grouping is what stops a
 * long conversation looking like a wall of separate cards.
 *
 * A message consisting only of a few emoji is drawn large and without a bubble, which is the
 * convention every messenger follows and which makes a one-emoji reply readable at a glance.
 */
@Composable
fun MessageBubble(
    state: MessageBubbleState,
    bubbleShape: BubbleShape,
    textScale: Float,
    audio: AudioPlaybackController,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAttachmentClick: (Attachment) -> Unit,
    onEntityClick: (TextEntity) -> Unit,
    onReplyPreviewClick: () -> Unit,
    onRetry: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = state.message
    val outgoing = message.isOutgoing
    val colors = PinguTheme.colors
    val alignment = if (outgoing) Alignment.End else Alignment.Start

    val bodyText = message.body.orEmpty()
    val largeEmoji = remember(bodyText, message.attachments.size) {
        message.attachments.isEmpty() && EmojiText.isLargeEmojiMessage(bodyText)
    }

    val container = when {
        largeEmoji -> androidx.compose.ui.graphics.Color.Transparent
        message.isFailed -> colors.failedBubble
        outgoing -> colors.outgoingBubble
        else -> colors.incomingBubble
    }
    val onContainer = when {
        largeEmoji -> MaterialTheme.colorScheme.onSurface
        message.isFailed -> colors.onFailedBubble
        outgoing -> colors.onOutgoingBubble
        else -> colors.onIncomingBubble
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (state.isSelected) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = SELECTION_ALPHA)
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                },
            )
            .padding(horizontal = 12.dp, vertical = if (state.isLastOfGroup) 3.dp else 1.dp),
        horizontalAlignment = alignment,
    ) {
        if (state.showSenderName && state.sender != null) {
            Text(
                text = state.sender.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = if (state.showAvatar) AVATAR_GUTTER else 12.dp, bottom = 2.dp),
            )
        }

        Row(verticalAlignment = Alignment.Bottom) {
            if (!outgoing && state.showAvatar) {
                if (state.isLastOfGroup) {
                    ContactAvatar(state.sender, size = 28.dp)
                } else {
                    Box(Modifier.size(28.dp))
                }
                Box(Modifier.width(6.dp))
            }

            Column(horizontalAlignment = alignment) {
                state.replySnippet?.let { snippet ->
                    ReplyPreview(
                        snippet = snippet,
                        outgoing = outgoing,
                        onClick = onReplyPreviewClick,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(bubbleShapeFor(bubbleShape, outgoing, state.isFirstOfGroup, state.isLastOfGroup))
                        .background(container)
                        .then(
                            if (message.isFailed) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.error,
                                    shape = bubbleShapeFor(
                                        bubbleShape,
                                        outgoing,
                                        state.isFirstOfGroup,
                                        state.isLastOfGroup,
                                    ),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                        .padding(
                            horizontal = if (largeEmoji) 0.dp else 12.dp,
                            vertical = if (largeEmoji) 0.dp else 8.dp,
                        )
                        .widthIn(max = BUBBLE_MAX_WIDTH),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!message.subject.isNullOrBlank()) {
                            Text(
                                text = message.subject,
                                style = MaterialTheme.typography.titleSmall,
                                color = onContainer,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        message.attachments.forEach { attachment ->
                            AttachmentContent(
                                attachment = attachment,
                                outgoing = outgoing,
                                audio = audio,
                                onOpen = { onAttachmentClick(attachment) },
                            )
                        }

                        if (message.needsDownload) {
                            DownloadPrompt(
                                status = message.status,
                                sizeBytes = message.sizeBytes,
                                onDownload = onDownload,
                                tint = onContainer,
                            )
                        }

                        if (bodyText.isNotBlank()) {
                            if (largeEmoji) {
                                Text(
                                    text = bodyText,
                                    fontSize = LARGE_EMOJI_SIZE * textScale,
                                    color = onContainer,
                                )
                            } else {
                                LinkedMessageText(
                                    text = bodyText,
                                    style = scaledBodyStyle(textScale),
                                    color = onContainer,
                                    linkColor = onContainer,
                                    highlightRanges = state.highlightRanges,
                                    highlightBackground = MaterialTheme.colorScheme.tertiaryContainer,
                                    onEntityClick = onEntityClick,
                                    onClick = onClick,
                                )
                            }
                        }
                    }
                }

                if (message.reactions.isNotEmpty()) {
                    ReactionRow(
                        reactions = message.reactions.map { it.emoji },
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                if (state.isLastOfGroup || message.isFailed) {
                    MessageFooter(
                        message = message,
                        outgoing = outgoing,
                        onRetry = onRetry,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageFooter(message: Message, outgoing: Boolean, onRetry: () -> Unit) {
    val time = rememberMessageTime(message.timestamp)
    Row(
        modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (outgoing) {
            MessageStatusIcon(status = message.status)
        }
        if (message.isFailed) {
            TextButton(onClick = onRetry, contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
                Text(
                    text = stringResource(R.string.action_retry),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun DownloadPrompt(
    status: MessageStatus,
    sizeBytes: Long,
    onDownload: () -> Unit,
    tint: androidx.compose.ui.graphics.Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (status == MessageStatus.DOWNLOAD_FAILED) {
                Icons.Outlined.Refresh
            } else {
                Icons.Outlined.Download
            },
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Box(Modifier.width(8.dp))
        TextButton(onClick = onDownload) {
            Text(
                text = if (sizeBytes > 0) {
                    stringResource(
                        R.string.attachment_size,
                        stringResource(R.string.action_download_message),
                        app.pingu.messages.core.util.FileSizes.format(sizeBytes),
                    )
                } else {
                    stringResource(R.string.action_download_message)
                },
            )
        }
    }
}

/** A quoted message above a reply, tapping which scrolls to the original. */
@Composable
fun ReplyPreview(
    snippet: String,
    outgoing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.composer_replying_to, snippet)
    Row(
        modifier = modifier
            .widthIn(max = BUBBLE_MAX_WIDTH)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (outgoing) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                ),
        )
        Box(Modifier.width(8.dp))
        Text(
            text = snippet,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The reaction chips shown under a bubble. */
@Composable
fun ReactionRow(reactions: List<String>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        reactions.distinct().take(MAX_REACTION_CHIPS).forEach { emoji ->
            Text(text = emoji, fontSize = 13.sp)
        }
        if (reactions.size > 1) {
            Text(
                text = reactions.size.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}

/**
 * Bubble corners.
 *
 * With tails on, the corner nearest the sender is squared off on the last bubble of a block, which
 * is the visual cue that says "this side of the conversation ends here". With tails off every
 * bubble is symmetric, for people who prefer the calmer look.
 */
private fun bubbleShapeFor(
    style: BubbleShape,
    outgoing: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
): Shape {
    val large = BubbleShapes.corner
    val small = BubbleShapes.cornerSmall
    if (style == BubbleShape.ROUNDED) return RoundedCornerShape(large)

    val topStart = if (!outgoing && !isFirst) small else large
    val topEnd = if (outgoing && !isFirst) small else large
    val bottomStart = if (!outgoing && isLast) BubbleShapes.tailCorner else if (!outgoing) small else large
    val bottomEnd = if (outgoing && isLast) BubbleShapes.tailCorner else if (outgoing) small else large
    return RoundedCornerShape(
        topStart = topStart,
        topEnd = topEnd,
        bottomStart = bottomStart,
        bottomEnd = bottomEnd,
    )
}

private val BUBBLE_MAX_WIDTH = 300.dp
private val AVATAR_GUTTER = 46.dp
private val LARGE_EMOJI_SIZE = 44.sp
private const val SELECTION_ALPHA = 0.45f
private const val MAX_REACTION_CHIPS = 3

/**
 * The message body style at the user's chosen size.
 *
 * Only the message text scales: timestamps, sender names and status lines stay at the system size,
 * so raising it enlarges what is being read rather than everything at once.
 */
@Composable
private fun scaledBodyStyle(textScale: Float): TextStyle {
    val base = MaterialTheme.typography.bodyLarge
    return if (textScale == 1f) base else base.copy(fontSize = base.fontSize * textScale)
}
