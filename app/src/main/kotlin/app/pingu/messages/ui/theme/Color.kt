package app.pingu.messages.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The colour system.
 *
 * One neutral surface family shared by every theme, plus a small set of accents. Keeping the
 * neutrals fixed is what stops the app looking like a theme engine: changing the accent changes the
 * send button and the outgoing bubble, not the whole interface.
 *
 * Dark mode is a dark grey, not black. Pure black is available as an explicit OLED setting because
 * it costs contrast on the edges of elevated surfaces, and forcing it on everyone is a common way
 * to make a dark theme uncomfortable to read.
 */
internal object PinguPalette {

    // Neutrals, light
    val lightBackground = Color(0xFFFBF9F9)
    val lightSurface = Color(0xFFFBF9F9)
    val lightSurfaceContainerLow = Color(0xFFF5F3F3)
    val lightSurfaceContainer = Color(0xFFEFEDED)
    val lightSurfaceContainerHigh = Color(0xFFE9E7E7)
    val lightSurfaceVariant = Color(0xFFDBE4E3)
    val lightOnSurface = Color(0xFF191C1C)
    val lightOnSurfaceVariant = Color(0xFF3F4948)
    val lightOutline = Color(0xFF6F7979)
    val lightOutlineVariant = Color(0xFFBFC8C8)
    val lightInverseSurface = Color(0xFF2D3131)
    val lightInverseOnSurface = Color(0xFFEFF1F1)
    val lightScrim = Color(0xFF000000)

    // Neutrals, dark
    val darkBackground = Color(0xFF101414)
    val darkSurface = Color(0xFF101414)
    val darkSurfaceContainerLow = Color(0xFF181D1D)
    val darkSurfaceContainer = Color(0xFF1C2121)
    val darkSurfaceContainerHigh = Color(0xFF272C2C)
    val darkSurfaceVariant = Color(0xFF3F4948)
    val darkOnSurface = Color(0xFFE0E3E2)
    val darkOnSurfaceVariant = Color(0xFFBEC8C8)
    val darkOutline = Color(0xFF899393)
    val darkOutlineVariant = Color(0xFF3F4948)
    val darkInverseSurface = Color(0xFFE0E3E2)
    val darkInverseOnSurface = Color(0xFF2D3131)

    // Errors, shared shape across themes
    val lightError = Color(0xFFBA1A1A)
    val lightOnError = Color(0xFFFFFFFF)
    val lightErrorContainer = Color(0xFFFFDAD6)
    val lightOnErrorContainer = Color(0xFF410002)
    val darkError = Color(0xFFFFB4AB)
    val darkOnError = Color(0xFF690005)
    val darkErrorContainer = Color(0xFF93000A)
    val darkOnErrorContainer = Color(0xFFFFDAD6)

    /** Avatar background colours; the eight slots [app.pingu.messages.core.util.Avatars] returns. */
    val avatarsLight = listOf(
        Color(0xFF00696E),
        Color(0xFF3F5F90),
        Color(0xFF6A5A8F),
        Color(0xFF8A5340),
        Color(0xFF3F6B4A),
        Color(0xFF7A5B1F),
        Color(0xFF8C4F63),
        Color(0xFF4C6358),
    )

    val avatarsDark = listOf(
        Color(0xFF4FD8DF),
        Color(0xFFA9C7FF),
        Color(0xFFD3BDFF),
        Color(0xFFFFB59C),
        Color(0xFFA5D2AF),
        Color(0xFFEACB84),
        Color(0xFFFFB1C6),
        Color(0xFFB2CCBE),
    )
}

/** The colours that define one accent, in one brightness. */
internal data class AccentTones(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
)

internal data class AccentScheme(val light: AccentTones, val dark: AccentTones)

/**
 * Accent definitions.
 *
 * Each is a Material 3 tonal set: a 40-tone primary for light mode and an 80-tone one for dark, so
 * contrast against the shared neutrals stays correct without a runtime colour engine.
 */
internal object Accents {

