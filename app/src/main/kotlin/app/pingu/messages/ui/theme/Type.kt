package app.pingu.messages.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography.
 *
 * The system font is used deliberately: it is the one the user chose, it already supports every
 * script their messages might arrive in, and bundling a display face would add weight while making
 * the app look less like the rest of their phone.
 *
 * Body line heights are set explicitly so multi-line message bubbles breathe: the Material default
 * is tuned for prose, and message text reads better slightly tighter.
 */
internal val PinguTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = FontFamily.Default),
        displayMedium = base.displayMedium.copy(fontFamily = FontFamily.Default),
        displaySmall = base.displaySmall.copy(fontFamily = FontFamily.Default),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Normal),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Normal),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Normal),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Medium),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.15.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.2.sp,
        ),
        bodySmall = base.bodySmall,
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium,
        labelSmall = base.labelSmall,
    )
}
