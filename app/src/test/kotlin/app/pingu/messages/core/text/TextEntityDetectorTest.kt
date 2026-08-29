package app.pingu.messages.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextEntityDetectorTest {

    @Test
    fun `finds an http link`() {
        val entities = TextEntityDetector.detect("See https://example.com/page?a=1 for details")
        assertThat(entities).hasSize(1)
        assertThat(entities[0].type).isEqualTo(TextEntity.Type.URL)
        assertThat(entities[0].text).isEqualTo("https://example.com/page?a=1")
    }

    @Test
    fun `finds a bare www link and builds an openable uri`() {
        val entities = TextEntityDetector.detect("go to www.example.com now")
        assertThat(entities).hasSize(1)
        assertThat(TextEntityDetector.toUri(entities[0])).isEqualTo("https://www.example.com")
    }

    @Test
    fun `finds an email address`() {
        val entities = TextEntityDetector.detect("mail me at ada@example.co.uk please")
        assertThat(entities.map { it.type }).containsExactly(TextEntity.Type.EMAIL)
        assertThat(TextEntityDetector.toUri(entities[0])).isEqualTo("mailto:ada@example.co.uk")
    }

    @Test
    fun `finds a phone number and ignores short digit runs`() {
        val entities = TextEntityDetector.detect("ring 07700 900123 at 5pm")
        assertThat(entities.map { it.type }).containsExactly(TextEntity.Type.PHONE)

        assertThat(TextEntityDetector.detect("only 12 left")).isEmpty()
    }

    @Test
    fun `does not turn digits inside a link into a phone number`() {
        val entities = TextEntityDetector.detect("https://example.com/1234567890")
        assertThat(entities.map { it.type }).containsExactly(TextEntity.Type.URL)
    }

    @Test
    fun `finds a street address`() {
        val entities = TextEntityDetector.detect("meet me at 221 Baker Street tomorrow")
        assertThat(entities.map { it.type }).contains(TextEntity.Type.ADDRESS)
        val address = entities.first { it.type == TextEntity.Type.ADDRESS }
        assertThat(TextEntityDetector.toUri(address)).startsWith("geo:0,0?q=")
    }

    @Test
    fun `entities are returned in reading order and never overlap`() {
        val text = "See https://example.com or mail ada@example.com or ring 07700 900123"
        val entities = TextEntityDetector.detect(text)
        assertThat(entities).hasSize(3)
        assertThat(entities.map { it.start }).isInOrder()
        entities.zipWithNext().forEach { (first, second) ->
            assertThat(first.endExclusive).isAtMost(second.start)
        }
    }

    @Test
    fun `plain text produces nothing`() {
        assertThat(TextEntityDetector.detect("just a normal message")).isEmpty()
        assertThat(TextEntityDetector.detect("")).isEmpty()
    }
}
