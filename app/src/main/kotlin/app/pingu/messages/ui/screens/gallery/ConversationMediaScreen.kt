package app.pingu.messages.ui.screens.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.pingu.messages.R
import app.pingu.messages.core.util.FileSizes
import app.pingu.messages.data.repository.MessageRepository
import app.pingu.messages.domain.model.Attachment
import app.pingu.messages.ui.components.EmptyState
import app.pingu.messages.ui.util.IntentActions
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Everything shared in one conversation, split into media and other files. */
class ConversationMediaViewModel(
    private val threadId: Long,
    private val messages: MessageRepository,
) : ViewModel() {

    private val _attachments = MutableStateFlow<List<Attachment>>(emptyList())
    val attachments: StateFlow<List<Attachment>> = _attachments.asStateFlow()

    init {
        viewModelScope.launch {
            _attachments.value = messages.attachmentsInThread(threadId)
        }
    }
}

/**
 * The media and files of a conversation.
 *
 * A grid for anything with a thumbnail, a list for the rest. Tapping a photo opens the same
 * full-screen viewer as tapping it in the thread, so the two paths behave identically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationMediaScreen(
    viewModel: ConversationMediaViewModel,
    onBack: () -> Unit,
    onOpenMedia: (messageId: Long, uri: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }

    val media = remember(attachments) { attachments.filter { it.kind.isVisualMedia } }
    val files = remember(attachments) { attachments.filterNot { it.kind.isVisualMedia } }

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
                title = { Text(stringResource(R.string.conversation_menu_media)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(stringResource(R.string.attachment_gallery)) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(stringResource(R.string.attachment_files)) },
                )
            }

            val items = if (tab == 0) media else files
            if (items.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = stringResource(R.string.empty_media_title),
                    body = stringResource(R.string.empty_media_body),
                )
            } else if (tab == 0) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 108.dp),
                    contentPadding = PaddingValues(2.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { it.id }) { attachment ->
                        Box(
                            modifier = Modifier
                                .padding(2.dp)
                                .aspectRatio(1f)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { onOpenMedia(attachment.messageId, attachment.uri) },
                        ) {
                            AsyncImage(
                                model = attachment.uri,
                                contentDescription = attachment.fileName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (attachment.kind == app.pingu.messages.domain.model.AttachmentKind.VIDEO) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(32.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(1),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { it.id }) { attachment ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { IntentActions.openAttachment(context, attachment) }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Description, null)
                            Column(modifier = Modifier.padding(start = 16.dp)) {
                                Text(
                                    text = attachment.fileName
                                        ?: stringResource(R.string.cd_attachment_file),
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.attachment_size,
                                        attachment.mimeType,
                                        FileSizes.format(attachment.sizeBytes),
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
