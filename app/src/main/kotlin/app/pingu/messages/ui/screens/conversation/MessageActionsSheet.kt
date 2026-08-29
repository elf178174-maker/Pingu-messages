package app.pingu.messages.ui.screens.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pingu.messages.R
import app.pingu.messages.core.text.Tapbacks
import app.pingu.messages.domain.model.Message
import app.pingu.messages.ui.components.ReactionPicker

/** The actions a single message offers. */
enum class MessageAction {
    REACT, REPLY, COPY, FORWARD, SHARE, SAVE_ATTACHMENT, SELECT, DETAILS, RESEND, DELETE
}

/**
 * The long-press menu for a message.
 *
 * Reactions sit at the top as a row of emoji, the way people expect. Below that only the actions
 * that make sense for this particular message: no "Save attachment" on a plain text message, no
 * "Send again" on one that arrived successfully.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionsSheet(
    message: Message,
    reactionsExplained: Boolean,
    onAction: (MessageAction) -> Unit,
    onReact: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ownReaction = message.reactions.firstOrNull { it.isFromMe }?.emoji

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        ReactionPicker(
            options = Tapbacks.palette,
            selected = ownReaction,
            onSelect = onReact,
        )
        if (reactionsExplained) {
            Text(
                text = stringResource(R.string.reaction_local_only),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            ActionRow(Icons.AutoMirrored.Outlined.Reply, stringResource(R.string.action_reply)) {
                onAction(MessageAction.REPLY)
            }
            if (message.hasText) {
                ActionRow(Icons.Outlined.ContentCopy, stringResource(R.string.message_action_copy_text)) {
                    onAction(MessageAction.COPY)
                }
            }
            ActionRow(Icons.AutoMirrored.Outlined.Send, stringResource(R.string.action_forward)) {
                onAction(MessageAction.FORWARD)
            }
            ActionRow(Icons.Outlined.Share, stringResource(R.string.action_share)) {
                onAction(MessageAction.SHARE)
            }
            if (message.hasAttachments) {
                ActionRow(
                    Icons.Outlined.Download,
                    stringResource(R.string.message_action_save_attachment),
                ) {
                    onAction(MessageAction.SAVE_ATTACHMENT)
                }
            }
            if (message.isFailed) {
                ActionRow(Icons.Outlined.Refresh, stringResource(R.string.message_action_resend)) {
                    onAction(MessageAction.RESEND)
                }
            }
            ActionRow(Icons.Outlined.SelectAll, stringResource(R.string.action_select)) {
                onAction(MessageAction.SELECT)
            }
            ActionRow(Icons.Outlined.Info, stringResource(R.string.message_action_details)) {
                onAction(MessageAction.DETAILS)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            ActionRow(
                icon = Icons.Outlined.Delete,
                label = stringResource(R.string.message_action_delete),
                tint = MaterialTheme.colorScheme.error,
            ) {
                onAction(MessageAction.DELETE)
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            modifier = Modifier.padding(start = 20.dp),
        )
    }
}
