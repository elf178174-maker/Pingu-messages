package app.pingu.messages.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.pingu.messages.di.AppContainer
import app.pingu.messages.ui.screens.blocked.BlockedViewModel
import app.pingu.messages.ui.screens.conversation.ConversationViewModel
import app.pingu.messages.ui.screens.conversations.ConversationsViewModel
import app.pingu.messages.ui.screens.gallery.ConversationMediaViewModel
import app.pingu.messages.ui.screens.media.MediaViewerViewModel
import app.pingu.messages.ui.screens.newmessage.NewMessageViewModel
import app.pingu.messages.ui.screens.onboarding.OnboardingViewModel
import app.pingu.messages.ui.screens.scheduled.ScheduledViewModel
import app.pingu.messages.ui.screens.search.SearchViewModel
import app.pingu.messages.ui.screens.settings.SettingsViewModel

/**
 * View model construction.
 *
 * Each factory names exactly the collaborators its view model needs, which keeps the dependency of
 * every screen visible in one place and makes each view model constructible in a test with fakes.
 * Screen arguments (a thread id, the message being viewed) are constructor parameters rather than
 * saved-state lookups, so a view model cannot exist in an ambiguous state.
 */
object PinguViewModels {

    fun conversations(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            ConversationsViewModel(
                conversations = container.conversationRepository,
                folders = container.folderRepository,
                blocked = container.blockedNumberRepository,
                sync = container.syncRepository,
                settings = container.settingsStore,
                defaultSmsApp = container.defaultSmsAppManager,
                notifier = container.messageNotifier,
                shortcuts = container.conversationShortcutManager,
                widgets = container.widgetUpdater,
            )
        }
    }

    fun conversation(container: AppContainer, threadId: Long): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                ConversationViewModel(
                    threadId = threadId,
                    conversations = container.conversationRepository,
                    messages = container.messageRepository,
                    drafts = container.draftRepository,
                    scheduledMessages = container.scheduledMessageRepository,
                    scheduler = container.scheduledMessageScheduler,
                    blocked = container.blockedNumberRepository,
                    sender = container.messageSender,
                    sync = container.syncRepository,
                    settings = container.settingsStore,
                    sims = container.simDataSource,
                    notifier = container.messageNotifier,
                    widgets = container.widgetUpdater,
                    storage = container.storageMaintenance,
                    metadataReader = container.attachmentMetadataReader,
                    mmsCoordinator = container.mmsReceiveCoordinator,
                    recorder = container.voiceRecorder,
                    location = container.locationSharing,
                )
            }
        }

    fun newMessage(
        container: AppContainer,
        forwardMessageIds: List<Long> = emptyList(),
    ): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            NewMessageViewModel(
                contacts = container.contactsDataSource,
                conversations = container.conversationRepository,
                drafts = container.draftRepository,
                messages = container.messageRepository,
                forwardMessageIds = forwardMessageIds,
            )
        }
    }

    fun search(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            SearchViewModel(
                conversations = container.conversationRepository,
                messages = container.messageRepository,
                contactsDataSource = container.contactsDataSource,
                contactIndex = container.contactIndex,
            )
        }
    }

    fun settings(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            SettingsViewModel(
                settingsStore = container.settingsStore,
                sims = container.simDataSource,
                defaultSmsApp = container.defaultSmsAppManager,
                storage = container.storageMaintenance,
                backup = container.backupManager,
            )
        }
    }

    fun blocked(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            BlockedViewModel(
                blocked = container.blockedNumberRepository,
                conversations = container.conversationRepository,
            )
        }
    }

    fun scheduled(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            ScheduledViewModel(
                repository = container.scheduledMessageRepository,
                scheduler = container.scheduledMessageScheduler,
            )
        }
    }

    fun onboarding(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            OnboardingViewModel(
                settings = container.settingsStore,
                defaultSmsApp = container.defaultSmsAppManager,
                permissions = container.appPermissions,
            )
        }
    }

    fun mediaViewer(
        container: AppContainer,
        threadId: Long,
        messageId: Long,
        uri: String,
    ): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            MediaViewerViewModel(
                threadId = threadId,
                messageId = messageId,
                initialUri = uri,
                messages = container.messageRepository,
                storage = container.storageMaintenance,
            )
        }
    }

    fun conversationMedia(container: AppContainer, threadId: Long): ViewModelProvider.Factory =
        viewModelFactory {
            initializer {
                ConversationMediaViewModel(
                    threadId = threadId,
                    messages = container.messageRepository,
                )
            }
        }
}
