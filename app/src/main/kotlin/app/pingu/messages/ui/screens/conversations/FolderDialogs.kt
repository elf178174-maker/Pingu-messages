package app.pingu.messages.ui.screens.conversations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pingu.messages.R
import app.pingu.messages.data.local.dao.FolderWithCount

/** A one-line text prompt, used to name a folder and to rename one. */
@Composable
fun FolderNameDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text(stringResource(R.string.folder_name_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * Picks the folder some conversations move into.
 *
 * "No folder" is always offered, because a conversation must be able to come back to the inbox, and
 * creating a folder from here moves the selection into it immediately.
 */
@Composable
fun MoveToFolderDialog(
    folders: List<FolderWithCount>,
    onMove: (Long?) -> Unit,
    onCreateFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_move_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                FolderChoiceRow(
                    icon = Icons.Outlined.Inbox,
                    label = stringResource(R.string.folder_none),
                    onClick = { onMove(null) },
                )
                folders.forEach { folder ->
                    FolderChoiceRow(
                        icon = Icons.Outlined.Folder,
                        label = folder.name,
                        onClick = { onMove(folder.id) },
                    )
                }
                FolderChoiceRow(
                    icon = Icons.Outlined.Add,
                    label = stringResource(R.string.folder_new),
                    onClick = onCreateFolder,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Creating, renaming and deleting folders, reached from the conversation list menu. */
@Composable
fun ManageFoldersDialog(
    folders: List<FolderWithCount>,
    onCreateFolder: () -> Unit,
    onRenameFolder: (FolderWithCount) -> Unit,
    onDeleteFolder: (FolderWithCount) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_folders)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (folders.isEmpty()) {
                    Text(
                        text = stringResource(R.string.folder_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                folders.forEach { folder ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                        ) {
                            Text(folder.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = pluralConversationCount(folder.conversationCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onRenameFolder(folder) }) {
                            Icon(
                                Icons.Outlined.Edit,
                                stringResource(R.string.folder_rename),
                            )
                        }
                        IconButton(onClick = { onDeleteFolder(folder) }) {
                            Icon(
                                Icons.Outlined.Delete,
                                stringResource(R.string.folder_delete_title),
                            )
                        }
                    }
                }
                FolderChoiceRow(
                    icon = Icons.Outlined.Add,
                    label = stringResource(R.string.folder_new),
                    onClick = onCreateFolder,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
    )
}

@Composable
private fun pluralConversationCount(count: Int): String =
    androidx.compose.ui.res.pluralStringResource(R.plurals.folder_conversations, count, count)

@Composable
private fun FolderChoiceRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
