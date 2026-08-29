package app.pingu.messages.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TapbacksTest {

    @Test
    fun `parses the classic verb form other messengers send`() {
        val parsed = Tapbacks.parse("Liked “see you at six”")
        assertThat(parsed).isNotNull()
        assertThat(parsed!!.emoji).isEqualTo(Tapbacks.THUMBS_UP)
        assertThat(parsed.quotedText).isEqualTo("see you at six")
        assertThat(parsed.removal).isFalse()
    }

    @Test
    fun `parses straight quotes as well as curly ones`() {
        val parsed = Tapbacks.parse("Loved \"the photo\"")
        assertThat(parsed?.emoji).isEqualTo(Tapbacks.HEART)
        assertThat(parsed?.quotedText).isEqualTo("the photo")
    }

    @Test
    fun `parses the reacted-with-emoji form`() {
        val parsed = Tapbacks.parse("Reacted 😂 to “that joke”")
        assertThat(parsed?.emoji).isEqualTo("😂")
        assertThat(parsed?.quotedText).isEqualTo("that joke")
    }

    @Test
    fun `parses a removal`() {
        val parsed = Tapbacks.parse("Removed a like from “dinner at eight”")
        assertThat(parsed?.removal).isTrue()
        assertThat(parsed?.emoji).isEqualTo(Tapbacks.THUMBS_UP)
    }

    @Test
    fun `ordinary messages are not reactions`() {
        assertThat(Tapbacks.parse("Liked it")).isNull()
        assertThat(Tapbacks.parse("I loved “that film” last night")).isNull()
        assertThat(Tapbacks.parse("")).isNull()
        assertThat(Tapbacks.parse(null)).isNull()
    }

    @Test
    fun `formatting round-trips through the parser`() {
        val outgoing = Tapbacks.format(Tapbacks.LAUGH, "the thing you said")
        val parsed = Tapbacks.parse(outgoing)
        assertThat(parsed?.emoji).isEqualTo(Tapbacks.LAUGH)
        assertThat(parsed?.quotedText).isEqualTo("the thing you said")
    }

    @Test
    fun `long quotes are truncated but stay parseable`() {
        val original = "x".repeat(400)
        val outgoing = Tapbacks.format(Tapbacks.HEART, original, maxQuoteLength = 40)
        assertThat(outgoing.length).isLessThan(original.length)
        assertThat(Tapbacks.parse(outgoing)).isNotNull()
    }

    @Test
    fun `removal formatting round-trips`() {
        val outgoing = Tapbacks.formatRemoval(Tapbacks.THUMBS_UP, "dinner")
        val parsed = Tapbacks.parse(outgoing)
        assertThat(parsed?.removal).isTrue()
        assertThat(parsed?.emoji).isEqualTo(Tapbacks.THUMBS_UP)
    }
}
