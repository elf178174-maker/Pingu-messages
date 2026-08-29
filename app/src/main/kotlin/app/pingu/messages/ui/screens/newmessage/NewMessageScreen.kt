package app.pingu.messages.ui.screens.newmessage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pingu.messages.R
import app.pingu.messages.core.util.PhoneNumbers
import app.pingu.messages.domain.model.Recipient
import app.pingu.messages.platform.permission.PermissionGroup
import app.pingu.messages.ui.components.ContactAvatar
import app.pingu.messages.ui.components.EmptyState

/**
 * Picking recipients for a new message.
 *
 * A typed number is always usable even without the contacts permission, so the screen is never a
 * dead end: the contact list is an accelerator, not a requirement.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewMessageScreen(
    viewModel: NewMessageViewModel,
    onBack: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val requestContacts = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refreshPermission() }

    LaunchedEffect(Unit) {
        if (!state.hasContactsPermission) {
            requestContacts.launch(PermissionGroup.CONTACTS.permissions.toTypedArray())
        }
    }

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
                title = { Text(stringResource(R.string.new_message_title)) },
                actions = {
                    TextButton(
                        onClick = { viewModel.resolveThread(onOpenConversation) },
                        enabled = state.canContinue,
                    ) {
                        Text(stringResource(R.string.action_continue))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (state.recipients.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.recipients.forEach { recipient ->
                        InputChip(
                            selected = false,
                            onClick = { viewModel.removeRecipient(recipient) },
                            label = { Text(recipient.label) },
                            trailingIcon = {
                                Icon(
                                    Icons.Outlined.Close,
                                    stringResource(R.string.recipients_remove),
                                )
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChanged,
                label = { Text(stringResource(R.string.new_message_to)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )

            if (state.queryIsDiallable) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.addRecipient(state.query) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.PersonSearch, null)
                    Text(
                        text = stringResource(
                            R.string.new_message_add_recipient,
                            PhoneNumbers.formatForDisplay(state.query),
                        ),
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }

            if (state.suggestions.isEmpty() && state.query.isNotBlank() && !state.isSearching) {
                EmptyState(
                    icon = Icons.Outlined.PersonSearch,
                    title = stringResource(R.string.empty_search_title),
                    body = stringResource(R.string.new_message_no_contacts),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.suggestions, key = { it.id }) { contact ->
                        contact.phones.forEach { phone ->
                            ContactRow(
                                name = contact.displayName,
                                number = phone.number,
                                label = phone.typeLabel,
                                photoUri = contact.thumbnailUri,
                                onClick = {
                                    viewModel.addRecipient(
                                        address = phone.number,
                                        displayName = contact.displayName,
                                        contactId = contact.id,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(
    name: String,
    number: String,
    label: String?,
    photoUri: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = name, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(
            recipient = Recipient(address = number, displayName = name, photoUri = photoUri),
            size = 40.dp,
        )
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(text = name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = listOfNotNull(label, PhoneNumbers.formatForDisplay(number))
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
