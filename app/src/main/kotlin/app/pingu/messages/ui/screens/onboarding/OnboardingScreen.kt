package app.pingu.messages.ui.screens.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pingu.messages.R
import app.pingu.messages.data.preferences.SettingsStore
import app.pingu.messages.platform.permission.AppPermissions
import app.pingu.messages.platform.permission.PermissionGroup
import app.pingu.messages.platform.system.DefaultSmsAppManager
import kotlinx.coroutines.launch

/** First-run state: which of the three things the user still has to decide. */
class OnboardingViewModel(
    private val settings: SettingsStore,
    private val defaultSmsApp: DefaultSmsAppManager,
    private val permissions: AppPermissions,
) : ViewModel() {

    fun isDefaultSmsApp(): Boolean = defaultSmsApp.isDefault()

    fun hasContacts(): Boolean = permissions.isGranted(PermissionGroup.CONTACTS)

    fun hasNotifications(): Boolean = permissions.isGranted(PermissionGroup.NOTIFICATIONS)

    fun complete() {
        viewModelScope.launch { settings.update { it.copy(onboardingComplete = true) } }
    }
}

/**
 * First launch.
 *
 * Three screens' worth of content on one, because a messaging app has exactly three things to say
 * before it can work: what it is, that Android only lets one app handle SMS, and which optional
 * permissions make it better. Everything after that is discoverable in the app itself.
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onRequestDefaultSmsApp: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDefault by remember { mutableStateOf(viewModel.isDefaultSmsApp()) }
    var hasContacts by remember { mutableStateOf(viewModel.hasContacts()) }
    var hasNotifications by remember { mutableStateOf(viewModel.hasNotifications()) }

    val requestContacts = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { hasContacts = viewModel.hasContacts() }

    val requestNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { hasNotifications = viewModel.hasNotifications() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.onboarding_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.size(4.dp))

        OnboardingStep(
            icon = Icons.Outlined.Sms,
            title = stringResource(R.string.onboarding_default_title),
            body = stringResource(R.string.onboarding_default_body),
            done = isDefault,
            doneLabel = stringResource(R.string.onboarding_default_done),
            actionLabel = stringResource(R.string.onboarding_default_action),
            onAction = {
                onRequestDefaultSmsApp()
                isDefault = viewModel.isDefaultSmsApp()
            },
        )

        OnboardingStep(
            icon = Icons.Outlined.Contacts,
            title = stringResource(R.string.permission_contacts_title),
            body = stringResource(R.string.permission_contacts_body),
            done = hasContacts,
            doneLabel = stringResource(R.string.permission_contacts_title),
            actionLabel = stringResource(R.string.action_continue),
            onAction = {
                requestContacts.launch(PermissionGroup.CONTACTS.permissions.toTypedArray())
            },
        )

        if (PermissionGroup.NOTIFICATIONS.permissions.isNotEmpty()) {
            OnboardingStep(
                icon = Icons.Outlined.Notifications,
                title = stringResource(R.string.permission_notifications_title),
                body = stringResource(R.string.permission_notifications_body),
                done = hasNotifications,
                doneLabel = stringResource(R.string.permission_notifications_title),
                actionLabel = stringResource(R.string.action_continue),
                onAction = {
                    requestNotifications.launch(
                        PermissionGroup.NOTIFICATIONS.permissions.toTypedArray(),
                    )
                },
            )
        }

        OnboardingStep(
            icon = Icons.Outlined.Lock,
            title = stringResource(R.string.onboarding_privacy_title),
            body = stringResource(R.string.onboarding_privacy_body),
            done = true,
            doneLabel = stringResource(R.string.onboarding_privacy_title),
            actionLabel = null,
            onAction = {},
        )

        Button(
            onClick = {
                viewModel.complete()
                onFinished()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onboarding_get_started))
        }

        TextButton(
            onClick = {
                viewModel.complete()
                onFinished()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_not_now))
        }
    }
}

@Composable
private fun OnboardingStep(
    icon: ImageVector,
    title: String,
    body: String,
    done: Boolean,
    doneLabel: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = if (done) Icons.Outlined.CheckCircle else icon,
            contentDescription = null,
            tint = if (done) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .padding(top = 2.dp)
                .size(24.dp),
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = if (done) doneLabel else title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (!done && actionLabel != null) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
