package app.pingu.messages.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AvatarsTest {

    @Test
    fun `initials use the first and last word`() {
        assertThat(Avatars.initials("Ada Lovelace")).isEqualTo("AL")
        assertThat(Avatars.initials("Ada Byron King Lovelace")).isEqualTo("AL")
        assertThat(Avatars.initials("Prince")).isEqualTo("P")
    }

    @Test
    fun `numbers and blanks have no initials`() {
        assertThat(Avatars.initials("+447700900123")).isEmpty()
        assertThat(Avatars.initials("")).isEmpty()
        assertThat(Avatars.initials(null)).isEmpty()
    }

    @Test
    fun `colour slots are stable and inside the palette`() {
        val first = Avatars.colorSlot("+447700900123")
        assertThat(first).isEqualTo(Avatars.colorSlot("+447700900123"))
        assertThat(first).isAtLeast(0)
        assertThat(first).isLessThan(Avatars.COLOR_SLOTS)
        assertThat(Avatars.colorSlot(null)).isEqualTo(0)
    }
}
