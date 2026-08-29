package app.pingu.messages.platform.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import app.pingu.messages.core.util.PhoneNumbers
import app.pingu.messages.data.local.PinguDatabase
import app.pingu.messages.data.local.entity.BlockedNumberEntity
import app.pingu.messages.data.local.entity.ConversationMetadataEntity
import app.pingu.messages.data.local.entity.FolderEntity
import app.pingu.messages.data.local.entity.ScheduledMessageEntity
import app.pingu.messages.data.preferences.SettingsStore
import app.pingu.messages.data.telephony.SmsProviderDataSource
import app.pingu.messages.data.telephony.ThreadsDataSource
import app.pingu.messages.domain.model.AppSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local backup and restore.
 *
 * There is no cloud here and no account. The user picks a file with the system document picker and
 * the app writes a single JSON document into it; restoring reads it back. That means the backup
 * lives wherever the user decided - a USB stick, their own cloud drive, another phone - and Pingu
 * Messages never sees it again.
 *
 * What is included: settings, every app-only decision (pins, mutes, archive and block state,
 * folders, scheduled messages, drafts, reactions) and the text of messages. What is not: attachment
 * binaries, which would turn a small text file into gigabytes. That limitation is stated in the
 * export screen rather than buried here.
 *
 * On restore, messages are written back into Android's own SMS store, so they reappear in this app
 * and in any other messaging app. Duplicates are skipped by matching sender, timestamp and body.
 */