    val brand = AccentScheme(
        light = AccentTones(
            primary = Color(0xFF006A6C),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFF9CF1F2),
            onPrimaryContainer = Color(0xFF002020),
            secondary = Color(0xFF4A6363),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFCCE8E7),
            onSecondaryContainer = Color(0xFF051F1F),
            tertiary = Color(0xFF4B607C),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFD3E4FF),
            onTertiaryContainer = Color(0xFF041C35),
        ),
        dark = AccentTones(
            primary = Color(0xFF80D5D6),
            onPrimary = Color(0xFF003738),
            primaryContainer = Color(0xFF004F51),
            onPrimaryContainer = Color(0xFF9CF1F2),
            secondary = Color(0xFFB0CCCB),
            onSecondary = Color(0xFF1B3534),
            secondaryContainer = Color(0xFF324B4B),
            onSecondaryContainer = Color(0xFFCCE8E7),
            tertiary = Color(0xFFB3C8E8),
            onTertiary = Color(0xFF1C314B),
            tertiaryContainer = Color(0xFF334863),
            onTertiaryContainer = Color(0xFFD3E4FF),
        ),
    )

    val blue = AccentScheme(
        light = AccentTones(
            primary = Color(0xFF00639B),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFCEE5FF),
            onPrimaryContainer = Color(0xFF001D33),
            secondary = Color(0xFF51606F),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFD4E4F6),
            onSecondaryContainer = Color(0xFF0D1D2A),
            tertiary = Color(0xFF67587A),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFEDDCFF),
            onTertiaryContainer = Color(0xFF221533),
        ),
        dark = AccentTones(
            primary = Color(0xFF95CCFF),
            onPrimary = Color(0xFF003353),
            primaryContainer = Color(0xFF004A76),
            onPrimaryContainer = Color(0xFFCEE5FF),
            secondary = Color(0xFFB9C8DA),
            onSecondary = Color(0xFF233240),
            secondaryContainer = Color(0xFF394857),
            onSecondaryContainer = Color(0xFFD4E4F6),
            tertiary = Color(0xFFD2BFE7),
            onTertiary = Color(0xFF372A49),
            tertiaryContainer = Color(0xFF4E4061),
            onTertiaryContainer = Color(0xFFEDDCFF),
        ),
    )

    val indigo = AccentScheme(
        light = AccentTones(
            primary = Color(0xFF4C5CA9),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFDDE1FF),
            onPrimaryContainer = Color(0xFF00105C),
            secondary = Color(0xFF5B5D72),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFE0E0F9),
            onSecondaryContainer = Color(0xFF181A2C),
            tertiary = Color(0xFF77536D),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFFD7F1),
            onTertiaryContainer = Color(0xFF2D1228),
        ),
        dark = AccentTones(
            primary = Color(0xFFB9C3FF),
            onPrimary = Color(0xFF1A2B78),
            primaryContainer = Color(0xFF334390),
            onPrimaryContainer = Color(0xFFDDE1FF),
            secondary = Color(0xFFC4C4DD),
            onSecondary = Color(0xFF2D2F42),
            secondaryContainer = Color(0xFF434559),
            onSecondaryContainer = Color(0xFFE0E0F9),
            tertiary = Color(0xFFE6BAD7),
            onTertiary = Color(0xFF44263D),
            tertiaryContainer = Color(0xFF5D3C55),
            onTertiaryContainer = Color(0xFFFFD7F1),
        ),
    )

    val violet = AccentScheme(
        light = AccentTones(
            primary = Color(0xFF7345A3),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFF1DBFF),
            onPrimaryContainer = Color(0xFF2C0051),
            secondary = Color(0xFF67596F),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFEEDCF6),
            onSecondaryContainer = Color(0xFF22182A),
            tertiary = Color(0xFF805158),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFFD9DD),
            onTertiaryContainer = Color(0xFF321018),
        ),
        dark = AccentTones(
            primary = Color(0xFFDDB8FF),
            onPrimary = Color(0xFF431371),
            primaryContainer = Color(0xFF5A2C89),
            onPrimaryContainer = Color(0xFFF1DBFF),
            secondary = Color(0xFFD2C0DA),
            onSecondary = Color(0xFF382C40),
            secondaryContainer = Color(0xFF4F4257),
            onSecondaryContainer = Color(0xFFEEDCF6),
            tertiary = Color(0xFFF3B7C0),
            onTertiary = Color(0xFF4B252C),
            tertiaryContainer = Color(0xFF653A42),
            onTertiaryContainer = Color(0xFFFFD9DD),
        ),
    )

    val green = AccentScheme(
        light = AccentTones(
            primary = Color(0xFF2A6A45),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFAFF2C2),
            onPrimaryContainer = Color(0xFF00210F),
            secondary = Color(0xFF4E6355),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFD0E8D7),
            onSecondaryContainer = Color(0xFF0B1F14),
            tertiary = Color(0xFF3B6470),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFBFE9F8),
            onTertiaryContainer = Color(0xFF001F27),
        ),
        dark = AccentTones(
            primary = Color(0xFF94D5A7),
            onPrimary = Color(0xFF00391E),
            primaryContainer = Color(0xFF08512F),
            onPrimaryContainer = Color(0xFFAFF2C2),
            secondary = Color(0xFFB4CCBC),
            onSecondary = Color(0xFF203529),
            secondaryContainer = Color(0xFF364B3E),
            onSecondaryContainer = Color(0xFFD0E8D7),
            tertiary = Color(0xFFA3CDDB),
            onTertiary = Color(0xFF033641),
            tertiaryContainer = Color(0xFF224C58),
            onTertiaryContainer = Color(0xFFBFE9F8),
        ),
    )

    val amber = AccentScheme(
        light = AccentTones(
            primary = Color(0xFF7C5800),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFDEA6),
            onPrimaryContainer = Color(0xFF271900),
            secondary = Color(0xFF6C5C3F),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFF5E0BB),
            onSecondaryContainer = Color(0xFF251A04),
            tertiary = Color(0xFF4B6547),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFCDEBC5),
            onTertiaryContainer = Color(0xFF092008),
        ),
        dark = AccentTones(
            primary = Color(0xFFF7BD48),
            onPrimary = Color(0xFF412D00),
            primaryContainer = Color(0xFF5E4200),
            onPrimaryContainer = Color(0xFFFFDEA6),
            secondary = Color(0xFFD8C4A0),
            onSecondary = Color(0xFF3B2F15),
            secondaryContainer = Color(0xFF53452A),
            onSecondaryContainer = Color(0xFFF5E0BB),
            tertiary = Color(0xFFB1CFAA),
            onTertiary = Color(0xFF1E361C),
            tertiaryContainer = Color(0xFF344D31),
            onTertiaryContainer = Color(0xFFCDEBC5),
        ),
    )

    val rose = AccentScheme(
        light = AccentTones(
            primary = Color(0xFF9C4045),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFFFDADB),
            onPrimaryContainer = Color(0xFF40000A),
            secondary = Color(0xFF775657),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFFFDADB),
            onSecondaryContainer = Color(0xFF2C1516),
            tertiary = Color(0xFF74592F),
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFFFDDAF),
            onTertiaryContainer = Color(0xFF281800),
        ),
        dark = AccentTones(
            primary = Color(0xFFFFB3B4),
            onPrimary = Color(0xFF5F131A),
            primaryContainer = Color(0xFF7D2A2F),
            onPrimaryContainer = Color(0xFFFFDADB),
            secondary = Color(0xFFE6BDBD),
            onSecondary = Color(0xFF44292A),
            secondaryContainer = Color(0xFF5D3F40),
            onSecondaryContainer = Color(0xFFFFDADB),
            tertiary = Color(0xFFE3C18C),
            onTertiary = Color(0xFF412C05),
            tertiaryContainer = Color(0xFF5A421A),
            onTertiaryContainer = Color(0xFFFFDDAF),
        ),
    )
}
