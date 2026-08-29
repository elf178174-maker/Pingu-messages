package app.pingu.messages.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhoneNumbersTest {

    @Test
    fun `normalize strips formatting but keeps the digits`() {
        assertThat(PhoneNumbers.normalize("+44 (0)7700 900-123")).isEqualTo("4407700900123")
        assertThat(PhoneNumbers.normalize("  07700 900123 ")).isEqualTo("07700900123")
    }

    @Test
    fun `normalize keeps alphanumeric senders intact`() {
        assertThat(PhoneNumbers.normalize("MyBank")).isEqualTo("MYBANK")
        assertThat(PhoneNumbers.normalize("")).isEmpty()
        assertThat(PhoneNumbers.normalize(null)).isEmpty()
    }

    @Test
    fun `the same number in different formats matches`() {
        assertThat(PhoneNumbers.sameNumber("+447700900123", "07700 900123")).isTrue()
        assertThat(PhoneNumbers.sameNumber("00447700900123", "+44 7700 900123")).isTrue()
        assertThat(PhoneNumbers.sameNumber("+1 (555) 010-9999", "555-010-9999")).isTrue()
    }

    @Test
    fun `different numbers do not match`() {
        assertThat(PhoneNumbers.sameNumber("+447700900123", "+447700900124")).isFalse()
        assertThat(PhoneNumbers.sameNumber("", "+447700900123")).isFalse()
        assertThat(PhoneNumbers.sameNumber(null, null)).isFalse()
    }

    @Test
    fun `short codes must match exactly`() {
        assertThat(PhoneNumbers.matchKey("60999")).isEqualTo("60999")
        assertThat(PhoneNumbers.sameNumber("60999", "160999")).isFalse()
    }

    @Test
    fun `alphanumeric senders match case insensitively`() {
        assertThat(PhoneNumbers.sameNumber("MyBank", "MYBANK")).isTrue()
        assertThat(PhoneNumbers.isAlphanumericSender("Amazon")).isTrue()
        assertThat(PhoneNumbers.isAlphanumericSender("+447700900123")).isFalse()
    }

    @Test
    fun `diallable requires at least three digits`() {
        assertThat(PhoneNumbers.isDiallable("112")).isTrue()
        assertThat(PhoneNumbers.isDiallable("12")).isFalse()
        assertThat(PhoneNumbers.isDiallable("Google")).isFalse()
    }

    @Test
    fun `display formatting groups digits and keeps a leading plus`() {
        assertThat(PhoneNumbers.formatForDisplay("+447700900123")).isEqualTo("+447 700 900 123")
        assertThat(PhoneNumbers.formatForDisplay("MyBank")).isEqualTo("MyBank")
        assertThat(PhoneNumbers.formatForDisplay("")).isEmpty()
    }

    @Test
    fun `recipient lists split on the separators the provider uses`() {
        assertThat(PhoneNumbers.splitRecipients("+441 +442, +443; +444"))
            .containsExactly("+441", "+442", "+443", "+444")
            .inOrder()
        assertThat(PhoneNumbers.splitRecipients(null)).isEmpty()
    }
}
