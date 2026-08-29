package app.pingu.messages.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner radii.
 *
 * Restrained on purpose: rows and sheets get the Material 3 defaults, and only the composer field
 * and the message bubbles are fully rounded, where the shape carries meaning. Rounding everything
 * heavily is the fastest way to make an interface look generic.
 */
internal val PinguShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Bubble geometry, shared by the conversation screen and the reply preview. */
internal object BubbleShapes {
    val corner = 18.dp
    val tailCorner = 4.dp
    val cornerSmall = 6.dp
}
