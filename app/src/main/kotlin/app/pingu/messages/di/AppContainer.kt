package app.pingu.messages.di

import android.content.Context
import app.pingu.messages.data.contacts.ContactIndex
import app.pingu.messages.data.contacts.ContactsDataSource
import app.pingu.messages.data.local.PinguDatabase
import app.pingu.messages.data.preferences.SettingsStore
import app.pingu.messages.data.repository.BlockedNumberRepository
import app.pingu.messages.data.repository.ConversationRepository
import app.pingu.messages.data.repository.DraftRepository
import app.pingu.messages.data.repository.FolderRepository
import app.pingu.messages.data.repository.MessageRepository
import app.pingu.messages.data.repository.ScheduledMessageRepository
import app.pingu.messages.data.repository.SyncRepository
import app.pingu.messages.data.telephony.AttachmentMetadataReader
import app.pingu.messages.data.telephony.MmsProviderDataSource
import app.pingu.messages.data.telephony.SimDataSource
import app.pingu.messages.data.telephony.SmsProviderDataSource
import app.pingu.messages.data.telephony.ThreadsDataSource
import app.pingu.messages.platform.backup.BackupManager
import app.pingu.messages.platform.media.AppFileStore
import app.pingu.messages.platform.media.LocationSharing
import app.pingu.messages.platform.media.StorageMaintenance
import app.pingu.messages.platform.media.VoiceRecorder
import app.pingu.messages.platform.messaging.IncomingMessageHandler
import app.pingu.messages.platform.messaging.MessageSender
import app.pingu.messages.platform.mms.MmsAttachmentEncoder
import app.pingu.messages.platform.mms.MmsReceiveCoordinator
import app.pingu.messages.platform.mms.MmsTransport
import app.pingu.messages.platform.notification.AvatarBitmaps
import app.pingu.messages.platform.notification.MessageNotifier
import app.pingu.messages.platform.permission.AppPermissions
import app.pingu.messages.platform.scheduling.ScheduledMessageDispatcher
import app.pingu.messages.platform.scheduling.ScheduledMessageScheduler
import app.pingu.messages.platform.shortcut.ConversationShortcutManager
import app.pingu.messages.platform.sms.SmsTransport
import app.pingu.messages.platform.system.DefaultSmsAppManager
import app.pingu.messages.platform.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first

/**
 * The application's object graph.
 *
 * Dependencies are wired by hand rather than by an annotation processor. For an app this size that
 * is a deliberate trade: the graph is one readable file, construction order is explicit, there is no
 * generated code to step through when something is null, and the build has one fewer annotation
 * processor to keep in step with the Kotlin version. Every class takes its collaborators through
 * its constructor, so each is testable in isolation and swapping this container for Hilt later
 * would touch only this file.
 *
 * Everything is lazy: opening the database, reading contacts and touching telephony all cost real
 * time, and a broadcast receiver that only needs the settings store should not pay for them.
 */
