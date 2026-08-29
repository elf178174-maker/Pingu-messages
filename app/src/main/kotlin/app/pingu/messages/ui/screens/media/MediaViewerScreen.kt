package app.pingu.messages.ui.screens.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pingu.messages.R
import app.pingu.messages.domain.model.AttachmentKind
import app.pingu.messages.ui.components.ConfirmDialog
import app.pingu.messages.ui.components.VideoPlayer
import app.pingu.messages.ui.components.ZoomableImage
import app.pingu.messages.ui.util.IntentActions

/**
 * Full-screen media viewer.
 *
 * Every photo and video in the conversation is in the pager, so swiping moves through the thread's
 * media in order rather than dead-ending on the one that was tapped. The chrome hides on a tap so
 * the picture gets the whole screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    viewModel: MediaViewerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var chromeVisible by remember { mutableStateOf(true) }
    var pendingDeletion by remember { mutableStateOf(false) }

    val savedMessage = stringResource(R.string.media_viewer_saved)
    val failedMessage = stringResource(R.string.media_viewer_save_failed)

    val pagerState = rememberPagerState(
        initialPage = state.initialIndex.coerceAtLeast(0),
        pageCount = { state.attachments.size },
    )

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val current = state.attachments.getOrNull(pagerState.currentPage)

    Scaffold(
        modifier = modifier,
        containerColor = Color.Black,
        topBar = {
            if (chromeVisible) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                stringResource(R.string.action_back),
                                tint = Color.White,
                            )
                        }
                    },
                    title = {
                        Text(
                            text = stringResource(
                                R.string.media_viewer_title,
                                pagerState.currentPage + 1,
                                state.attachments.size.coerceAtLeast(1),
                            ),
                            color = Color.White,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                    actions = {
                        IconButton(
                            onClick = { current?.let { IntentActions.shareAttachment(context, it) } },
                        ) {
                            Icon(
                                Icons.Outlined.Share,
                                stringResource(R.string.media_viewer_share),
                                tint = Color.White,
                            )
                        }
                        IconButton(
                            onClick = { current?.let { viewModel.save(it, savedMessage, failedMessage) } },
                        ) {
                            Icon(
                                Icons.Outlined.Download,
                                stringResource(R.string.media_save),
                                tint = Color.White,
                            )
                        }
                        IconButton(onClick = { pendingDeletion = true }) {
                            Icon(
                                Icons.Outlined.Delete,
                                stringResource(R.string.media_viewer_delete),
                                tint = Color.White,
                            )
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (state.attachments.isEmpty()) return@Box
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val attachment = state.attachments[page]
                if (attachment.kind == AttachmentKind.VIDEO) {
                    VideoPlayer(
                        uri = attachment.uri,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    ZoomableImage(
                        model = attachment.uri,
                        contentDescription = attachment.fileName,
                        onTap = { chromeVisible = !chromeVisible },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    if (pendingDeletion) {
        ConfirmDialog(
            title = stringResource(R.string.media_viewer_delete),
            body = stringResource(R.string.delete_message_body),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                pendingDeletion = false
                viewModel.deleteCurrentMessage()
                onBack()
            },
            onDismiss = { pendingDeletion = false },
        )
    }
}
