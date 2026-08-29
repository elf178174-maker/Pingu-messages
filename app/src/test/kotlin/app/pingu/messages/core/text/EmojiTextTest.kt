package app.pingu.messages.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EmojiTextTest {

    @Test
    fun `a single emoji is drawn large`() {
        assertThat(EmojiText.isLargeEmojiMessage("👍")).isTrue()
    }

    @Test
    fun `up to three emoji are drawn large`() {
        assertThat(EmojiText.isLargeEmojiMessage("😀😀😀")).isTrue()
        assertThat(EmojiText.isLargeEmojiMessage("😀😀😀😀")).isFalse()
    }

    @Test
    fun `whitespace between emoji is allowed`() {
        assertThat(EmojiText.countStandaloneEmoji("😀 😀")).isEqualTo(2)
    }

    @Test
    fun `emoji mixed with words are not drawn large`() {
        assertThat(EmojiText.isLargeEmojiMessage("nice 👍")).isFalse()
        assertThat(EmojiText.countStandaloneEmoji("hello")).isNull()
    }

    @Test
    fun `a zero-width-joiner sequence counts as one emoji`() {
        // Family: man + ZWJ + woman + ZWJ + girl
        val family = "👨‍👩‍👧"
        assertThat(EmojiText.countStandaloneEmoji(family)).isEqualTo(1)
        assertThat(EmojiText.isLargeEmojiMessage(family)).isTrue()
    }

    @Test
    fun `a skin tone modifier does not add a cluster`() {
        val waving = "👋🏽"
        assertThat(EmojiText.countStandaloneEmoji(waving)).isEqualTo(1)
    }

    @Test
    fun `an emoji with a variation selector counts once`() {
        assertThat(EmojiText.countStandaloneEmoji("❤️")).isEqualTo(1)
    }

    @Test
    fun `empty text is not an emoji message`() {
        assertThat(EmojiText.countStandaloneEmoji("")).isNull()
        assertThat(EmojiText.isLargeEmojiMessage("   ")).isFalse()
    }
}
