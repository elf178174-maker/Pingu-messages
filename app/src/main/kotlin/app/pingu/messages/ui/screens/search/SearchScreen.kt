package app.pingu.messages.ui.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pingu.messages.R
import app.pingu.messages.core.util.FileSizes
import app.pingu.messages.core.util.PhoneNumbers
import app.pingu.messages.domain.model.Recipient
import app.pingu.messages.ui.components.ContactAvatar
import app.pingu.messages.ui.components.EmptyState
import app.pingu.messages.ui.components.GroupAvatar
import app.pingu.messages.ui.components.highlightedText
import app.pingu.messages.ui.util.rememberListTimestamp

/**
 * Global search.
 *
 * Results are grouped by what they are - conversations, contacts, messages, attachments - because a
 * flat list of mixed hits makes the user do the sorting. Tapping a message result opens its
 * conversation and scrolls to that exact message rather than dropping them at the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    onOpenMessage: (threadId: Long, messageId: Long) -> Unit,
    onStartConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val highlightColor = MaterialTheme.colorScheme.tertiaryContainer

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        modifier = modifier,
        topBar = {
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
                    TextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChanged,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onQueryChanged("") }) {
                                    Icon(Icons.Outlined.Close, stringResource(R.string.action_close))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (state.isSearching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when {
                state.query.isBlank() -> EmptyState(
                    icon = Icons.Outlined.Search,
                    title = stringResource(R.string.search_prompt_title),
                    body = stringResource(R.string.search_prompt_body),
                )

                state.isEmpty && state.hasSearched && !state.isSearching -> EmptyState(
                    icon = Icons.Outlined.Search,
                    title = stringResource(R.string.empty_search_title),
                    body = stringResource(R.string.empty_search_body),
                    actionLabel = if (PhoneNumbers.isDiallable(state.query)) {
                        stringResource(R.string.action_new_message)
                    } else {
                        null
                    },
                    onAction = { onStartConversation(state.query) },
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (state.conversations.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.search_section_conversations)) }
                        items(state.conversations, key = { "c${it.threadId}" }) { conversation ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenConversation(conversation.threadId) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (conversation.isGroup) {
                                    GroupAvatar(conversation.recipients, size = 40.dp)
                                } else {
                                    ContactAvatar(conversation.recipients.firstOrNull(), size = 40.dp)
                                }
                                Column(modifier = Modifier.padding(start = 14.dp)) {
                                    Text(
                                        text = conversation.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = conversation.snippet,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    if (state.contacts.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.search_section_contacts)) }
                        items(state.contacts, key = { "p${it.id}" }) { contact ->
                            val number = contact.primaryNumber.orEmpty()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onStartConversation(number) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ContactAvatar(
                                    recipient = Recipient(
                                        address = number,
                                        displayName = contact.displayName,
                                        photoUri = contact.thumbnailUri,
                                    ),
                                    size = 40.dp,
                                )
                                Column(modifier = Modifier.padding(start = 14.dp)) {
                                    Text(contact.displayName, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = PhoneNumbers.formatForDisplay(number),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    if (state.messages.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.search_section_messages)) }
                        items(state.messages, key = { "m${it.messageId}" }) { hit ->
                            MessageHitRow(
                                hit = hit,
                                highlightColor = highlightColor,
                                onClick = { onOpenMessage(hit.threadId, hit.messageId) },
                            )
                        }
                        if (state.totalMessageHits > state.messages.size) {
                            item {
                                TextButton(
                                    onClick = viewModel::loadMoreMessages,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                ) {
                                    Text(stringResource(R.string.search_more_results))
                                }
                            }
                        }
                    }

                    if (state.attachments.isNotEmpty()) {
                        item { SectionHeader(stringResource(R.string.search_section_attachments)) }
                        items(state.attachments, key = { "a${it.id}" }) { attachment ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenConversation(attachment.threadId) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.AttachFile, null)
                                Column(modifier = Modifier.padding(start = 14.dp)) {
                                    Text(
                                        text = attachment.fileName.orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.attachment_size,
                                            attachment.mimeType,
                                            FileSizes.format(attachment.sizeBytes),
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageHitRow(
    hit: MessageHit,
    highlightColor: Color,
    onClick: () -> Unit,
) {
    val timestamp = rememberListTimestamp(hit.timestamp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = hit.conversationTitle, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = hit.conversationTitle,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = highlightedText(
                text = hit.excerpt.text,
                ranges = listOfNotNull(hit.excerpt.matchRange),
                background = highlightColor,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column {
        HorizontalDivider()
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
        )
    }
}
