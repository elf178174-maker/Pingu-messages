package app.pingu.messages.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pingu.messages.R
import app.pingu.messages.core.util.FileSizes
import app.pingu.messages.domain.model.AccentColor
import app.pingu.messages.domain.model.AppSettings
import app.pingu.messages.domain.model.BubbleShape
import app.pingu.messages.domain.model.GroupMessagingMode
import app.pingu.messages.domain.model.NotificationPrivacy
import app.pingu.messages.domain.model.SwipeAction
import app.pingu.messages.domain.model.ThemeMode
import app.pingu.messages.platform.backup.BackupManager
import app.pingu.messages.platform.notification.NotificationChannels
import app.pingu.messages.platform.permission.PermissionGroup
import app.pingu.messages.ui.components.SettingsRow
import app.pingu.messages.ui.components.SettingsSection
import app.pingu.messages.ui.components.SettingsSwitchRow
import app.pingu.messages.ui.components.SingleChoiceDialog
import app.pingu.messages.ui.util.IntentActions

/** Which single-choice dialog is open, if any. */
private enum class SettingsDialog { THEME, ACCENT, BUBBLE, PRIVACY, SIM, GROUP, SWIPE_RIGHT, SWIPE_LEFT, RETENTION }

/**
 * The settings screen.
 *
 * Sections follow what a person is trying to change, not what layer of the app owns the value:
 * appearance, notifications, messaging, privacy, storage, about. Anything the platform owns -
 * notification sounds, the SMS role - links out to the system screen instead of pretending to own
 * it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenBlocked: () -> Unit,
    onOpenScheduled: () -> Unit,
    onOpenAbout: () -> Unit,
    onRequestDefaultSmsApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    val cacheCleared = stringResource(R.string.settings_cache_cleared)
    val exportRunning = stringResource(R.string.settings_export_running)
    val exportDone = stringResource(R.string.settings_export_done)
    val importDone = stringResource(R.string.settings_import_done)
    val importFailed = stringResource(R.string.settings_import_failed)

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupManager.MIME_TYPE),
    ) { uri ->
        uri?.let {
            viewModel.export(it, includeMessages = true, exportRunning, exportDone, importFailed)
        }
    }

    val requestPhoneState = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refresh() }

    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.import(it, restoreMessages = true, importDone, importFailed) }
    }

    LaunchedEffect(state.resultMessage) {
        state.resultMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeResult()
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
                title = { Text(stringResource(R.string.settings_title)) },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            if (!state.isDefaultSmsApp) {
                SettingsRow(
                    title = stringResource(R.string.onboarding_default_action),
                    summary = stringResource(R.string.default_app_banner_body),
                    icon = Icons.Outlined.Info,
                    onClick = onRequestDefaultSmsApp,
                )
                HorizontalDivider()
            }

            SettingsSection(stringResource(R.string.settings_section_appearance)) {
                SettingsRow(
                    title = stringResource(R.string.settings_theme),
                    summary = themeLabel(state.settings.themeMode),
                    icon = Icons.Outlined.Brush,
                    onClick = { dialog = SettingsDialog.THEME },
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    summary = stringResource(R.string.settings_dynamic_color_summary),
                    icon = Icons.Outlined.Palette,
                    checked = state.settings.dynamicColor,
                    enabled = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S,
                    onCheckedChange = { value -> viewModel.update { it.copy(dynamicColor = value) } },
                )
                SettingsRow(
                    title = stringResource(R.string.settings_accent),
                    summary = accentLabel(state.settings.accentColor),
                    enabled = !state.settings.dynamicColor ||
                        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S,
                    onClick = { dialog = SettingsDialog.ACCENT },
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_pure_black),
                    summary = stringResource(R.string.settings_pure_black_summary),
                    checked = state.settings.pureBlackDarkMode,
                    onCheckedChange = { value -> viewModel.update { it.copy(pureBlackDarkMode = value) } },
                )
                SettingsRow(
                    title = stringResource(R.string.settings_bubble_style),
                    summary = bubbleLabel(state.settings.bubbleShape),
                    onClick = { dialog = SettingsDialog.BUBBLE },
                )
            }

            SettingsSection(stringResource(R.string.settings_section_notifications)) {
                SettingsRow(
                    title = stringResource(R.string.settings_notifications_system),
                    summary = stringResource(R.string.settings_notifications_system_summary),
                    icon = Icons.Outlined.Notifications,
                    onClick = {
                        IntentActions.openNotificationSettings(context, NotificationChannels.MESSAGES)
                    },
                )
                SettingsRow(
                    title = stringResource(R.string.settings_notification_privacy),
                    summary = privacyLabel(state.settings.notificationPrivacy),
                    onClick = { dialog = SettingsDialog.PRIVACY },
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_notification_vibrate),
                    checked = state.settings.notificationVibrate,
                    onCheckedChange = { value -> viewModel.update { it.copy(notificationVibrate = value) } },
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_notification_bubbles),
                    summary = stringResource(R.string.settings_notification_bubbles_summary),
                    checked = state.settings.conversationBubbles,
                    enabled = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R,
                    onCheckedChange = { value -> viewModel.update { it.copy(conversationBubbles = value) } },
                )
            }

            SettingsSection(stringResource(R.string.settings_section_messaging)) {
                // A dual-SIM phone cannot list its SIMs until the phone-state permission is
                // granted, so the offer to grant it has to come before the picker can exist.
                if (state.canAskForSimAccess) {
                    SettingsRow(
                        title = stringResource(R.string.permission_phone_title),
                        summary = stringResource(R.string.permission_phone_body),
                        icon = Icons.Outlined.SimCard,
                        onClick = {
                            requestPhoneState.launch(
                                PermissionGroup.PHONE_STATE.permissions.toTypedArray(),
                            )
                        },
                    )
                }
                if (state.sims.size > 1) {
                    SettingsRow(
                        title = stringResource(R.string.settings_default_sim),
                        summary = state.sims
                            .firstOrNull { it.subscriptionId == state.settings.defaultSubscriptionId }
                            ?.label
                            ?: stringResource(R.string.settings_default_sim_ask),
                        icon = Icons.Outlined.SimCard,
                        onClick = { dialog = SettingsDialog.SIM },
                    )
                }
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_delivery_reports),
                    summary = stringResource(R.string.settings_delivery_reports_summary),
                    checked = state.settings.deliveryReports,
                    onCheckedChange = { value -> viewModel.update { it.copy(deliveryReports = value) } },
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_auto_download_mms),
                    checked = state.settings.autoDownloadMms,
                    onCheckedChange = { value -> viewModel.update { it.copy(autoDownloadMms = value) } },
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_auto_download_mms_roaming),
                    checked = state.settings.autoDownloadMmsWhileRoaming,
                    enabled = state.settings.autoDownloadMms,
                    onCheckedChange = { value ->
                        viewModel.update { it.copy(autoDownloadMmsWhileRoaming = value) }
                    },
                )
                SettingsRow(
                    title = stringResource(R.string.settings_group_mms),
                    summary = groupLabel(state.settings.groupMessagingMode),
                    onClick = { dialog = SettingsDialog.GROUP },
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_split_long_sms),
                    checked = state.settings.splitLongMessages,
                    onCheckedChange = { value -> viewModel.update { it.copy(splitLongMessages = value) } },
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_reaction_fallback),
                    summary = stringResource(R.string.settings_reaction_fallback_summary),
                    checked = state.settings.reactionTextFallback,
                    onCheckedChange = { value -> viewModel.update { it.copy(reactionTextFallback = value) } },
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_quote_replies),
                    checked = state.settings.quoteWhenReplying,
                    onCheckedChange = { value -> viewModel.update { it.copy(quoteWhenReplying = value) } },
                )
                SettingsRow(
                    title = stringResource(R.string.settings_scheduled),
                    icon = Icons.Outlined.Schedule,
                    onClick = onOpenScheduled,
                )
                SettingsRow(
                    title = stringResource(R.string.settings_swipe_right),
                    summary = swipeLabel(state.settings.swipeRightAction),
                    onClick = { dialog = SettingsDialog.SWIPE_RIGHT },
                )
                SettingsRow(
                    title = stringResource(R.string.settings_swipe_left),
                    summary = swipeLabel(state.settings.swipeLeftAction),
                    onClick = { dialog = SettingsDialog.SWIPE_LEFT },
                )
            }

            SettingsSection(stringResource(R.string.settings_section_privacy)) {
                SettingsRow(
                    title = stringResource(R.string.settings_blocked_numbers),
                    icon = Icons.Outlined.Block,
                    onClick = onOpenBlocked,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_spam_filter),
                    checked = state.settings.spamFilterEnabled,
                    onCheckedChange = { value -> viewModel.update { it.copy(spamFilterEnabled = value) } },
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_app_lock),
                    summary = stringResource(R.string.settings_app_lock_summary),
                    icon = Icons.Outlined.PrivacyTip,
                    checked = state.settings.appLockEnabled,
                    onCheckedChange = { value -> viewModel.update { it.copy(appLockEnabled = value) } },
                )
            }

            SettingsSection(stringResource(R.string.settings_section_storage)) {
                SettingsRow(
                    title = stringResource(R.string.settings_storage_attachments),
                    summary = FileSizes.format(state.storage.attachmentBytes),
                    icon = Icons.Outlined.Storage,
                )
                SettingsRow(
                    title = stringResource(R.string.settings_storage_cache),
                    summary = FileSizes.format(state.storage.cacheBytes),
                    onClick = { viewModel.clearCache(cacheCleared) },
                    icon = Icons.Outlined.DeleteSweep,
                )
                SettingsRow(
                    title = stringResource(R.string.settings_media_auto_delete),
                    summary = retentionLabel(state.settings.autoDeleteMediaDays),
                    onClick = { dialog = SettingsDialog.RETENTION },
                )
                SettingsRow(
                    title = stringResource(R.string.settings_export),
                    summary = stringResource(R.string.settings_export_summary),
                    icon = Icons.Outlined.Upload,
                    onClick = { createBackup.launch(BackupManager.DEFAULT_FILE_NAME) },
                )
                SettingsRow(
                    title = stringResource(R.string.settings_import),
                    icon = Icons.Outlined.Download,
                    onClick = { openBackup.launch(arrayOf(BackupManager.MIME_TYPE, "*/*")) },
                )
            }

            SettingsSection(stringResource(R.string.settings_section_about)) {
                SettingsRow(
                    title = stringResource(R.string.settings_limitations),
                    icon = Icons.Outlined.Info,
                    onClick = onOpenAbout,
                )
                SettingsRow(
                    title = stringResource(R.string.settings_project),
                    icon = Icons.Outlined.Code,
                    onClick = { IntentActions.openUri(context, PROJECT_URL) },
                )
                SettingsRow(
                    title = stringResource(R.string.settings_version),
                    summary = versionName(context),
                )
            }
        }
    }

    SettingsDialogs(
        dialog = dialog,
        state = state,
        onDismiss = { dialog = null },
        onUpdate = viewModel::update,
    )
}

