package app.pingu.messages.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmsMessageSizeTest {

    @Test
    fun `plain latin text uses the seven bit alphabet`() {
        val result = SmsMessageSize.measure("Hello there")
        assertThat(result.encoding).isEqualTo(SmsMessageSize.Encoding.GSM_7BIT)
        assertThat(result.segments).isEqualTo(1)
        assertThat(result.units).isEqualTo(11)
        assertThat(result.remainingInSegment).isEqualTo(149)
    }

    @Test
    fun `a single part holds 160 septets`() {
        val result = SmsMessageSize.measure("a".repeat(160))
        assertThat(result.segments).isEqualTo(1)
        assertThat(result.remainingInSegment).isEqualTo(0)
    }

    @Test
    fun `one extra character costs a second segment of 153`() {
        val result = SmsMessageSize.measure("a".repeat(161))
        assertThat(result.segments).isEqualTo(2)
        assertThat(result.remainingInSegment).isEqualTo(2 * 153 - 161)
    }

    @Test
    fun `extension table characters cost two septets`() {
        val result = SmsMessageSize.measure("100%{}")
        assertThat(result.encoding).isEqualTo(SmsMessageSize.Encoding.GSM_7BIT)
        // Four plain characters plus two escaped ones.
        assertThat(result.units).isEqualTo(8)
    }

    @Test
    fun `accented latin characters stay in the GSM alphabet`() {
        // A common surprise: these are in GSM 03.38, so they do not halve the limit.
        val result = SmsMessageSize.measure("Caf\u00E9")
        assertThat(result.encoding).isEqualTo(SmsMessageSize.Encoding.GSM_7BIT)
    }

    @Test
    fun `one character outside the alphabet forces the whole message to unicode`() {
        // A curly apostrophe, which keyboards insert automatically, is the usual culprit.
        val result = SmsMessageSize.measure("Don\u2019t")
        assertThat(result.encoding).isEqualTo(SmsMessageSize.Encoding.UNICODE)
        assertThat(result.units).isEqualTo(5)
        assertThat(result.remainingInSegment).isEqualTo(70 - 5)
    }

    @Test
    fun `unicode messages split at 67 characters per part`() {
        val result = SmsMessageSize.measure("中".repeat(71))
        assertThat(result.encoding).isEqualTo(SmsMessageSize.Encoding.UNICODE)
        assertThat(result.segments).isEqualTo(2)
    }

    @Test
    fun `an empty message occupies no segments`() {
        val result = SmsMessageSize.measure("")
        assertThat(result.segments).isEqualTo(0)
        assertThat(result.units).isEqualTo(0)
    }
}
