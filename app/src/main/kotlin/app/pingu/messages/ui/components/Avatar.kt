package app.pingu.messages.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pingu.messages.core.util.Avatars
import app.pingu.messages.domain.model.Recipient
import app.pingu.messages.ui.theme.PinguTheme
import coil.compose.AsyncImage

/**
 * A contact avatar: the photo when there is one, otherwise initials on a colour derived from the
 * number, so the same person always gets the same colour.
 *
 * Avatars are decorative here - the contact name is always beside them - so they are hidden from
 * screen readers rather than repeating the name.
 */
@Composable
fun ContactAvatar(
    recipient: Recipient?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val colors = PinguTheme.colors
    val initials = recipient?.let { Avatars.initials(it.displayName) }.orEmpty()
    val slot = recipient?.colorSlot ?: 0
    val background = colors.avatarBackgrounds.getOrElse(slot) { colors.avatarBackgrounds.first() }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        val photo = recipient?.photoUri
        when {
            photo != null -> AsyncImage(
                model = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )

            initials.isNotEmpty() -> Text(
                text = initials,
                color = colors.onAvatar,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * INITIALS_RATIO).sp,
            )

            else -> Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = colors.onAvatar,
                modifier = Modifier.size(size * ICON_RATIO),
            )
        }
    }
}

/**
 * A group avatar: the two most recent participants overlapped, which reads as "more than one
 * person" at a glance without needing a badge.
 */
@Composable
fun GroupAvatar(
    recipients: List<Recipient>,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    if (recipients.size < 2) {
        ContactAvatar(recipients.firstOrNull(), modifier, size)
        return
    }
    val colors = PinguTheme.colors
    Box(modifier = modifier.size(size).clearAndSetSemantics { }) {
        ContactAvatar(
            recipient = recipients[1],
            size = size * GROUP_SCALE,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(size * GROUP_SCALE + GROUP_RING)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            ContactAvatar(recipient = recipients[0], size = size * GROUP_SCALE)
        }
        if (recipients.size > 2) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(size * BADGE_SCALE)
                    .clip(CircleShape)
                    .background(colors.avatarBackgrounds.last()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Group,
                    contentDescription = null,
                    tint = colors.onAvatar,
                    modifier = Modifier.size(size * BADGE_ICON_SCALE),
                )
            }
        }
    }
}

private const val INITIALS_RATIO = 0.38f
private const val ICON_RATIO = 0.55f
private const val GROUP_SCALE = 0.68f
private const val BADGE_SCALE = 0.42f
private const val BADGE_ICON_SCALE = 0.26f
private val GROUP_RING = 3.dp
