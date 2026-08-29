package app.pingu.messages.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QuotedReplyTest {

    @Test
    fun `format puts the quote above the reply`() {
        val formatted = QuotedReply.format("Are we still on for six?", "Yes, see you then")
        assertThat(formatted).isEqualTo("> Are we still on for six?\n\nYes, see you then")
    }

    @Test
    fun `whitespace in the quoted message is collapsed`() {
        val formatted = QuotedReply.format("line one\n   line two", "sure")
        assertThat(formatted).startsWith("> line one line two")
    }

    @Test
    fun `long quotes are truncated`() {
        val formatted = QuotedReply.format("y".repeat(500), "ok", maxQuoteLength = 20)
        assertThat(formatted.lines().first().length).isAtMost(22)
        assertThat(formatted).endsWith("ok")
    }

    @Test
    fun `an empty original leaves the reply untouched`() {
        assertThat(QuotedReply.format("   ", "hello")).isEqualTo("hello")
    }

    @Test
    fun `parse splits a quoted reply back apart`() {
        val parsed = QuotedReply.parse("> Are we still on?\n\nYes")
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.quotedText).isEqualTo("Are we still on?")
        assertThat(parsed.replyText).isEqualTo("Yes")
    }

    @Test
    fun `parse handles a multi-line quote`() {
        val parsed = QuotedReply.parse("> first\n> second\n\nreply body")
        assertThat(parsed?.quotedText).isEqualTo("first second")
        assertThat(parsed?.replyText).isEqualTo("reply body")
    }

    @Test
    fun `messages that are not replies parse to null`() {
        assertThat(QuotedReply.parse("just a message")).isNull()
        assertThat(QuotedReply.parse("> quote with no reply")).isNull()
        assertThat(QuotedReply.parse(null)).isNull()
    }

    @Test
    fun `format and parse round-trip`() {
        val formatted = QuotedReply.format("original text", "the reply")
        val parsed = QuotedReply.parse(formatted)
        assertThat(parsed?.quotedText).isEqualTo("original text")
        assertThat(parsed?.replyText).isEqualTo("the reply")
    }
}
