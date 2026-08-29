package app.pingu.messages.core.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchMatcherTest {

    @Test
    fun `matching ignores case`() {
        assertThat(SearchMatcher.contains("Dinner at eight", "DINNER")).isTrue()
        assertThat(SearchMatcher.contains("Dinner at eight", "breakfast")).isFalse()
    }

    @Test
    fun `matching ignores accents`() {
        assertThat(SearchMatcher.contains("Renée Dubois", "renee")).isTrue()
        assertThat(SearchMatcher.contains("Renee Dubois", "renée")).isTrue()
    }

    @Test
    fun `folding preserves length so highlight offsets stay correct`() {
        val original = "Renée"
        assertThat(SearchMatcher.fold(original)).hasLength(original.length)
    }

    @Test
    fun `ranges point at the original text`() {
        val text = "Café or café?"
        val ranges = SearchMatcher.findRanges(text, "cafe")
        assertThat(ranges).hasSize(2)
        assertThat(text.substring(ranges[0].first, ranges[0].last + 1)).isEqualTo("Café")
        assertThat(text.substring(ranges[1].first, ranges[1].last + 1)).isEqualTo("café")
    }

    @Test
    fun `a blank needle matches nothing`() {
        assertThat(SearchMatcher.contains("anything", "  ")).isFalse()
        assertThat(SearchMatcher.findRanges("anything", "")).isEmpty()
    }

    @Test
    fun `short text is excerpted whole`() {
        val excerpt = SearchMatcher.excerpt("dinner at eight", "eight")
        assertThat(excerpt.text).isEqualTo("dinner at eight")
        assertThat(excerpt.matchRange).isNotNull()
    }

    @Test
    fun `long text is excerpted around the match`() {
        val text = "a".repeat(300) + " needle " + "b".repeat(300)
        val excerpt = SearchMatcher.excerpt(text, "needle", maxLength = 60)
        assertThat(excerpt.text.length).isAtMost(61)
        assertThat(excerpt.text).contains("needle")
        assertThat(excerpt.truncatedStart).isTrue()
    }

    @Test
    fun `excerpting text with no match still returns readable text`() {
        val excerpt = SearchMatcher.excerpt("c".repeat(400), "nothing", maxLength = 40)
        assertThat(excerpt.matchRange).isNull()
        assertThat(excerpt.text).endsWith("…")
    }
}
