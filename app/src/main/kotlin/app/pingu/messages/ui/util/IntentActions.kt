package app.pingu.messages.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.core.content.ContextCompat
import app.pingu.messages.R
import app.pingu.messages.domain.model.Attachment
import app.pingu.messages.domain.model.Message

/**
 * Intents the app hands to other apps.
 *
 * Everything here is defensive about the fact that a device may simply have no app for a given
 * intent: a tablet with no dialler, a phone with no maps app. Each call reports whether it worked
 * so the caller can say something useful instead of crashing with `ActivityNotFoundException`.
 */
object IntentActions {

    fun copyToClipboard(context: Context, text: String, label: String = "message") {
        val clipboard = ContextCompat.getSystemService(context, ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        // Android 13 and later show their own copy confirmation; a second toast would be noise.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.message_copied, Toast.LENGTH_SHORT).show()
        }
    }

    fun shareText(context: Context, text: String): Boolean = start(
        context,
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            },
            null,
        ),
    )

    fun shareAttachment(context: Context, attachment: Attachment): Boolean = start(
        context,
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = attachment.mimeType
                putExtra(Intent.EXTRA_STREAM, Uri.parse(attachment.uri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            null,
        ),
    )

    /** Shares a whole message: its text, its attachments, or both. */
    fun shareMessage(context: Context, message: Message): Boolean {
        val attachments = message.attachments
        return when {
            attachments.isEmpty() -> shareText(context, message.body.orEmpty())
            attachments.size == 1 && !message.hasText -> shareAttachment(context, attachments.first())
            else -> start(
                context,
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = attachments.firstOrNull()?.mimeType ?: "*/*"
                        putParcelableArrayListExtra(
                            Intent.EXTRA_STREAM,
                            ArrayList(attachments.map { Uri.parse(it.uri) }),
                        )
                        if (message.hasText) putExtra(Intent.EXTRA_TEXT, message.body)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    null,
                ),
            )
        }
    }

    fun openUri(context: Context, uri: String): Boolean =
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(uri)))

    fun openAttachment(context: Context, attachment: Attachment): Boolean = start(
        context,
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(attachment.uri), attachment.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },
    )

    fun dial(context: Context, number: String): Boolean =
        start(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))

    fun viewContact(context: Context, contactId: Long): Boolean = start(
        context,
        Intent(
            Intent.ACTION_VIEW,
            android.content.ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
        ),
    )

    /** Opens the "add to contacts" flow with the number pre-filled. */
    fun addContact(context: Context, number: String): Boolean = start(
        context,
        Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
        },
    )

    fun openSettings(context: Context, intent: Intent): Boolean = start(context, intent)

    /** Opens the system notification settings for this app's message channel. */
    fun openNotificationSettings(context: Context, channelId: String): Boolean = start(
        context,
        Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, channelId)
        },
    )

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (error: android.content.ActivityNotFoundException) {
        Toast.makeText(context, R.string.error_no_app_for_action, Toast.LENGTH_SHORT).show()
        false
    } catch (error: SecurityException) {
        Toast.makeText(context, R.string.error_no_app_for_action, Toast.LENGTH_SHORT).show()
        false
    }
}
