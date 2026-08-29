package app.pingu.messages.ui.screens.scheduled

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.pingu.messages.R
import app.pingu.messages.core.util.PhoneNumbers
import app.pingu.messages.data.repository.ScheduledMessageRepository
import app.pingu.messages.domain.model.ScheduledMessage
import app.pingu.messages.domain.model.ScheduledFailureReason
import app.pingu.messages.domain.model.ScheduledMessageState
import app.pingu.messages.platform.scheduling.ScheduledMessageScheduler
import app.pingu.messages.ui.components.EmptyState
import app.pingu.messages.ui.util.TimeFormatting
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The scheduled queue, including entries that failed so the user can act on them. */
class ScheduledViewModel(
    private val repository: ScheduledMessageRepository,
    private val scheduler: ScheduledMessageScheduler,
) : ViewModel() {

    val messages: StateFlow<List<ScheduledMessage>> = repository.observeAll()
        .map { list -> list.filterNot { it.state == ScheduledMessageState.SENT } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), emptyList())

    fun cancel(message: ScheduledMessage) {
        viewModelScope.launch {
            scheduler.cancel(message.id)
            repository.cancel(message.id)
        }
    }

    fun delete(message: ScheduledMessage) {
        viewModelScope.launch {
            scheduler.cancel(message.id)
            repository.delete(message.id)
        }
    }

    fun retry(message: ScheduledMessage) {
        viewModelScope.launch {
            val newTime = System.currentTimeMillis() + RETRY_DELAY_MILLIS
            repository.reschedule(message.id, newTime)
            scheduler.schedule(message.id, newTime)
        }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
        const val RETRY_DELAY_MILLIS = 60_000L
    }
}

/**
 * Scheduled messages.
 *
 * Failures are shown with the reason the send failed rather than being silently dropped, and can be
 * retried in a minute's time - which is usually all a "no service" failure needs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledScreen(
    viewModel: ScheduledViewModel,
    onBack: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
                title = { Text(stringResource(R.string.scheduled_messages_title)) },
            )
        },
    ) { padding ->
        if (messages.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Schedule,
                title = stringResource(R.string.empty_scheduled_title),
                body = stringResource(R.string.empty_scheduled_body),
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                items(messages, key = { it.id }) { message ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = message.threadId > 0) {
                                onOpenConversation(message.threadId)
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Text(
                            text = message.recipients.joinToString(", ") {
                                PhoneNumbers.formatForDisplay(it)
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = message.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Row(
                            modifier = Modifier.padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = when (message.state) {
                                    ScheduledMessageState.FAILED ->
                                        failureText(message.failureReason)

                                    ScheduledMessageState.CANCELLED ->
                                        stringResource(R.string.schedule_cancelled)

                                    else -> stringResource(
                                        R.string.schedule_scheduled_for,
                                        TimeFormatting.dayAndTime(context, message.scheduledAt),
                                    )
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (message.state == ScheduledMessageState.FAILED) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.weight(1f),
                            )
                            when (message.state) {
                                ScheduledMessageState.PENDING ->
                                    TextButton(onClick = { viewModel.cancel(message) }) {
                                        Text(stringResource(R.string.action_cancel))
                                    }

                                ScheduledMessageState.FAILED ->
                                    TextButton(onClick = { viewModel.retry(message) }) {
                                        Text(stringResource(R.string.action_retry))
                                    }

                                else ->
                                    TextButton(onClick = { viewModel.delete(message) }) {
                                        Text(stringResource(R.string.action_delete))
                                    }
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * Turns a stored failure key into a sentence.
 *
 * Anything unrecognised - including a reason written by an older version of the app - falls back to
 * the generic message rather than showing the raw key.
 */
@Composable
private fun failureText(reason: String?): String = stringResource(
    when (reason) {
        ScheduledFailureReason.NOT_DEFAULT_SMS_APP -> R.string.error_not_default_app
        ScheduledFailureReason.NO_SIM -> R.string.error_no_sim
        ScheduledFailureReason.NO_SERVICE -> R.string.error_send_failed_no_service
        ScheduledFailureReason.NO_MOBILE_DATA -> R.string.error_mms_no_data
        ScheduledFailureReason.TOO_LARGE -> R.string.schedule_failed_too_large
        ScheduledFailureReason.PERMISSION_REQUIRED -> R.string.error_permission_generic
        ScheduledFailureReason.ATTACHMENT_UNREADABLE -> R.string.error_attachment_unreadable
        ScheduledFailureReason.NO_HANDLING_APP -> R.string.error_no_app_for_action
        else -> R.string.error_send_failed_generic
    },
)