@Composable
private fun SettingsDialogs(
    dialog: SettingsDialog?,
    state: SettingsUiState,
    onDismiss: () -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
) {
    when (dialog) {
        SettingsDialog.THEME -> SingleChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries,
            selected = state.settings.themeMode,
            label = { themeLabel(it) },
            onSelect = { value -> onUpdate { it.copy(themeMode = value) } },
            onDismiss = onDismiss,
        )

        SettingsDialog.ACCENT -> SingleChoiceDialog(
            title = stringResource(R.string.settings_accent),
            options = AccentColor.entries,
            selected = state.settings.accentColor,
            label = { accentLabel(it) },
            onSelect = { value -> onUpdate { it.copy(accentColor = value) } },
            onDismiss = onDismiss,
        )

        SettingsDialog.BUBBLE -> SingleChoiceDialog(
            title = stringResource(R.string.settings_bubble_style),
            options = BubbleShape.entries,
            selected = state.settings.bubbleShape,
            label = { bubbleLabel(it) },
            onSelect = { value -> onUpdate { it.copy(bubbleShape = value) } },
            onDismiss = onDismiss,
        )

        SettingsDialog.PRIVACY -> SingleChoiceDialog(
            title = stringResource(R.string.settings_notification_privacy),
            options = NotificationPrivacy.entries,
            selected = state.settings.notificationPrivacy,
            label = { privacyLabel(it) },
            onSelect = { value -> onUpdate { it.copy(notificationPrivacy = value) } },
            onDismiss = onDismiss,
        )

        SettingsDialog.SIM -> SingleChoiceDialog(
            title = stringResource(R.string.settings_default_sim),
            options = listOf(AppSettings.SUBSCRIPTION_ASK) + state.sims.map { it.subscriptionId },
            selected = state.settings.defaultSubscriptionId,
            label = { id ->
                state.sims.firstOrNull { it.subscriptionId == id }?.label
                    ?: stringResource(R.string.settings_default_sim_ask)
            },
            onSelect = { value -> onUpdate { it.copy(defaultSubscriptionId = value) } },
            onDismiss = onDismiss,
        )

        SettingsDialog.GROUP -> SingleChoiceDialog(
            title = stringResource(R.string.settings_group_mms),
            options = GroupMessagingMode.entries,
            selected = state.settings.groupMessagingMode,
            label = { groupLabel(it) },
            onSelect = { value -> onUpdate { it.copy(groupMessagingMode = value) } },
            onDismiss = onDismiss,
        )

        SettingsDialog.SWIPE_RIGHT -> SingleChoiceDialog(
            title = stringResource(R.string.settings_swipe_right),
            options = SwipeAction.entries,
            selected = state.settings.swipeRightAction,
            label = { swipeLabel(it) },
            onSelect = { value -> onUpdate { it.copy(swipeRightAction = value) } },
            onDismiss = onDismiss,
        )

        SettingsDialog.SWIPE_LEFT -> SingleChoiceDialog(
            title = stringResource(R.string.settings_swipe_left),
            options = SwipeAction.entries,
            selected = state.settings.swipeLeftAction,
            label = { swipeLabel(it) },
            onSelect = { value -> onUpdate { it.copy(swipeLeftAction = value) } },
            onDismiss = onDismiss,
        )

        SettingsDialog.RETENTION -> SingleChoiceDialog(
            title = stringResource(R.string.settings_media_auto_delete),
            options = RETENTION_OPTIONS,
            selected = state.settings.autoDeleteMediaDays,
            label = { retentionLabel(it) },
            onSelect = { value -> onUpdate { it.copy(autoDeleteMediaDays = value) } },
            onDismiss = onDismiss,
        )

        null -> Unit
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    },
)

