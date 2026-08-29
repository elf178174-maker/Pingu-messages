package app.pingu.messages.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pingu.messages.PinguApplication
import app.pingu.messages.domain.model.AppSettings
import app.pingu.messages.ui.screens.conversation.ConversationScreen
import app.pingu.messages.ui.screens.conversation.ConversationViewModel
import app.pingu.messages.ui.theme.PinguTheme

/**
 * A single conversation in its own window.
 *
 * This is what a notification bubble expands into, and what "open in a new window" uses on a
 * tablet or a desktop-mode display. It hosts the same conversation screen as the main activity, so
 * there is one implementation of the messaging UI rather than a reduced copy.
 */
class ConversationWindowActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as PinguApplication).container
        val threadId = intent?.getLongExtra(MainActivity.EXTRA_THREAD_ID, 0L) ?: 0L
        if (threadId <= 0L) {
            finish()
            return
        }

        setContent {
            val settings by container.settingsStore.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())

            PinguTheme(
                themeMode = settings.themeMode,
                accent = settings.accentColor,
                useDynamicColor = settings.dynamicColor,
                pureBlack = settings.pureBlackDarkMode,
            ) {
                val viewModel: ConversationViewModel = viewModel(
                    factory = PinguViewModels.conversation(container, threadId),
                )
                ConversationScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    // A bubble is a single conversation; deeper navigation belongs in the app.
                    onOpenMedia = { _, _ -> },
                    onForward = { },
                    onOpenConversationMedia = { },
                )
            }
        }
    }
}
