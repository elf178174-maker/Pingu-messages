package app.pingu.messages.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.pingu.messages.R

/**
 * The optional app lock.
 *
 * Uses the platform biometric prompt, which falls back to the device PIN or pattern, so there is no
 * app-specific passcode to forget and no home-made credential storage. When the device has no
 * screen lock at all the gate opens rather than trapping the user out of their messages.
 */
@Composable
fun AppLockGate(
    enabled: Boolean,
    activity: FragmentActivity,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }

    val canAuthenticate = remember {
        BiometricManager.from(activity).canAuthenticate(ALLOWED_AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }
    var unlocked by remember { mutableStateOf(!canAuthenticate) }
    var prompting by remember { mutableStateOf(false) }

    val title = stringResource(R.string.app_lock_title)
    val subtitle = stringResource(R.string.app_lock_subtitle)

    fun authenticate() {
        if (prompting) return
        prompting = true
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    unlocked = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false
                }
            },
        )
        runCatching {
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
                .build()
            prompt.authenticate(info)
        }.onFailure {
            // Some platform versions reject the combined authenticator set; never trap the user
            // out of their messages because of it.
            prompting = false
            unlocked = true
        }
    }

    LaunchedEffect(canAuthenticate) {
        if (!unlocked) authenticate()
    }

    if (unlocked) {
        content()
    } else {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Button(
                    onClick = { authenticate() },
                    modifier = Modifier.padding(top = 24.dp),
                ) {
                    Text(stringResource(R.string.app_lock_unlock))
                }
            }
        }
    }
}

private const val ALLOWED_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