@Composable
private fun accentLabel(accent: AccentColor): String = when (accent) {
    AccentColor.BRAND -> stringResource(R.string.app_name_short)
    else -> accent.name.lowercase().replaceFirstChar { it.uppercase() }
}

@Composable
private fun bubbleLabel(shape: BubbleShape): String = stringResource(
    when (shape) {
        BubbleShape.ROUNDED -> R.string.settings_bubble_rounded
        BubbleShape.TAILED -> R.string.settings_bubble_tailed
    },
)

@Composable
private fun privacyLabel(privacy: NotificationPrivacy): String = stringResource(
    when (privacy) {
        NotificationPrivacy.FULL -> R.string.settings_notification_privacy_full
        NotificationPrivacy.SENDER_ONLY -> R.string.settings_notification_privacy_sender
        NotificationPrivacy.HIDDEN -> R.string.settings_notification_privacy_hidden
        NotificationPrivacy.NONE -> R.string.settings_notification_privacy_none
    },
)

@Composable
private fun groupLabel(mode: GroupMessagingMode): String = stringResource(
    when (mode) {
        GroupMessagingMode.GROUP_MMS -> R.string.settings_group_mms_mms
        GroupMessagingMode.INDIVIDUAL_SMS -> R.string.settings_group_mms_sms
    },
)

@Composable
private fun swipeLabel(action: SwipeAction): String = stringResource(
    when (action) {
        SwipeAction.NONE -> R.string.settings_swipe_none
        SwipeAction.ARCHIVE -> R.string.action_archive
        SwipeAction.DELETE -> R.string.action_delete
        SwipeAction.MARK_READ_UNREAD -> R.string.action_mark_read
        SwipeAction.PIN -> R.string.action_pin
        SwipeAction.MUTE -> R.string.action_mute
    },
)

@Composable
private fun retentionLabel(days: Int): String = if (days <= 0) {
    stringResource(R.string.settings_auto_delete_never)
} else {
    stringResource(R.string.settings_auto_delete_days, days)
}

private fun versionName(context: android.content.Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
}.getOrDefault("")

private val RETENTION_OPTIONS = listOf(0, 30, 90, 180, 365)
private const val PROJECT_URL = "https://github.com/elf178174-maker/Pingu-messages"