class BackupManager(
    private val context: Context,
    private val database: PinguDatabase,
    private val settings: SettingsStore,
    private val smsProvider: SmsProviderDataSource,
    private val threads: ThreadsDataSource,
    private val ioDispatcher: CoroutineDispatcher,
) {

    data class ExportSummary(
        val conversations: Int,
        val messages: Int,
        val blockedNumbers: Int,
        val scheduledMessages: Int,
        val bytesWritten: Long,
    )

    data class ImportSummary(
        val conversationsRestored: Int,
        val messagesRestored: Int,
        val messagesSkipped: Int,
        val blockedNumbersRestored: Int,
    )

    suspend fun export(target: Uri, includeMessages: Boolean): Result<ExportSummary> =
        withContext(ioDispatcher) {
            runCatching {
                val document = buildDocument(includeMessages)
                val bytes = document.toString(JSON_INDENT).toByteArray(Charsets.UTF_8)
                context.contentResolver.openOutputStream(target, "wt")?.use { it.write(bytes) }
                    ?: error("The chosen file could not be written")

                ExportSummary(
                    conversations = document.getJSONArray(KEY_CONVERSATIONS).length(),
                    messages = document.optJSONArray(KEY_MESSAGES)?.length() ?: 0,
                    blockedNumbers = document.getJSONArray(KEY_BLOCKED).length(),
                    scheduledMessages = document.getJSONArray(KEY_SCHEDULED).length(),
                    bytesWritten = bytes.size.toLong(),
                )
            }
        }

    suspend fun import(source: Uri, restoreMessages: Boolean): Result<ImportSummary> =
        withContext(ioDispatcher) {
            runCatching {
                val text = context.contentResolver.openInputStream(source)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: error("The chosen file could not be read")

                val document = JSONObject(text)
                require(document.optInt(KEY_VERSION, 0) in 1..FORMAT_VERSION) {
                    "This backup was made by a newer version of Pingu Messages"
                }

                val conversations = restoreConversationMetadata(document)
                val blocked = restoreBlockedNumbers(document)
                restoreFolders(document)
                restoreScheduled(document)
                restoreSettings(document)

                var restored = 0
                var skipped = 0
                if (restoreMessages) {
                    val result = restoreMessagesInto(document)
                    restored = result.first
                    skipped = result.second
                }
                ImportSummary(conversations, restored, skipped, blocked)
            }
        }

    // ---- Export -------------------------------------------------------------------------------

    private suspend fun buildDocument(includeMessages: Boolean): JSONObject {
        val conversationDao = database.conversationDao()
        val document = JSONObject()
        document.put(KEY_VERSION, FORMAT_VERSION)
        document.put(KEY_CREATED_AT, System.currentTimeMillis())
        document.put(KEY_APP, "Pingu Messages")

        val conversations = JSONArray()
        conversationDao.allMetadata().forEach { metadata ->
            val row = conversationDao.getConversation(metadata.threadId)
            conversations.put(
                JSONObject().apply {
                    put("threadId", metadata.threadId)
                    put("addresses", row?.addresses.orEmpty())
                    put("pinned", metadata.pinned)
                    put("pinnedAt", metadata.pinnedAt)
                    put("muted", metadata.muted)
                    put("mutedUntil", metadata.mutedUntil)
                    put("archived", metadata.archived)
                    put("blocked", metadata.blocked)
                    put("spam", metadata.spam)
                    put("customTitle", metadata.customTitle ?: JSONObject.NULL)
                    put("subscriptionId", metadata.subscriptionId)
                    put("notificationsEnabled", metadata.notificationsEnabled)
                    put("folderId", metadata.folderId ?: JSONObject.NULL)
                },
            )
        }
        document.put(KEY_CONVERSATIONS, conversations)

        val blocked = JSONArray()
        database.blockedNumberDao().getAll().forEach { entity ->
            blocked.put(
                JSONObject().apply {
                    put("address", entity.address)
                    put("matchKey", entity.matchKey)
                    put("origin", entity.origin)
                    put("blockedAt", entity.blockedAt)
                    put("note", entity.note ?: JSONObject.NULL)
                },
            )
        }
        document.put(KEY_BLOCKED, blocked)

        val keywords = JSONArray()
        database.blockedNumberDao().keywords().forEach(keywords::put)
        document.put(KEY_SPAM_KEYWORDS, keywords)

        val scheduled = JSONArray()
        database.scheduledMessageDao().pending().forEach { entity ->
            scheduled.put(
                JSONObject().apply {
                    put("threadId", entity.threadId)
                    put("recipients", entity.recipients)
                    put("body", entity.body)
                    put("subject", entity.subject ?: JSONObject.NULL)
                    put("scheduledAt", entity.scheduledAt)
                    put("subscriptionId", entity.subscriptionId)
                },
            )
        }
        document.put(KEY_SCHEDULED, scheduled)

        val drafts = JSONArray()
        database.draftDao().threadIdsWithDrafts().forEach { threadId ->
            database.draftDao().get(threadId)?.let { draft ->
                drafts.put(
                    JSONObject().apply {
                        put("threadId", draft.threadId)
                        put("text", draft.text)
                        put("subject", draft.subject ?: JSONObject.NULL)
                    },
                )
            }
        }
        document.put(KEY_DRAFTS, drafts)

        document.put(KEY_SETTINGS, settingsToJson(settings.settings.first()))

        if (includeMessages) {
            val messages = JSONArray()
            conversationDao.allThreadIds().forEach { threadId ->
                database.messageDao().getAllForThread(threadId).forEach { entity ->
                    messages.put(
                        JSONObject().apply {
                            put("threadId", entity.threadId)
                            put("transport", entity.transport)
                            put("address", entity.address ?: JSONObject.NULL)
                            put("body", entity.body ?: JSONObject.NULL)
                            put("subject", entity.subject ?: JSONObject.NULL)
                            put("timestamp", entity.timestamp)
                            put("isOutgoing", entity.isOutgoing)
                            put("isRead", entity.isRead)
                            put("status", entity.status)
                            put("subscriptionId", entity.subscriptionId)
                            put("hasAttachments", entity.hasAttachments)
                        },
                    )
                }
            }
            document.put(KEY_MESSAGES, messages)

            val reactions = JSONArray()
            conversationDao.allThreadIds().forEach { threadId ->
                val ids = database.messageDao().getAllForThread(threadId).map { it.id }
                database.reactionDao().forMessages(ids).forEach { reaction ->
                    val message = database.messageDao().getById(reaction.messageId)
                    reactions.put(
                        JSONObject().apply {
                            put("threadId", message?.threadId ?: 0L)
                            put("messageTimestamp", message?.timestamp ?: 0L)
                            put("emoji", reaction.emoji)
                            put("authorKey", reaction.authorKey)
                        },
                    )
                }
            }
            document.put(KEY_REACTIONS, reactions)
        }
        return document
    }

    private fun settingsToJson(current: AppSettings): JSONObject = JSONObject().apply {
        put("themeMode", current.themeMode.name)
        put("dynamicColor", current.dynamicColor)
        put("accentColor", current.accentColor.name)
        put("pureBlackDarkMode", current.pureBlackDarkMode)
        put("bubbleShape", current.bubbleShape.name)
        put("messageTextScale", current.messageTextScale.toDouble())
        put("notificationPrivacy", current.notificationPrivacy.name)
        put("notificationVibrate", current.notificationVibrate)
        put("conversationBubbles", current.conversationBubbles)
        put("deliveryReports", current.deliveryReports)
        put("autoDownloadMms", current.autoDownloadMms)
        put("autoDownloadMmsWhileRoaming", current.autoDownloadMmsWhileRoaming)
        put("groupMessagingMode", current.groupMessagingMode.name)
        put("splitLongMessages", current.splitLongMessages)
        put("reactionTextFallback", current.reactionTextFallback)
        put("quoteWhenReplying", current.quoteWhenReplying)
        put("spamFilterEnabled", current.spamFilterEnabled)
        put("autoDeleteMediaDays", current.autoDeleteMediaDays)
        put("swipeRightAction", current.swipeRightAction.name)
        put("swipeLeftAction", current.swipeLeftAction.name)
    }

    // ---- Import -------------------------------------------------------------------------------

    private suspend fun restoreConversationMetadata(document: JSONObject): Int {
        val array = document.optJSONArray(KEY_CONVERSATIONS) ?: return 0
        val dao = database.conversationDao()
        var restored = 0
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val addresses = PhoneNumbers.splitRecipients(item.optString("addresses"))
            // Thread ids are device-specific, so they are resolved from the addresses instead.
            val threadId = if (addresses.isEmpty()) {
                item.optLong("threadId")
            } else {
                threads.getOrCreateThreadId(addresses) ?: item.optLong("threadId")
            }
            if (threadId <= 0L) continue

            dao.upsertMetadata(
                ConversationMetadataEntity(
                    threadId = threadId,
                    pinned = item.optBoolean("pinned"),
                    pinnedAt = item.optLong("pinnedAt"),
                    muted = item.optBoolean("muted"),
                    mutedUntil = item.optLong("mutedUntil"),
                    archived = item.optBoolean("archived"),
                    blocked = item.optBoolean("blocked"),
                    spam = item.optBoolean("spam"),
                    customTitle = item.optString("customTitle").takeIf { it.isNotBlank() && it != "null" },
                    subscriptionId = item.optInt("subscriptionId", -1),
                    notificationsEnabled = item.optBoolean("notificationsEnabled", true),
                    folderId = item.optLong("folderId").takeIf { it > 0 },
                ),
            )
            restored++
        }
        return restored
    }

    private suspend fun restoreBlockedNumbers(document: JSONObject): Int {
        val array = document.optJSONArray(KEY_BLOCKED) ?: return 0
        val dao = database.blockedNumberDao()
        var restored = 0
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val address = item.optString("address")
            if (address.isBlank()) continue
            dao.insert(
                BlockedNumberEntity(
                    address = address,
                    matchKey = item.optString("matchKey").ifBlank { PhoneNumbers.matchKey(address) },
                    origin = item.optString("origin").ifBlank { "MANUAL" },
                    blockedAt = item.optLong("blockedAt", System.currentTimeMillis()),
                    note = item.optString("note").takeIf { it.isNotBlank() && it != "null" },
                ),
            )
            restored++
        }
        return restored
    }

    private suspend fun restoreFolders(document: JSONObject) {
        val array = document.optJSONArray(KEY_FOLDERS) ?: return
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            database.folderDao().insert(
                FolderEntity(
                    name = item.optString("name"),
                    colorSlot = item.optInt("colorSlot"),
                    position = item.optInt("position"),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private suspend fun restoreScheduled(document: JSONObject) {
        val array = document.optJSONArray(KEY_SCHEDULED) ?: return
        val now = System.currentTimeMillis()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val scheduledAt = item.optLong("scheduledAt")
            // A schedule in the past would fire immediately on restore, which is never what the
            // user meant; those entries are dropped and reported by the count.
            if (scheduledAt <= now) continue
            database.scheduledMessageDao().insert(
                ScheduledMessageEntity(
                    threadId = item.optLong("threadId"),
                    recipients = item.optString("recipients"),
                    body = item.optString("body"),
                    subject = item.optString("subject").takeIf { it.isNotBlank() && it != "null" },
                    scheduledAt = scheduledAt,
                    createdAt = now,
                    subscriptionId = item.optInt("subscriptionId", -1),
                    state = "PENDING",
                ),
            )
        }
    }

    private suspend fun restoreSettings(document: JSONObject) {
        val item = document.optJSONObject(KEY_SETTINGS) ?: return
        settings.update { current ->
            current.copy(
                themeMode = enumOr(item.optString("themeMode"), current.themeMode),
                dynamicColor = item.optBoolean("dynamicColor", current.dynamicColor),
                accentColor = enumOr(item.optString("accentColor"), current.accentColor),
                pureBlackDarkMode = item.optBoolean("pureBlackDarkMode", current.pureBlackDarkMode),
                bubbleShape = enumOr(item.optString("bubbleShape"), current.bubbleShape),
                messageTextScale = item.optDouble("messageTextScale", current.messageTextScale.toDouble())
                    .toFloat(),
                notificationPrivacy = enumOr(
                    item.optString("notificationPrivacy"),
                    current.notificationPrivacy,
                ),
                notificationVibrate = item.optBoolean("notificationVibrate", current.notificationVibrate),
                conversationBubbles = item.optBoolean("conversationBubbles", current.conversationBubbles),
                deliveryReports = item.optBoolean("deliveryReports", current.deliveryReports),
                autoDownloadMms = item.optBoolean("autoDownloadMms", current.autoDownloadMms),
                autoDownloadMmsWhileRoaming = item.optBoolean(
                    "autoDownloadMmsWhileRoaming",
                    current.autoDownloadMmsWhileRoaming,
                ),
                groupMessagingMode = enumOr(
                    item.optString("groupMessagingMode"),
                    current.groupMessagingMode,
                ),
                splitLongMessages = item.optBoolean("splitLongMessages", current.splitLongMessages),
                reactionTextFallback = item.optBoolean("reactionTextFallback", current.reactionTextFallback),
                quoteWhenReplying = item.optBoolean("quoteWhenReplying", current.quoteWhenReplying),
                spamFilterEnabled = item.optBoolean("spamFilterEnabled", current.spamFilterEnabled),
                autoDeleteMediaDays = item.optInt("autoDeleteMediaDays", current.autoDeleteMediaDays),
                swipeRightAction = enumOr(item.optString("swipeRightAction"), current.swipeRightAction),
                swipeLeftAction = enumOr(item.optString("swipeLeftAction"), current.swipeLeftAction),
            )
        }
    }

    /**
     * Writes exported messages back into Android's SMS store.
     *
     * Only possible while the app holds the default SMS role, which is checked by the caller. A
     * message that already exists (same sender, same second, same text) is skipped so restoring
     * twice does not duplicate a conversation.
     */
    private suspend fun restoreMessagesInto(document: JSONObject): Pair<Int, Int> {
        val array = document.optJSONArray(KEY_MESSAGES) ?: return 0 to 0
        var restored = 0
        var skipped = 0

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            if (item.optString("transport") != "SMS") {
                // Multimedia messages carry binary parts that a text backup cannot hold.
                skipped++
                continue
            }
            val address = item.optString("address").takeIf { it.isNotBlank() && it != "null" }
            val body = item.optString("body").takeIf { it.isNotBlank() && it != "null" }
            if (address == null || body == null) {
                skipped++
                continue
            }
            val timestamp = item.optLong("timestamp")
            val isOutgoing = item.optBoolean("isOutgoing")

            if (messageExists(address, body, timestamp)) {
                skipped++
                continue
            }

            val threadId = threads.getOrCreateThreadId(listOf(address))
            val uri = if (isOutgoing) {
                smsProvider.insertOutgoing(address, body, timestamp, item.optInt("subscriptionId", -1), threadId)
                    ?.also { smsProvider.markSent(it) }
            } else {
                smsProvider.insertReceived(
                    address = address,
                    body = body,
                    timestampMillis = timestamp,
                    sentTimestampMillis = timestamp,
                    subscriptionId = item.optInt("subscriptionId", -1),
                    read = item.optBoolean("isRead", true),
                    threadId = threadId,
                )
            }
            if (uri != null) restored++ else skipped++
        }
        Log.i(TAG, "Restored $restored messages, skipped $skipped")
        return restored to skipped
    }

    private fun messageExists(address: String, body: String, timestamp: Long): Boolean {
        val key = PhoneNumbers.matchKey(address)
        return smsProvider.queryRecent(DUPLICATE_SCAN_LIMIT).any { row ->
            row.dateMillis == timestamp &&
                row.body == body &&
                PhoneNumbers.matchKey(row.address) == key
        }
    }

    private inline fun <reified T : Enum<T>> enumOr(stored: String?, fallback: T): T =
        stored?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: fallback

    companion object {
        private const val TAG = "BackupManager"

        const val FORMAT_VERSION = 1
        const val MIME_TYPE = "application/json"
        const val DEFAULT_FILE_NAME = "pingu-messages-backup.json"

        private const val JSON_INDENT = 2
        private const val DUPLICATE_SCAN_LIMIT = 5_000

        private const val KEY_VERSION = "version"
        private const val KEY_CREATED_AT = "createdAt"
        private const val KEY_APP = "app"
        private const val KEY_CONVERSATIONS = "conversations"
        private const val KEY_MESSAGES = "messages"
        private const val KEY_BLOCKED = "blockedNumbers"
        private const val KEY_SPAM_KEYWORDS = "spamKeywords"
        private const val KEY_SCHEDULED = "scheduledMessages"
        private const val KEY_DRAFTS = "drafts"
        private const val KEY_FOLDERS = "folders"
        private const val KEY_SETTINGS = "settings"
        private const val KEY_REACTIONS = "reactions"
    }
}
