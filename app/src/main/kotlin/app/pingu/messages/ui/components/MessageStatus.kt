package app.pingu.messages.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pingu.messages.R
import app.pingu.messages.domain.model.MessageStatus

/**
 * The delivery state of an outgoing message.
 *
 * Every state has both an icon and a content description, so the information is never carried by
 * shape or colour alone - a requirement for screen readers and for anyone who cannot distinguish
 * one tick from two.
 *
 * There is no "read" tick for SMS anywhere in this app, because the protocol has no read receipt
 * and inventing one would be a lie about what the recipient has seen.
 */
@Composable
fun MessageStatusIcon(
    status: MessageStatus,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val (icon, descriptionRes) = when (status) {
        MessageStatus.SENDING -> Icons.Outlined.Schedule to R.string.status_sending
        MessageStatus.SENT -> Icons.Outlined.Check to R.string.status_sent
        MessageStatus.DELIVERED -> Icons.Outlined.DoneAll to R.string.status_delivered
        MessageStatus.READ -> Icons.Outlined.DoneAll to R.string.status_read
        MessageStatus.FAILED -> Icons.Filled.Error to R.string.status_failed
        MessageStatus.SCHEDULED -> Icons.Outlined.Schedule to R.string.status_scheduled
        MessageStatus.DOWNLOADING -> Icons.Outlined.Schedule to R.string.status_downloading
        MessageStatus.PENDING_DOWNLOAD -> Icons.Outlined.Schedule to R.string.status_downloading
        MessageStatus.DOWNLOAD_FAILED -> Icons.Filled.Error to R.string.status_download_failed
        MessageStatus.EXPIRED -> Icons.Filled.Error to R.string.status_expired
        MessageStatus.DRAFT -> Icons.Outlined.Schedule to R.string.conversation_draft_prefix
        MessageStatus.RECEIVED -> return
    }

    Icon(
        imageVector = icon,
        contentDescription = stringResource(descriptionRes),
        tint = if (status.isFailure) MaterialTheme.colorScheme.error else tint,
        modifier = modifier.size(size),
    )
}
