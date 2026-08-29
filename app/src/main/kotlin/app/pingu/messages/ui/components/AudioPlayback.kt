package app.pingu.messages.ui.components

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * Playback for voice messages and audio attachments.
 *
 * One player per screen: starting a second clip stops the first, which is the behaviour people
 * expect and also the only way to avoid two voice messages talking over each other. Position is
 * polled while playing rather than pushed, because `MediaPlayer` has no progress callback.
 */
class AudioPlaybackController(private val context: Context) {

    private var player: MediaPlayer? = null

    val playingUri: MutableState<String?> = mutableStateOf(null)
    val positionMillis: MutableState<Int> = mutableStateOf(0)
    val durationMillis: MutableState<Int> = mutableStateOf(0)
    val isPaused: MutableState<Boolean> = mutableStateOf(false)

    fun toggle(uri: String) {
        if (playingUri.value == uri) {
            val current = player ?: return
            if (current.isPlaying) {
                current.pause()
                isPaused.value = true
            } else {
                current.start()
                isPaused.value = false
            }
            return
        }
        play(uri)
    }

    fun play(uri: String) {
        stop()
        runCatching {
            val created = MediaPlayer().apply {
                setDataSource(context, Uri.parse(uri))
                setOnCompletionListener { stop() }
                setOnErrorListener { _, _, _ ->
                    stop()
                    true
                }
                prepare()
                start()
            }
            player = created
            playingUri.value = uri
            durationMillis.value = created.duration.coerceAtLeast(0)
            positionMillis.value = 0
            isPaused.value = false
        }.onFailure { stop() }
    }

    fun seekTo(fraction: Float) {
        val current = player ?: return
        val target = (current.duration * fraction).toInt().coerceIn(0, current.duration)
        runCatching { current.seekTo(target) }
        positionMillis.value = target
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        playingUri.value = null
        positionMillis.value = 0
        durationMillis.value = 0
        isPaused.value = false
    }

    internal fun refreshPosition() {
        val current = player ?: return
        runCatching {
            if (current.isPlaying) positionMillis.value = current.currentPosition
        }
    }

    internal fun isActive(): Boolean = player != null
}

/**
 * Creates a controller tied to the composition, released when the screen goes away so the
 * microphone-adjacent audio focus and the media codec are never leaked.
 */
@Composable
fun rememberAudioPlaybackController(): AudioPlaybackController {
    val context = LocalContext.current
    val controller = remember(context) { AudioPlaybackController(context) }

    LaunchedEffect(controller.playingUri.value) {
        while (controller.isActive()) {
            controller.refreshPosition()
            delay(PROGRESS_POLL_MILLIS)
        }
    }

    DisposableEffect(controller) {
        onDispose { controller.stop() }
    }
    return controller
}

private const val PROGRESS_POLL_MILLIS = 200L
