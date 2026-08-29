package app.pingu.messages.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pingu.messages.R

/**
 * About, privacy and the honest list of what a third-party SMS app cannot do.
 *
 * This screen exists because the alternative is users assuming a missing feature is a bug. Every
 * limitation here is a platform or protocol constraint, stated plainly, with what the app does
 * instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                title = { Text(stringResource(R.string.settings_limitations)) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Section(stringResource(R.string.settings_privacy_policy))
            Text(
                text = stringResource(R.string.about_privacy_body),
                style = MaterialTheme.typography.bodyMedium,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Section(stringResource(R.string.about_limitations_title))
            Limitation(
                title = stringResource(R.string.limitation_rcs_title),
                body = stringResource(R.string.limitation_rcs_body),
            )
            Limitation(
                title = stringResource(R.string.limitation_reactions_title),
                body = stringResource(R.string.limitation_reactions_body),
            )
            Limitation(
                title = stringResource(R.string.limitation_edit_title),
                body = stringResource(R.string.limitation_edit_body),
            )
            Limitation(
                title = stringResource(R.string.limitation_delete_title),
                body = stringResource(R.string.limitation_delete_body),
            )
            Limitation(
                title = stringResource(R.string.limitation_read_title),
                body = stringResource(R.string.limitation_read_body),
            )
            Limitation(
                title = stringResource(R.string.limitation_typing_title),
                body = stringResource(R.string.limitation_typing_body),
            )
            Limitation(
                title = stringResource(R.string.limitation_encryption_title),
                body = stringResource(R.string.limitation_encryption_body),
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(bottom = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun Limitation(title: String, body: String) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
