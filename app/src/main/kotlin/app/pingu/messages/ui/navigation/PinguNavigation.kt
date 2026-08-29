package app.pingu.messages.ui.navigation

import android.net.Uri

/**
 * Navigation routes.
 *
 * String routes with explicit builders rather than raw strings at the call sites, so an argument
 * can never be forgotten or encoded twice. Every argument that can contain arbitrary text is URL
 * encoded on the way in and decoded by the navigation library on the way out.
 */
object Routes {

    const val ONBOARDING = "onboarding"
    const val CONVERSATIONS = "conversations"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val BLOCKED = "blocked"
    const val SCHEDULED = "scheduled"

    const val ARG_THREAD_ID = "threadId"
    const val ARG_MESSAGE_ID = "messageId"
    const val ARG_URI = "uri"
    const val ARG_ADDRESS = "address"
    const val ARG_BODY = "body"
    const val ARG_FORWARD_IDS = "forwardIds"

    const val CONVERSATION = "conversation/{$ARG_THREAD_ID}?$ARG_MESSAGE_ID={$ARG_MESSAGE_ID}"
    const val NEW_MESSAGE =
        "new_message?$ARG_ADDRESS={$ARG_ADDRESS}&$ARG_BODY={$ARG_BODY}&$ARG_FORWARD_IDS={$ARG_FORWARD_IDS}"
    const val MEDIA_VIEWER = "media/{$ARG_THREAD_ID}/{$ARG_MESSAGE_ID}?$ARG_URI={$ARG_URI}"
    const val CONVERSATION_MEDIA = "conversation_media/{$ARG_THREAD_ID}"

    fun conversation(threadId: Long, messageId: Long? = null): String =
        "conversation/$threadId?$ARG_MESSAGE_ID=${messageId ?: 0L}"

    fun newMessage(
        address: String? = null,
        body: String? = null,
        forwardIds: List<Long> = emptyList(),
    ): String = buildString {
        append("new_message?")
        append("$ARG_ADDRESS=").append(encode(address))
        append("&$ARG_BODY=").append(encode(body))
        append("&$ARG_FORWARD_IDS=").append(encode(forwardIds.joinToString(",")))
    }

    fun mediaViewer(threadId: Long, messageId: Long, uri: String): String =
        "media/$threadId/$messageId?$ARG_URI=${encode(uri)}"

    fun conversationMedia(threadId: Long): String = "conversation_media/$threadId"

    fun parseForwardIds(raw: String?): List<Long> =
        raw.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }

    private fun encode(value: String?): String = Uri.encode(value.orEmpty())
}
