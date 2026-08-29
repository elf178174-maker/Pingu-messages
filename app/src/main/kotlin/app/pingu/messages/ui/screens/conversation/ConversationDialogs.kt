package app.pingu.messages.ui.screens.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pingu.messages.R
import app.pingu.messages.core.util.FileSizes
import app.pingu.messages.domain.model.Message
import app.pingu.messages.domain.model.MessageTransport
import app.pingu.messages.domain.model.SimCard
import app.pingu.messages.ui.util.TimeFormatting

/**
 * Message details.
 *
 * Shows only what the platform actually reports. There is no invented "read at" line for SMS,
 * because SMS has no read receipt; when a delivery report was never requested, the delivered row is
 * simply absent rather than showing a hopeful dash.
 */
@Composable
fun MessageDetailsDialog(
    message: Message,
    sims: List<SimCard>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sim = sims.firstOrNull { it.subscriptionId == message.subscriptionId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DetailRow(
                    stringResource(R.string.details_type),
                    if (message.transport == MessageTransport.MMS) "MMS" else "SMS",
                )
                if (message.isOutgoing) {
                    message.address?.let { DetailRow(stringResource(R.string.details_to), it) }
                } else {
                    message.address?.let { DetailRow(stringResource(R.string.details_from), it) }
                }
                DetailRow(
                    if (message.isOutgoing) {
                        stringResource(R.string.details_sent)
                    } else {
                        stringResource(R.string.details_received)
                    },
                    TimeFormatting.dayAndTime(context, message.timestamp),
                )
                if (message.sentTimestamp > 0 && message.sentTimestamp != message.timestamp) {
                    DetailRow(
                        stringResource(R.string.details_sent),
                        TimeFormatting.dayAndTime(context, message.sentTimestamp),
                    )
                }
                sim?.let { DetailRow(stringResource(R.string.details_sim), it.label) }
                if (message.sizeBytes > 0) {
                    DetailRow(
                        stringResource(R.string.details_size),
                        FileSizes.format(message.sizeBytes),
                    )
                }
                if (message.attachments.isNotEmpty()) {
                    DetailRow(
                        stringResource(R.string.details_parts),
                        message.attachments.size.toString(),
                    )
                }
                if (message.errorCode != 0) {
                    DetailRow("Error code", message.errorCode.toString())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(LABEL_WIDTH_FRACTION),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Lets the user give a conversation a name of their own, which is useful for group threads. */
@Composable
fun RenameConversationDialog(
    currentTitle: String,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(currentTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.conversation_menu_rename)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.takeIf { it.isNotBlank() }) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private const val LABEL_WIDTH_FRACTION = 0.42f
