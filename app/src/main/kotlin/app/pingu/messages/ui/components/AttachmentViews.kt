package app.pingu.messages.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pingu.messages.R
import app.pingu.messages.core.util.FileSizes
import app.pingu.messages.domain.model.Attachment
import app.pingu.messages.domain.model.AttachmentKind
import app.pingu.messages.ui.util.formatDuration
import coil.compose.AsyncImage

/**
 * Renders one attachment inside a message bubble.
 *
 * Each media kind gets the treatment it deserves rather than a generic file chip: a photo is a
 * photo, a video shows a frame with a play badge, a voice message gets a scrubber and a duration,
 * and only genuinely opaque files fall back to name-type-size.
 */
@Composable
fun AttachmentContent(
    attachment: Attachment,
    outgoing: Boolean,
    audio: AudioPlaybackController,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (attachment.kind) {
        AttachmentKind.IMAGE, AttachmentKind.GIF ->
            ImageAttachment(attachment, onOpen, modifier)

        AttachmentKind.VIDEO -> VideoAttachment(attachment, onOpen, modifier)

        AttachmentKind.VOICE, AttachmentKind.AUDIO ->
            AudioAttachment(attachment, outgoing, audio, modifier)

        AttachmentKind.CONTACT_CARD ->
            IconAttachment(
                icon = Icons.Outlined.Person,
                title = attachment.fileName ?: stringResource(R.string.attachment_contact),
                subtitle = stringResource(R.string.attachment_contact),
                outgoing = outgoing,
                onClick = onOpen,
                modifier = modifier,
            )

        AttachmentKind.LOCATION ->
            IconAttachment(
                icon = Icons.Outlined.Place,
                title = stringResource(R.string.attachment_location),
                subtitle = attachment.extra.orEmpty(),
                outgoing = outgoing,
                onClick = onOpen,
                modifier = modifier,
            )

        AttachmentKind.FILE ->
            IconAttachment(
                icon = Icons.Outlined.Description,
                title = attachment.fileName ?: stringResource(R.string.cd_attachment_file),
                subtitle = stringResource(
                    R.string.attachment_size,
                    attachment.mimeType,
                    FileSizes.format(attachment.sizeBytes),
                ),
                outgoing = outgoing,
                onClick = onOpen,
                modifier = modifier,
            )
    }
}

@Composable
private fun ImageAttachment(
    attachment: Attachment,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ratio = attachment.aspectRatio.coerceIn(MIN_RATIO, MAX_RATIO)
    AsyncImage(
        model = attachment.uri,
        contentDescription = stringResource(R.string.cd_attachment_image),
        contentScale = ContentScale.Crop,
        modifier = modifier
            .widthIn(max = MEDIA_MAX_WIDTH)
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClickLabel = stringResource(R.string.cd_attachment_image), onClick = onOpen),
    )
}

@Composable
private fun VideoAttachment(
    attachment: Attachment,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ratio = attachment.aspectRatio.coerceIn(MIN_RATIO, MAX_RATIO)
    Box(
        modifier = modifier
            .widthIn(max = MEDIA_MAX_WIDTH)
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClickLabel = stringResource(R.string.media_play), onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = attachment.uri,
            contentDescription = stringResource(R.string.cd_attachment_video),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = SCRIM_ALPHA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
            )
        }
        if (attachment.durationMillis > 0) {
            Text(
                text = formatDuration(attachment.durationMillis),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun AudioAttachment(
    attachment: Attachment,
    outgoing: Boolean,
    audio: AudioPlaybackController,
    modifier: Modifier = Modifier,
) {
    val playing = audio.playingUri.value == attachment.uri
    val duration = if (playing && audio.durationMillis.value > 0) {
        audio.durationMillis.value.toLong()
    } else {
        attachment.durationMillis
    }
    val position = if (playing) audio.positionMillis.value.toLong() else 0L
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val onSurface = if (outgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier.widthIn(min = AUDIO_MIN_WIDTH, max = MEDIA_MAX_WIDTH),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(onSurface.copy(alpha = CONTROL_ALPHA))
                .clickable(
                    onClickLabel = stringResource(
                        if (playing && !audio.isPaused.value) R.string.media_pause else R.string.media_play,
                    ),
                ) { audio.toggle(attachment.uri) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (playing && !audio.isPaused.value) {
                    Icons.Outlined.Pause
                } else {
                    Icons.Outlined.PlayArrow
                },
                contentDescription = null,
                tint = onSurface,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (playing) {
                Slider(
                    value = progress,
                    onValueChange = audio::seekTo,
                    modifier = Modifier.height(20.dp),
                )
            } else {
                LinearProgressIndicator(
                    progress = { 0f },
                    color = onSurface,
                    trackColor = onSurface.copy(alpha = TRACK_ALPHA),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (attachment.kind == AttachmentKind.VOICE) {
                    Icon(
                        imageVector = Icons.Outlined.Mic,
                        contentDescription = null,
                        tint = onSurface,
                        modifier = Modifier
                            .size(12.dp)
                            .padding(end = 2.dp),
                    )
                }
                Text(
                    text = if (duration > 0) {
                        formatDuration(if (playing) duration - position else duration)
                    } else {
                        stringResource(R.string.cd_attachment_audio)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurface,
                )
            }
        }
    }
}

@Composable
private fun IconAttachment(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    outgoing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onSurface = if (outgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .widthIn(max = MEDIA_MAX_WIDTH)
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(onSurface.copy(alpha = CONTROL_ALPHA))
            .clickable(onClickLabel = title, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = onSurface)
        Box(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurface.copy(alpha = SUBTITLE_ALPHA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val MEDIA_MAX_WIDTH = 260.dp
private val AUDIO_MIN_WIDTH = 200.dp
private const val MIN_RATIO = 0.6f
private const val MAX_RATIO = 1.9f
private const val SCRIM_ALPHA = 0.45f
private const val CONTROL_ALPHA = 0.15f
private const val TRACK_ALPHA = 0.3f
private const val SUBTITLE_ALPHA = 0.75f
