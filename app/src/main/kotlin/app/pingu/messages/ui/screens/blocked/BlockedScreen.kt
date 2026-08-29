package app.pingu.messages.ui.screens.blocked

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pingu.messages.R
import app.pingu.messages.core.util.PhoneNumbers
import app.pingu.messages.ui.components.EmptyState

/**
 * Blocked numbers and spam.
 *
 * The system block list is used where the platform allows it, which is why a blocked number here
 * also stops calls; that is stated on the row rather than left as a surprise.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedScreen(
    viewModel: BlockedViewModel,
    onBack: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var addingNumber by remember { mutableStateOf(false) }
    var addingKeyword by remember { mutableStateOf(false) }

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
                title = { Text(stringResource(R.string.blocked_numbers_title)) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (tab == 0) addingNumber = true else addingKeyword = true },
            ) {
                Icon(Icons.Outlined.Add, stringResource(R.string.action_add))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(stringResource(R.string.blocked_tab_numbers)) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(stringResource(R.string.blocked_tab_spam)) },
                )
            }

            if (tab == 0) {
                if (state.blocked.isEmpty()) {
                    EmptyState(
                        icon = Icons.Outlined.Block,
                        title = stringResource(R.string.empty_blocked_title),
                        body = stringResource(R.string.empty_blocked_body),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.blocked, key = { it.matchKey }) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = PhoneNumbers.formatForDisplay(entry.address),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    if (entry.syncedToSystem) {
                                        Text(
                                            text = stringResource(R.string.blocked_system_synced),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                TextButton(onClick = { viewModel.unblock(entry.address) }) {
                                    Text(stringResource(R.string.action_unblock))
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Text(
                            text = stringResource(R.string.blocked_keywords_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                    items(state.keywords, key = { it.id }) { keyword ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = keyword.keyword, modifier = Modifier.weight(1f))
                            IconButton(onClick = { viewModel.removeKeyword(keyword.id) }) {
                                Icon(Icons.Outlined.Close, stringResource(R.string.action_remove))
                            }
                        }
                    }
                    if (state.spamConversations.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.conversations_spam),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 20.dp, top = 16.dp),
                            )
                        }
                        items(state.spamConversations, key = { it.threadId }) { conversation ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenConversation(conversation.threadId) }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(conversation.title, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        text = conversation.snippet,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                                TextButton(
                                    onClick = { viewModel.restoreFromSpam(conversation.threadId) },
                                ) {
                                    Text(stringResource(R.string.action_not_spam))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (addingNumber) {
        TextEntryDialog(
            title = stringResource(R.string.blocked_add_number),
            label = stringResource(R.string.recipients_hint),
            onConfirm = {
                viewModel.block(it)
                addingNumber = false
            },
            onDismiss = { addingNumber = false },
        )
    }

    if (addingKeyword) {
        TextEntryDialog(
            title = stringResource(R.string.blocked_keywords_title),
            label = stringResource(R.string.blocked_keyword_hint),
            onConfirm = {
                viewModel.addKeyword(it)
                addingKeyword = false
            },
            onDismiss = { addingKeyword = false },
        )
    }
}

@Composable
private fun TextEntryDialog(
    title: String,
    label: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value.trim()) },
                enabled = value.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
