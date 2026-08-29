package app.pingu.messages.ui.screens.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pingu.messages.R
import app.pingu.messages.core.util.FileSizes
import app.pingu.messages.core.util.SmsMessageSize
import app.pingu.messages.domain.model.Attachment
import app.pingu.messages.domain.model.SimCard as SimCardModel
import app.pingu.messages.ui.util.formatDuration
import coil.compose.AsyncImage

/**
 * The message composer.
 *
 * The trailing button adapts to what is in the field: a microphone when there is nothing to send,
 * a send button as soon as there is. That is the behaviour of every mature messenger and it means
 * the most common action is always under the thumb.
 *
 * The segment counter only appears once a message will cost more than one SMS, or once a character
 * has forced Unicode encoding - the two moments where it actually tells the user something.
 */
@Composable
fun MessageComposer(
    state: ComposerState,
    sims: List<SimCardModel>,
    recordingElapsedMillis: Long?,
    recordingCancelArmed: Boolean,
    onTextChange: (String) -> Unit,
    onSubjectChange: (String?) -> Unit,
    onSend: () -> Unit,
    onSendLongPress: () -> Unit,
    onAttach: () -> Unit,
    onCamera: () -> Unit,
    onEmoji: () -> Unit,
    onRemoveAttachment: (Attachment) -> Unit,
    onCancelReply: () -> Unit,
    onSelectSim: (Int) -> Unit,
    onRecordStart: () -> Unit,
    onRecordFinish: () -> Unit,
    onRecordCancel: () -> Unit,
    onRecordDrag: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val recording = recordingElapsedMillis != null

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            state.replyToSnippet?.let { snippet ->
                ReplyChip(snippet = snippet, onCancel = onCancelReply)
            }

            AnimatedVisibility(visible = state.attachments.isNotEmpty()) {
                AttachmentStrip(
                    attachments = state.attachments,
                    onRemove = onRemoveAttachment,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            state.subject?.let { subject ->
                OutlinedTextField(
                    value = subject,
                    onValueChange = { onSubjectChange(it) },
                    placeholder = { Text(stringResource(R.string.composer_hint_subject)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    trailingIcon = {
                        IconButton(onClick = { onSubjectChange(null) }) {
                            Icon(Icons.Outlined.Close, stringResource(R.string.action_remove))
                        }
                    },
                )
            }

            if (recording) {
                RecordingRow(
                    elapsedMillis = recordingElapsedMillis,
                    cancelArmed = recordingCancelArmed,
                )
            }

            Row(verticalAlignment = Alignment.Bottom) {
                if (!recording) {
                    IconButton(onClick = onAttach, enabled = enabled) {
                        Icon(
                            Icons.Outlined.AddCircleOutline,
                            stringResource(R.string.composer_attach),
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (recording) {
                        Text(
                            text = stringResource(
                                if (recordingCancelArmed) {
                                    R.string.composer_recording_slide_to_cancel
                                } else {
                                    R.string.composer_recording_release_to_send
                                },
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    } else {
                        ComposerField(
                            state = state,
                            enabled = enabled,
                            onTextChange = onTextChange,
                            onEmoji = onEmoji,
                            onCamera = onCamera,
                        )
                    }
                }

                if (sims.size > 1 && !recording) {
                    SimSelector(
                        sims = sims,
                        selectedSubscriptionId = state.subscriptionId,
                        onSelect = onSelectSim,
                    )
                }

                SendButton(
                    canSend = state.canSend,
                    sending = state.isSending,
                    enabled = enabled,
                    recording = recording,
                    onSend = onSend,
                    onSendLongPress = onSendLongPress,
                    onRecordStart = onRecordStart,
                    onRecordFinish = onRecordFinish,
                    onRecordCancel = onRecordCancel,
                    onRecordDrag = onRecordDrag,
                )
            }

            SegmentCounter(text = state.text, isMms = state.requiresMms)
        }
    }
}

@Composable
private fun ComposerField(
    state: ComposerState,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
    onEmoji: () -> Unit,
    onCamera: () -> Unit,
) {
    OutlinedTextField(
        value = state.text,
        onValueChange = onTextChange,
        enabled = enabled,
        placeholder = {
            Text(
                stringResource(
                    if (state.requiresMms) R.string.composer_hint_mms else R.string.composer_hint,
                ),
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp, max = 140.dp),
        shape = RoundedCornerShape(24.dp),
        maxLines = 6,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Default,
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        leadingIcon = {
            IconButton(onClick = onEmoji) {
                Icon(Icons.Outlined.EmojiEmotions, stringResource(R.string.composer_emoji))
            }
        },
        trailingIcon = {
            IconButton(onClick = onCamera) {
                Icon(Icons.Outlined.CameraAlt, stringResource(R.string.composer_camera))
            }
        },
    )
}

/**
 * The send button, which becomes a hold-to-record microphone when there is nothing to send.
 *
 * Holding starts the recording, releasing sends it, and dragging away far enough cancels - the
 * gesture people already know. A long press on the send button opens the scheduler.
 */
@Composable
private fun SendButton(
    canSend: Boolean,
    sending: Boolean,
    enabled: Boolean,
    recording: Boolean,
    onSend: () -> Unit,
    onSendLongPress: () -> Unit,
    onRecordStart: () -> Unit,
    onRecordFinish: () -> Unit,
    onRecordCancel: () -> Unit,
    onRecordDrag: (Float) -> Unit,
) {
    val background = if (canSend || recording) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val tint = if (canSend || recording) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .padding(start = 4.dp, bottom = 2.dp)
            .size(48.dp)
            .clip(CircleShape)
            .background(background)
            .then(
                if (canSend) {
                    Modifier.sendGestures(enabled && !sending, onSend, onSendLongPress)
                } else {
                    Modifier.recordGestures(enabled, onRecordStart, onRecordFinish, onRecordCancel, onRecordDrag)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (canSend) Icons.AutoMirrored.Filled.Send else Icons.Outlined.Mic,
            contentDescription = stringResource(
                if (canSend) R.string.composer_send else R.string.composer_record,
            ),
            tint = tint,
        )
    }
}

@Composable
private fun ReplyChip(snippet: String, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.composer_replying_to, ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = snippet,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Outlined.Close, stringResource(R.string.composer_clear_reply))
        }
    }
}

@Composable
private fun AttachmentStrip(
    attachments: List<Attachment>,
    onRemove: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(attachments, key = { it.uri }) { attachment ->
            Box {
                if (attachment.kind.isVisualMedia) {
                    AsyncImage(
                        model = attachment.uri,
                        contentDescription = attachment.fileName,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(6.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = attachment.fileName ?: attachment.mimeType,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = FileSizes.format(attachment.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(
                    onClick = { onRemove(attachment) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.attachment_remove),
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(elapsedMillis: Long, cancelArmed: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error),
        )
        Box(Modifier.width(8.dp))
        Text(
            text = formatDuration(elapsedMillis),
            style = MaterialTheme.typography.labelLarge,
            color = if (cancelArmed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun SimSelector(
    sims: List<SimCardModel>,
    selectedSubscriptionId: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = sims.firstOrNull { it.subscriptionId == selectedSubscriptionId } ?: sims.first()

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Outlined.SimCard,
                contentDescription = stringResource(R.string.composer_sim_selector),
            )
        }
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            sims.forEach { sim ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(sim.label) },
                    onClick = {
                        expanded = false
                        onSelect(sim.subscriptionId)
                    },
                    trailingIcon = {
                        if (sim.subscriptionId == selected.subscriptionId) {
                            Icon(Icons.Outlined.SimCard, null)
                        }
                    },
                )
            }
        }
    }
}

/**
 * The live segment counter.
 *
 * Hidden while a message still fits in one SMS, because until then it is noise. As soon as it does
 * not, it shows characters remaining, the segment count and, implicitly, why: an emoji or a curly
 * quote drops the limit from 160 to 70.
 */
@Composable
private fun SegmentCounter(text: String, isMms: Boolean) {
    if (isMms || text.isEmpty()) return
    val measurement = remember(text) { SmsMessageSize.measure(text) }
    if (measurement.segments <= 1 && measurement.encoding == SmsMessageSize.Encoding.GSM_7BIT) return

    Text(
        text = stringResource(
            R.string.composer_character_counter,
            measurement.remainingInSegment,
            measurement.units,
            measurement.segments,
        ),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 12.dp, top = 2.dp),
    )
}
