package app.pingu.messages.core.util

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test

class FileSizesTest {

    @Test
    fun `bytes below a kilobyte are shown as bytes`() {
        assertThat(FileSizes.format(0, Locale.UK)).isEqualTo("0 B")
        assertThat(FileSizes.format(999, Locale.UK)).isEqualTo("999 B")
    }

    @Test
    fun `larger sizes step up through the decimal units`() {
        assertThat(FileSizes.format(1_000, Locale.UK)).isEqualTo("1 kB")
        assertThat(FileSizes.format(1_500_000, Locale.UK)).isEqualTo("1.5 MB")
        assertThat(FileSizes.format(2_400_000_000, Locale.UK)).isEqualTo("2.4 GB")
    }

    @Test
    fun `three digit values drop the decimal place`() {
        assertThat(FileSizes.format(310_000, Locale.UK)).isEqualTo("310 kB")
    }
}
