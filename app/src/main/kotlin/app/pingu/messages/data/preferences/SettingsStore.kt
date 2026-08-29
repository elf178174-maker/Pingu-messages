package app.pingu.messages.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.pingu.messages.domain.model.AppSettings
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Typed access to user settings, backed by a Preferences DataStore.
 *
 * DataStore rather than SharedPreferences because reads are a flow (so the theme reacts without a
 * listener dance), writes are transactional, and the file is included in Android's backup rules
 * while the message mirror is not.
 *
 * Unknown enum values decay to the default instead of throwing, so a downgrade after a future
 * version added a new theme cannot leave the app unable to start.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val accentColor = stringPreferencesKey("accent_color")
        val pureBlack = booleanPreferencesKey("pure_black")
        val bubbleShape = stringPreferencesKey("bubble_shape")
        val messageTextScale = floatPreferencesKey("message_text_scale")

        val notificationPrivacy = stringPreferencesKey("notification_privacy")
        val notificationVibrate = booleanPreferencesKey("notification_vibrate")
        val conversationBubbles = booleanPreferencesKey("conversation_bubbles")

        val defaultSubscriptionId = intPreferencesKey("default_subscription_id")
        val deliveryReports = booleanPreferencesKey("delivery_reports")
        val autoDownloadMms = booleanPreferencesKey("auto_download_mms")
        val autoDownloadMmsRoaming = booleanPreferencesKey("auto_download_mms_roaming")
        val groupMessagingMode = stringPreferencesKey("group_messaging_mode")
        val splitLongMessages = booleanPreferencesKey("split_long_messages")
        val sendDelaySeconds = intPreferencesKey("send_delay_seconds")
        val sendMmsReadReports = booleanPreferencesKey("send_mms_read_reports")
        val reactionTextFallback = booleanPreferencesKey("reaction_text_fallback")
        val quoteWhenReplying = booleanPreferencesKey("quote_when_replying")

        val appLockEnabled = booleanPreferencesKey("app_lock_enabled")
        val spamFilterEnabled = booleanPreferencesKey("spam_filter_enabled")

        val autoDeleteMediaDays = intPreferencesKey("auto_delete_media_days")

        val swipeRightAction = stringPreferencesKey("swipe_right_action")
        val swipeLeftAction = stringPreferencesKey("swipe_left_action")

        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { error ->
            // A corrupt preferences file must not stop the app from starting.
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { it.toSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { preferences ->
            val updated = transform(preferences.toSettings())
            preferences.write(updated)
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            themeMode = enumOrDefault(this[Keys.themeMode], defaults.themeMode),
            dynamicColor = this[Keys.dynamicColor] ?: defaults.dynamicColor,
            accentColor = enumOrDefault(this[Keys.accentColor], defaults.accentColor),
            pureBlackDarkMode = this[Keys.pureBlack] ?: defaults.pureBlackDarkMode,
            bubbleShape = enumOrDefault(this[Keys.bubbleShape], defaults.bubbleShape),
            messageTextScale = this[Keys.messageTextScale] ?: defaults.messageTextScale,
            notificationPrivacy = enumOrDefault(this[Keys.notificationPrivacy], defaults.notificationPrivacy),
            notificationVibrate = this[Keys.notificationVibrate] ?: defaults.notificationVibrate,
            conversationBubbles = this[Keys.conversationBubbles] ?: defaults.conversationBubbles,
            defaultSubscriptionId = this[Keys.defaultSubscriptionId] ?: defaults.defaultSubscriptionId,
            deliveryReports = this[Keys.deliveryReports] ?: defaults.deliveryReports,
            autoDownloadMms = this[Keys.autoDownloadMms] ?: defaults.autoDownloadMms,
            autoDownloadMmsWhileRoaming = this[Keys.autoDownloadMmsRoaming]
                ?: defaults.autoDownloadMmsWhileRoaming,
            groupMessagingMode = enumOrDefault(this[Keys.groupMessagingMode], defaults.groupMessagingMode),
            splitLongMessages = this[Keys.splitLongMessages] ?: defaults.splitLongMessages,
            sendDelaySeconds = this[Keys.sendDelaySeconds] ?: defaults.sendDelaySeconds,
            sendMmsReadReports = this[Keys.sendMmsReadReports] ?: defaults.sendMmsReadReports,
            reactionTextFallback = this[Keys.reactionTextFallback] ?: defaults.reactionTextFallback,
            quoteWhenReplying = this[Keys.quoteWhenReplying] ?: defaults.quoteWhenReplying,
            appLockEnabled = this[Keys.appLockEnabled] ?: defaults.appLockEnabled,
            spamFilterEnabled = this[Keys.spamFilterEnabled] ?: defaults.spamFilterEnabled,
            autoDeleteMediaDays = this[Keys.autoDeleteMediaDays] ?: defaults.autoDeleteMediaDays,
            swipeRightAction = enumOrDefault(this[Keys.swipeRightAction], defaults.swipeRightAction),
            swipeLeftAction = enumOrDefault(this[Keys.swipeLeftAction], defaults.swipeLeftAction),
            onboardingComplete = this[Keys.onboardingComplete] ?: defaults.onboardingComplete,
        )
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.write(settings: AppSettings) {
        this[Keys.themeMode] = settings.themeMode.name
        this[Keys.dynamicColor] = settings.dynamicColor
        this[Keys.accentColor] = settings.accentColor.name
        this[Keys.pureBlack] = settings.pureBlackDarkMode
        this[Keys.bubbleShape] = settings.bubbleShape.name
        this[Keys.messageTextScale] = settings.messageTextScale
        this[Keys.notificationPrivacy] = settings.notificationPrivacy.name
        this[Keys.notificationVibrate] = settings.notificationVibrate
        this[Keys.conversationBubbles] = settings.conversationBubbles
        this[Keys.defaultSubscriptionId] = settings.defaultSubscriptionId
        this[Keys.deliveryReports] = settings.deliveryReports
        this[Keys.autoDownloadMms] = settings.autoDownloadMms
        this[Keys.autoDownloadMmsRoaming] = settings.autoDownloadMmsWhileRoaming
        this[Keys.groupMessagingMode] = settings.groupMessagingMode.name
        this[Keys.splitLongMessages] = settings.splitLongMessages
        this[Keys.sendDelaySeconds] = settings.sendDelaySeconds
        this[Keys.sendMmsReadReports] = settings.sendMmsReadReports
        this[Keys.reactionTextFallback] = settings.reactionTextFallback
        this[Keys.quoteWhenReplying] = settings.quoteWhenReplying
        this[Keys.appLockEnabled] = settings.appLockEnabled
        this[Keys.spamFilterEnabled] = settings.spamFilterEnabled
        this[Keys.autoDeleteMediaDays] = settings.autoDeleteMediaDays
        this[Keys.swipeRightAction] = settings.swipeRightAction.name
        this[Keys.swipeLeftAction] = settings.swipeLeftAction.name
        this[Keys.onboardingComplete] = settings.onboardingComplete
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(stored: String?, default: T): T =
        stored?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: default

    /** Exposed for the backup exporter, which writes the current settings into the archive. */
    @Suppress("unused")
    internal fun keysForBackup(): List<String> = listOf(
        Keys.themeMode.name,
        Keys.accentColor.name,
        Keys.notificationPrivacy.name,
    )
}
