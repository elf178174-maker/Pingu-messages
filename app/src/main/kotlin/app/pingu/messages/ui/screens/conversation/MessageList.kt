package app.pingu.messages.ui.screens.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pingu.messages.core.text.SearchMatcher
import app.pingu.messages.core.text.TextEntity
import app.pingu.messages.core.time.RelativeTime
import app.pingu.messages.core.util.PhoneNumbers
import app.pingu.messages.domain.model.Attachment
import app.pingu.messages.domain.model.Message
import app.pingu.messages.domain.model.Recipient
import app.pingu.messages.ui.components.AudioPlaybackController
import app.pingu.messages.ui.components.DateSeparator
import app.pingu.messages.ui.components.MessageBubble
import app.pingu.messages.ui.components.MessageBubbleState
import app.pingu.messages.ui.util.rememberDateSeparatorLabel
import java.time.ZoneId

/**
 * The scrolling list of messages.
 *
 * Drawn in reverse so index zero is the newest message: that way an arriving message appears at the
 * bottom without a scroll animation, and "load older" is simply asking for more items at the end.
 *
 * Grouping and date separators are computed per item from its neighbours, which keeps the work
 * proportional to what is on screen rather than to the size of the thread.
 */
@Composable
fun MessageList(
    state: ConversationUiState,
    listState: LazyListState,
    audio: AudioPlaybackController,
    onMessageClick: (Message) -> Unit,
    onMessageLongClick: (Message) -> Unit,
    onAttachmentClick: (Message, Attachment) -> Unit,
    onEntityClick: (TextEntity) -> Unit,
    onReplyPreviewClick: (Message) -> Unit,
    onRetry: (Message) -> Unit,
    onDownload: (Message) -> Unit,
    onLoadOlder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    val messages = state.messages
    val conversation = state.conversation

    val reachedTop by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= messages.size - LOAD_MORE_THRESHOLD
        }
    }

    LaunchedEffect(reachedTop, messages.size) {
        if (reachedTop && state.hasMoreToLoad) onLoadOlder()
    }

    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        itemsIndexed(
            items = messages,
            key = { _, message -> message.id },
        ) { index, message ->
            // "newer" is the item before this one in reverse layout, i.e. later in time.
            val newer = messages.getOrNull(index - 1)
            val older = messages.getOrNull(index + 1)

            val sender = senderFor(message, conversation?.recipients.orEmpty())
            val sameSenderAsOlder = older != null &&
                older.isOutgoing == message.isOutgoing &&
                PhoneNumbers.sameNumber(older.address, message.address) &&
                RelativeTime.withinGroupingWindow(older.timestamp, message.timestamp, GROUPING_WINDOW_MILLIS)
            val sameSenderAsNewer = newer != null &&
                newer.isOutgoing == message.isOutgoing &&
                PhoneNumbers.sameNumber(newer.address, message.address) &&
                RelativeTime.withinGroupingWindow(message.timestamp, newer.timestamp, GROUPING_WINDOW_MILLIS)

            val highlightRanges = if (state.searchQuery.isBlank()) {
                emptyList()
            } else {
                SearchMatcher.findRanges(message.body.orEmpty(), state.searchQuery)
            }

            MessageBubble(
                state = MessageBubbleState(
                    message = message,
                    sender = sender,
                    showSenderName = conversation?.isGroup == true &&
                        !message.isOutgoing &&
                        !sameSenderAsOlder,
                    showAvatar = conversation?.isGroup == true && !message.isOutgoing,
                    isFirstOfGroup = !sameSenderAsOlder,
                    isLastOfGroup = !sameSenderAsNewer,
                    isSelected = message.id in state.selectedMessageIds,
                    selectionMode = state.selectionMode,
                    replySnippet = message.replyToSnippet,
                    highlightRanges = highlightRanges,
                ),
                bubbleShape = state.settings.bubbleShape,
                audio = audio,
                onClick = { onMessageClick(message) },
                onLongClick = { onMessageLongClick(message) },
                onAttachmentClick = { onAttachmentClick(message, it) },
                onEntityClick = onEntityClick,
                onReplyPreviewClick = { onReplyPreviewClick(message) },
                onRetry = { onRetry(message) },
                onDownload = { onDownload(message) },
            )

            // In reverse layout the separator for a day is drawn after the oldest message of it.
            val startsNewDay = older == null ||
                !RelativeTime.isSameDay(older.timestamp, message.timestamp, zone)
            if (startsNewDay) {
                DateSeparator(label = rememberDateSeparatorLabel(message.timestamp))
            }
        }

        if (state.hasMoreToLoad) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            }
        }
    }
}

private fun senderFor(message: Message, recipients: List<Recipient>): Recipient? {
    if (message.isOutgoing) return null
    return recipients.firstOrNull { PhoneNumbers.sameNumber(it.address, message.address) }
        ?: recipients.firstOrNull()
}

/** Messages from the same person inside this window are drawn as one visual block. */
private const val GROUPING_WINDOW_MILLIS = 3 * 60 * 1000L
private const val LOAD_MORE_THRESHOLD = 8
