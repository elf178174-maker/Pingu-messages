package app.pingu.messages.ui.screens.conversation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput

/** Horizontal distance, in pixels, past which releasing cancels the recording instead of sending. */
const val RECORDING_CANCEL_THRESHOLD_PX = 140f

/** Tap to send, long press to schedule. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.sendGestures(
    enabled: Boolean,
    onSend: () -> Unit,
    onLongPress: () -> Unit,
): Modifier = combinedClickable(
    enabled = enabled,
    onClick = onSend,
    onLongClick = onLongPress,
)

/**
 * Hold to record, release to send, slide away to cancel.
 *
 * Implemented with the raw pointer stream rather than `detectDragGestures` because recording has to
 * begin on the press itself: waiting for a long-press timeout would clip the first word of every
 * voice message.
 */
fun Modifier.recordGestures(
    enabled: Boolean,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    onDrag: (Float) -> Unit,
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        onStart()

        var horizontalTravel = 0f
        var released = false
        while (!released) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                released = true
            } else if (change.pressed) {
                horizontalTravel += change.positionChange().x
                onDrag(horizontalTravel)
                change.consume()
            } else {
                change.consume()
                released = true
            }
        }

        if (horizontalTravel < -RECORDING_CANCEL_THRESHOLD_PX) onCancel() else onFinish()
    }
}
