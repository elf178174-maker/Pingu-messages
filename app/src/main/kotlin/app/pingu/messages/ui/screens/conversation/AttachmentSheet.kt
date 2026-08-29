package app.pingu.messages.ui.screens.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ContactPage
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pingu.messages.R

/** The kinds of attachment the sheet can start. */
enum class AttachmentSource { CAMERA_PHOTO, CAMERA_VIDEO, GALLERY, FILES, AUDIO, CONTACT, LOCATION }

/**
 * The attachment picker.
 *
 * Photos and files come from the system pickers, which need no storage permission at all and give
 * the user a chance to share only the items they choose. Camera capture hands off to the system
 * camera app rather than embedding a viewfinder, so the app never has to hold the camera permission
 * and the user gets the camera they already know.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentSheet(
    onSelect: (AttachmentSource) -> Unit,
    onDismiss: () -> Unit,
    locationAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val options = buildList {
        add(Option(AttachmentSource.GALLERY, Icons.Outlined.Image, R.string.attachment_gallery))
        add(Option(AttachmentSource.CAMERA_PHOTO, Icons.Outlined.CameraAlt, R.string.attachment_camera))
        add(Option(AttachmentSource.CAMERA_VIDEO, Icons.Outlined.Videocam, R.string.attachment_video))
        add(Option(AttachmentSource.FILES, Icons.Outlined.Folder, R.string.attachment_files))
        add(Option(AttachmentSource.AUDIO, Icons.Outlined.AudioFile, R.string.attachment_audio))
        add(Option(AttachmentSource.CONTACT, Icons.Outlined.ContactPage, R.string.attachment_contact))
        if (locationAvailable) {
            add(Option(AttachmentSource.LOCATION, Icons.Outlined.Place, R.string.attachment_location))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 92.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            items(options, key = { it.source.name }) { option ->
                AttachmentOption(option = option, onClick = { onSelect(option.source) })
            }
        }
    }
}

private data class Option(
    val source: AttachmentSource,
    val icon: ImageVector,
    val labelRes: Int,
)

@Composable
private fun AttachmentOption(option: Option, onClick: () -> Unit) {
    val label = stringResource(option.labelRes)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
