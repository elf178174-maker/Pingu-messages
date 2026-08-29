package app.pingu.messages.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.pingu.messages.domain.model.AccentColor
import app.pingu.messages.domain.model.ThemeMode

/** Colours the app needs that Material 3 does not name, kept in one place rather than inline. */
@Immutable
data class PinguColors(
    val outgoingBubble: Color,
    val onOutgoingBubble: Color,
    val incomingBubble: Color,
    val onIncomingBubble: Color,
    val failedBubble: Color,
    val onFailedBubble: Color,
    val avatarBackgrounds: List<Color>,
    val onAvatar: Color,
    val unreadIndicator: Color,
    val composerBackground: Color,
    val divider: Color,
)

internal val LocalPinguColors = staticCompositionLocalOf {
    PinguColors(
        outgoingBubble = Color.Unspecified,
        onOutgoingBubble = Color.Unspecified,
        incomingBubble = Color.Unspecified,
        onIncomingBubble = Color.Unspecified,
        failedBubble = Color.Unspecified,
        onFailedBubble = Color.Unspecified,
        avatarBackgrounds = emptyList(),
        onAvatar = Color.Unspecified,
        unreadIndicator = Color.Unspecified,
        composerBackground = Color.Unspecified,
        divider = Color.Unspecified,
    )
}

/** Access to the app-specific colours: `PinguTheme.colors.outgoingBubble`. */
object PinguTheme {
    val colors: PinguColors
        @Composable
        @ReadOnlyComposable
        get() = LocalPinguColors.current
}

/**
 * The app theme.
 *
 * Dynamic colour is used when the platform offers it and the user has not turned it off, which is
 * the behaviour Android users expect from a system app. When it is unavailable or disabled, one of
 * the hand-built accents is used instead - not a washed-out fallback.
 */
@Composable
fun PinguTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: AccentColor = AccentColor.BRAND,
    useDynamicColor: Boolean = true,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current

    val baseScheme = when {
        useDynamicColor && dynamicAvailable ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> darkSchemeFor(accent)
        else -> lightSchemeFor(accent)
    }

    val scheme = if (dark && pureBlack) baseScheme.toPureBlack() else baseScheme

    val pinguColors = PinguColors(
        outgoingBubble = scheme.primary,
        onOutgoingBubble = scheme.onPrimary,
        incomingBubble = scheme.surfaceVariant,
        onIncomingBubble = scheme.onSurfaceVariant,
        failedBubble = scheme.errorContainer,
        onFailedBubble = scheme.onErrorContainer,
        avatarBackgrounds = if (dark) PinguPalette.avatarsDark else PinguPalette.avatarsLight,
        onAvatar = if (dark) Color(0xFF10201F) else Color.White,
        unreadIndicator = scheme.primary,
        composerBackground = scheme.surfaceContainerHigh,
        divider = scheme.outlineVariant,
    )

    CompositionLocalProvider(LocalPinguColors provides pinguColors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = PinguTypography,
            shapes = PinguShapes,
            content = content,
        )
    }
}

private fun tonesFor(accent: AccentColor): AccentScheme = when (accent) {
    AccentColor.BRAND -> Accents.brand
    AccentColor.BLUE -> Accents.blue
    AccentColor.INDIGO -> Accents.indigo
    AccentColor.VIOLET -> Accents.violet
    AccentColor.GREEN -> Accents.green
    AccentColor.AMBER -> Accents.amber
    AccentColor.ROSE -> Accents.rose
}

private fun lightSchemeFor(accent: AccentColor): ColorScheme {
    val tones = tonesFor(accent).light
    return lightColorScheme(
        primary = tones.primary,
        onPrimary = tones.onPrimary,
        primaryContainer = tones.primaryContainer,
        onPrimaryContainer = tones.onPrimaryContainer,
        secondary = tones.secondary,
        onSecondary = tones.onSecondary,
        secondaryContainer = tones.secondaryContainer,
        onSecondaryContainer = tones.onSecondaryContainer,
        tertiary = tones.tertiary,
        onTertiary = tones.onTertiary,
        tertiaryContainer = tones.tertiaryContainer,
        onTertiaryContainer = tones.onTertiaryContainer,
        background = PinguPalette.lightBackground,
        onBackground = PinguPalette.lightOnSurface,
        surface = PinguPalette.lightSurface,
        onSurface = PinguPalette.lightOnSurface,
        surfaceVariant = PinguPalette.lightSurfaceVariant,
        onSurfaceVariant = PinguPalette.lightOnSurfaceVariant,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = PinguPalette.lightSurfaceContainerLow,
        surfaceContainer = PinguPalette.lightSurfaceContainer,
        surfaceContainerHigh = PinguPalette.lightSurfaceContainerHigh,
        surfaceContainerHighest = PinguPalette.lightSurfaceContainerHigh,
        outline = PinguPalette.lightOutline,
        outlineVariant = PinguPalette.lightOutlineVariant,
        inverseSurface = PinguPalette.lightInverseSurface,
        inverseOnSurface = PinguPalette.lightInverseOnSurface,
        inversePrimary = tonesFor(accent).dark.primary,
        error = PinguPalette.lightError,
        onError = PinguPalette.lightOnError,
        errorContainer = PinguPalette.lightErrorContainer,
        onErrorContainer = PinguPalette.lightOnErrorContainer,
        scrim = PinguPalette.lightScrim,
    )
}

private fun darkSchemeFor(accent: AccentColor): ColorScheme {
    val tones = tonesFor(accent).dark
    return darkColorScheme(
        primary = tones.primary,
        onPrimary = tones.onPrimary,
        primaryContainer = tones.primaryContainer,
        onPrimaryContainer = tones.onPrimaryContainer,
        secondary = tones.secondary,
        onSecondary = tones.onSecondary,
        secondaryContainer = tones.secondaryContainer,
        onSecondaryContainer = tones.onSecondaryContainer,
        tertiary = tones.tertiary,
        onTertiary = tones.onTertiary,
        tertiaryContainer = tones.tertiaryContainer,
        onTertiaryContainer = tones.onTertiaryContainer,
        background = PinguPalette.darkBackground,
        onBackground = PinguPalette.darkOnSurface,
        surface = PinguPalette.darkSurface,
        onSurface = PinguPalette.darkOnSurface,
        surfaceVariant = PinguPalette.darkSurfaceVariant,
        onSurfaceVariant = PinguPalette.darkOnSurfaceVariant,
        surfaceContainerLowest = Color(0xFF0B0F0F),
        surfaceContainerLow = PinguPalette.darkSurfaceContainerLow,
        surfaceContainer = PinguPalette.darkSurfaceContainer,
        surfaceContainerHigh = PinguPalette.darkSurfaceContainerHigh,
        surfaceContainerHighest = Color(0xFF313636),
        outline = PinguPalette.darkOutline,
        outlineVariant = PinguPalette.darkOutlineVariant,
        inverseSurface = PinguPalette.darkInverseSurface,
        inverseOnSurface = PinguPalette.darkInverseOnSurface,
        inversePrimary = tonesFor(accent).light.primary,
        error = PinguPalette.darkError,
        onError = PinguPalette.darkOnError,
        errorContainer = PinguPalette.darkErrorContainer,
        onErrorContainer = PinguPalette.darkOnErrorContainer,
        scrim = PinguPalette.lightScrim,
    )
}

/**
 * OLED variant: the page background goes black while elevated surfaces stay slightly lifted, so
 * cards and sheets remain distinguishable instead of dissolving into the background.
 */
private fun ColorScheme.toPureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1B1B1B),
    surfaceContainerHighest = Color(0xFF242424),
)
