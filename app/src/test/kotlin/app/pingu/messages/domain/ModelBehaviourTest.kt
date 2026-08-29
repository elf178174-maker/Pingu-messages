package app.pingu.messages.domain

import app.pingu.messages.domain.model.Attachment
import app.pingu.messages.domain.model.AttachmentKind
import app.pingu.messages.domain.model.Conversation
import app.pingu.messages.domain.model.MessageStatus
import app.pingu.messages.domain.model.Recipient
import app.pingu.messages.domain.model.ScheduledMessage
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModelBehaviourTest {

    private fun conversation(
        recipients: List<Recipient> = listOf(Recipient("+447700900123", displayName = "Ada")),
        block: Conversation.() -> Conversation = { this },
    ) = Conversation(threadId = 1L, recipients = recipients).block()

    @Test
    fun `a conversation is titled by its contacts`() {
        assertThat(conversation().title).isEqualTo("Ada")
    }

    @Test
    fun `a group conversation lists everyone`() {
        val group = conversation(
            listOf(
                Recipient("+447700900123", displayName = "Ada"),
                Recipient("+447700900124", displayName = "Grace"),
            ),
        )
        assertThat(group.isGroup).isTrue()
        assertThat(group.title).isEqualTo("Ada, Grace")
    }

    @Test
    fun `a custom title wins over the generated one`() {
        assertThat(conversation { copy(customTitle = "Book club") }.title).isEqualTo("Book club")
    }

    @Test
    fun `an unknown number falls back to a formatted number`() {
        val unknown = conversation(listOf(Recipient("+447700900123")))
        assertThat(unknown.title).isEqualTo("+447 700 900 123")
    }

    @Test
    fun `a timed mute expires`() {
        val now = 1_000_000L
        val muted = conversation { copy(isMuted = true, mutedUntil = now + 1_000) }
        assertThat(muted.isMutedAt(now)).isTrue()
        assertThat(muted.isMutedAt(now + 2_000)).isFalse()
    }

    @Test
    fun `a mute with no end never expires`() {
        val muted = conversation { copy(isMuted = true, mutedUntil = 0L) }
        assertThat(muted.isMutedAt(Long.MAX_VALUE)).isTrue()
    }

    @Test
    fun `a draft counts even when it is only attachments`() {
        assertThat(conversation { copy(draftHasAttachments = true) }.hasDraft).isTrue()
        assertThat(conversation { copy(draftText = "   ") }.hasDraft).isFalse()
    }

    @Test
    fun `attachment kinds come from the mime type`() {
        assertThat(Attachment.kindOf("image/png")).isEqualTo(AttachmentKind.IMAGE)
        assertThat(Attachment.kindOf("image/gif")).isEqualTo(AttachmentKind.GIF)
        assertThat(Attachment.kindOf("video/mp4")).isEqualTo(AttachmentKind.VIDEO)
        assertThat(Attachment.kindOf("audio/mpeg")).isEqualTo(AttachmentKind.AUDIO)
        assertThat(Attachment.kindOf("text/x-vcard")).isEqualTo(AttachmentKind.CONTACT_CARD)
        assertThat(Attachment.kindOf("application/pdf")).isEqualTo(AttachmentKind.FILE)
    }

    @Test
    fun `a recorded clip is a voice message, not just audio`() {
        val kind = Attachment.kindOf("audio/mp4", extra = Attachment.EXTRA_VOICE_MESSAGE)
        assertThat(kind).isEqualTo(AttachmentKind.VOICE)
        assertThat(kind.isPlayable).isTrue()
    }

    @Test
    fun `a vcf file without a vcard mime type is still a contact card`() {
        assertThat(Attachment.kindOf("application/octet-stream", fileName = "ada.vcf"))
            .isEqualTo(AttachmentKind.CONTACT_CARD)
    }

    @Test
    fun `attachments fall back to a sane aspect ratio`() {
        val unknown = Attachment(uri = "content://x", mimeType = "image/png")
        assertThat(unknown.aspectRatio).isEqualTo(Attachment.DEFAULT_ASPECT_RATIO)
        val known = unknown.copy(width = 1000, height = 500)
        assertThat(known.aspectRatio).isEqualTo(2f)
    }

    @Test
    fun `outgoing statuses are classified correctly`() {
        assertThat(MessageStatus.SENT.isOutgoing).isTrue()
        assertThat(MessageStatus.DELIVERED.isOutgoing).isTrue()
        assertThat(MessageStatus.RECEIVED.isOutgoing).isFalse()
        assertThat(MessageStatus.FAILED.isFailure).isTrue()
        assertThat(MessageStatus.DOWNLOAD_FAILED.isFailure).isTrue()
        assertThat(MessageStatus.SENDING.isInFlight).isTrue()
    }

    @Test
    fun `a scheduled message with an attachment must travel as mms`() {
        val scheduled = ScheduledMessage(
            threadId = 1L,
            recipients = listOf("+447700900123"),
            body = "hi",
            scheduledAt = 0L,
        )
        assertThat(scheduled.requiresMms).isFalse()
        assertThat(
            scheduled.copy(attachments = listOf(Attachment(uri = "u", mimeType = "image/png")))
                .requiresMms,
        ).isTrue()
        assertThat(scheduled.copy(subject = "Subject").requiresMms).isTrue()
    }
}