class AppContainer(
    private val context: Context,
    private val applicationScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    // ---- Storage ------------------------------------------------------------------------------

    val database: PinguDatabase by lazy { PinguDatabase.build(context) }

    val settingsStore: SettingsStore by lazy { SettingsStore(context) }

    val fileStore: AppFileStore by lazy { AppFileStore(context) }

    // ---- Telephony and contacts ---------------------------------------------------------------

    val smsProviderDataSource: SmsProviderDataSource by lazy { SmsProviderDataSource(context) }

    val mmsProviderDataSource: MmsProviderDataSource by lazy { MmsProviderDataSource(context) }

    val threadsDataSource: ThreadsDataSource by lazy { ThreadsDataSource(context) }

    val simDataSource: SimDataSource by lazy { SimDataSource(context) }

    val contactsDataSource: ContactsDataSource by lazy { ContactsDataSource(context) }

    val contactIndex: ContactIndex by lazy {
        ContactIndex(context, contactsDataSource, applicationScope)
    }

    val attachmentMetadataReader: AttachmentMetadataReader by lazy {
        AttachmentMetadataReader(context)
    }

    // ---- Repositories -------------------------------------------------------------------------

    val syncRepository: SyncRepository by lazy {
        SyncRepository(
            context = context,
            database = database,
            threads = threadsDataSource,
            sms = smsProviderDataSource,
            mms = mmsProviderDataSource,
            metadataReader = attachmentMetadataReader,
            ioDispatcher = ioDispatcher,
        )
    }

    val conversationRepository: ConversationRepository by lazy {
        ConversationRepository(
            database = database,
            contacts = contactIndex,
            threads = threadsDataSource,
            sms = smsProviderDataSource,
            mms = mmsProviderDataSource,
            ioDispatcher = ioDispatcher,
        )
    }

    val messageRepository: MessageRepository by lazy {
        MessageRepository(
            context = context,
            database = database,
            sms = smsProviderDataSource,
            mms = mmsProviderDataSource,
            ioDispatcher = ioDispatcher,
        )
    }

    val draftRepository: DraftRepository by lazy {
        DraftRepository(context, database, ioDispatcher)
    }

    val scheduledMessageRepository: ScheduledMessageRepository by lazy {
        ScheduledMessageRepository(database, ioDispatcher)
    }

    val blockedNumberRepository: BlockedNumberRepository by lazy {
        BlockedNumberRepository(context, database, ioDispatcher)
    }

    val folderRepository: FolderRepository by lazy {
        FolderRepository(database, ioDispatcher)
    }

    // ---- Platform integrations ----------------------------------------------------------------

    val defaultSmsAppManager: DefaultSmsAppManager by lazy { DefaultSmsAppManager(context) }

    val appPermissions: AppPermissions by lazy { AppPermissions(context) }

    val avatarBitmaps: AvatarBitmaps by lazy { AvatarBitmaps(context) }

    val conversationShortcutManager: ConversationShortcutManager by lazy {
        ConversationShortcutManager(context, avatarBitmaps)
    }

    val messageNotifier: MessageNotifier by lazy {
        MessageNotifier(context, avatarBitmaps, conversationShortcutManager)
    }

    val widgetUpdater: WidgetUpdater by lazy { WidgetUpdater(context) }

    val smsTransport: SmsTransport by lazy { SmsTransport(context, simDataSource) }

    val mmsTransport: MmsTransport by lazy { MmsTransport(context, simDataSource, fileStore) }

    private val mmsAttachmentEncoder: MmsAttachmentEncoder by lazy { MmsAttachmentEncoder(context) }

    val messageSender: MessageSender by lazy {
        MessageSender(
            context = context,
            settingsProvider = { settingsStore.settings.first() },
            sims = simDataSource,
            threads = threadsDataSource,
            smsProvider = smsProviderDataSource,
            mmsProvider = mmsProviderDataSource,
            smsTransport = smsTransport,
            mmsTransport = mmsTransport,
            attachmentEncoder = mmsAttachmentEncoder,
            syncRepository = syncRepository,
            defaultSmsApp = defaultSmsAppManager,
            ioDispatcher = ioDispatcher,
        )
    }

    val incomingMessageHandler: IncomingMessageHandler by lazy {
        IncomingMessageHandler(
            syncRepository = syncRepository,
            conversations = conversationRepository,
            messages = messageRepository,
            blocked = blockedNumberRepository,
            contacts = contactIndex,
            settings = settingsStore,
            notifier = messageNotifier,
            shortcuts = conversationShortcutManager,
            widgets = widgetUpdater,
        )
    }

    val mmsReceiveCoordinator: MmsReceiveCoordinator by lazy {
        MmsReceiveCoordinator(
            context = context,
            mmsProvider = mmsProviderDataSource,
            threads = threadsDataSource,
            transport = mmsTransport,
            sims = simDataSource,
            settings = settingsStore,
            syncRepository = syncRepository,
            conversations = conversationRepository,
            messages = messageRepository,
            incoming = incomingMessageHandler,
            notifier = messageNotifier,
        )
    }

    val scheduledMessageScheduler: ScheduledMessageScheduler by lazy {
        ScheduledMessageScheduler(context, scheduledMessageRepository)
    }

    val scheduledMessageDispatcher: ScheduledMessageDispatcher by lazy {
        ScheduledMessageDispatcher(
            repository = scheduledMessageRepository,
            sender = messageSender,
            notifier = messageNotifier,
            scheduler = scheduledMessageScheduler,
        )
    }

    val voiceRecorder: VoiceRecorder by lazy { VoiceRecorder(context, fileStore) }

    val locationSharing: LocationSharing by lazy { LocationSharing(context) }

    val storageMaintenance: StorageMaintenance by lazy {
        StorageMaintenance(context, database, fileStore, settingsStore, ioDispatcher)
    }

    val backupManager: BackupManager by lazy {
        BackupManager(
            context = context,
            database = database,
            settings = settingsStore,
            smsProvider = smsProviderDataSource,
            threads = threadsDataSource,
            ioDispatcher = ioDispatcher,
        )
    }
}
