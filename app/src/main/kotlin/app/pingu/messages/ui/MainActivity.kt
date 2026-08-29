package app.pingu.messages.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import app.pingu.messages.PinguApplication
import app.pingu.messages.core.util.PhoneNumbers
import app.pingu.messages.domain.model.AppSettings
import app.pingu.messages.ui.navigation.PinguNavHost
import app.pingu.messages.ui.navigation.Routes
import app.pingu.messages.ui.theme.PinguTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The single activity that hosts the whole app.
 *
 * It has three jobs beyond showing the navigation graph: turning the intents other apps send
 * (`sms:` links, shared content, launcher shortcuts, notification taps) into a destination,
 * requesting the SMS role when the user asks for it, and gating the UI behind the optional app
 * lock.
 *
 * It extends `FragmentActivity` because `BiometricPrompt` requires one; everything else is Compose.
 */
class MainActivity : FragmentActivity() {

    private val container by lazy { (application as PinguApplication).container }

    private var pendingRoute: String? = null

    private val roleRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // The result code is unreliable across OEMs; the role itself is the source of truth.
        lifecycleScope.launch {
            if (container.defaultSmsAppManager.isDefault()) {
                container.syncRepository.syncAll()
                container.widgetUpdater.requestUpdate()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        var startDestination by mutableStateOf<String?>(null)
        splashScreen.setKeepOnScreenCondition { startDestination == null }

        lifecycleScope.launch {
            val settings = container.settingsStore.settings.first()
            startDestination = if (settings.onboardingComplete) {
                Routes.CONVERSATIONS
            } else {
                Routes.ONBOARDING
            }
        }

        pendingRoute = routeFor(intent)

        setContent {
            val settings by container.settingsStore.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            val destination = startDestination

            PinguTheme(
                themeMode = settings.themeMode,
                accent = settings.accentColor,
                useDynamicColor = settings.dynamicColor,
                pureBlack = settings.pureBlackDarkMode,
            ) {
                if (destination != null) {
                    val navController = rememberNavController()
                    val route = remember(destination) { pendingRoute }

                    LaunchedEffect(route) {
                        route?.let {
                            pendingRoute = null
                            navController.navigate(it)
                        }
                    }

                    AppLockGate(
                        enabled = settings.appLockEnabled,
                        activity = this,
                    ) {
                        PinguNavHost(
                            navController = navController,
                            container = container,
                            startDestination = destination,
                            onRequestDefaultSmsApp = ::requestDefaultSmsRole,
                            modifier = Modifier,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = routeFor(intent)
        // Recreating is the simplest correct way to apply a new destination without keeping a
        // navigation controller reference outside the composition.
        recreate()
    }

    private fun requestDefaultSmsRole() {
        val request = container.defaultSmsAppManager.createRequestIntent() ?: return
        runCatching { roleRequest.launch(request) }
    }

    /**
     * Maps an incoming intent to a destination.
     *
     * Handles the launcher shortcuts, notification taps, `sms:`/`smsto:` links from browsers and
     * dialers, and content shared from other apps.
     */
    private fun routeFor(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            ACTION_OPEN_CONVERSATION -> {
                val threadId = intent.getLongExtra(EXTRA_THREAD_ID, 0L)
                if (threadId > 0) Routes.conversation(threadId) else null
            }

            ACTION_COMPOSE -> Routes.newMessage()
            ACTION_SEARCH -> Routes.SEARCH

            Intent.ACTION_SENDTO, Intent.ACTION_VIEW -> {
                val address = addressFrom(intent.data)
                val body = intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: intent.getStringExtra(EXTRA_SMS_BODY)
                if (address.isNullOrBlank() && body.isNullOrBlank()) {
                    null
                } else {
                    Routes.newMessage(address = address, body = body)
                }
            }

            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE ->
                Routes.newMessage(body = intent.getStringExtra(Intent.EXTRA_TEXT))

            else -> null
        }
    }

    private fun addressFrom(uri: Uri?): String? {
        val raw = uri?.schemeSpecificPart ?: return null
        return PhoneNumbers.splitRecipients(raw).firstOrNull()
    }

    companion object {
        const val ACTION_OPEN_CONVERSATION = "app.pingu.messages.action.OPEN_CONVERSATION"
        const val ACTION_COMPOSE = "app.pingu.messages.action.COMPOSE"
        const val ACTION_SEARCH = "app.pingu.messages.action.SEARCH"
        const val EXTRA_THREAD_ID = "thread_id"

        /** The extra dialers use for a pre-filled message body on an `sms:` intent. */
        private const val EXTRA_SMS_BODY = "sms_body"
    }
}
