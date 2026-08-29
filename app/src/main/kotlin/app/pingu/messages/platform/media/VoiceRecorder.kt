package app.pingu.messages.platform.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.io.File

/**
 * Records voice messages.
 *
 * AAC in an MPEG-4 container: it is what every phone can both record and play, it is small enough
 * to fit inside an MMS, and it is what other messaging apps send, so a recording made here plays
 * on the recipient's phone whatever they use.
 *
 * The recorder holds the microphone for exactly as long as the button is held. [cancel] deletes the
 * file as well as releasing the hardware, so an abandoned recording leaves nothing behind.
 */
class VoiceRecorder(
    private val context: Context,
    private val fileStore: AppFileStore,
) {

    /** Snapshot of an in-progress recording, polled by the UI for the timer and the level meter. */
    data class State(
        val file: File,
        val startedAtMillis: Long,
    ) {
        fun elapsedMillis(): Long = SystemClock.elapsedRealtime() - startedAtMillis
    }

    private var recorder: MediaRecorder? = null
    private var current: State? = null

    val isRecording: Boolean get() = recorder != null

    fun start(): Result<State> {
        if (isRecording) return Result.failure(IllegalStateException("Already recording"))
        return runCatching {
            val file = fileStore.createCacheFile(AppFileStore.DIR_VOICE, ".m4a")
            @Suppress("DEPRECATION")
            val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }
            newRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(BIT_RATE)
                setAudioSamplingRate(SAMPLE_RATE)
                setAudioChannels(1)
                setMaxDuration(MAX_DURATION_MILLIS)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = newRecorder
            State(file, SystemClock.elapsedRealtime()).also { current = it }
        }.onFailure { error ->
            Log.w(TAG, "Could not start recording", error)
            releaseQuietly()
        }
    }

    /**
     * Stops and keeps the recording.
     *
     * A recording shorter than [MIN_DURATION_MILLIS] is treated as an accidental tap: it is
     * discarded and null is returned, which is what every messenger does with a stray press.
     */
    fun stop(): Result<File?> {
        val state = current ?: return Result.success(null)
        val elapsed = state.elapsedMillis()
        return runCatching {
            recorder?.stop()
            releaseQuietly()
            if (elapsed < MIN_DURATION_MILLIS || state.file.length() == 0L) {
                fileStore.deleteQuietly(state.file)
                null
            } else {
                state.file
            }
        }.onFailure {
            // MediaRecorder.stop throws when it never captured anything; the file is unusable.
            releaseQuietly()
            fileStore.deleteQuietly(state.file)
        }
    }

    fun cancel() {
        val state = current
        runCatching { recorder?.stop() }
        releaseQuietly()
        state?.let { fileStore.deleteQuietly(it.file) }
    }

    /** Current input level, 0..1, for the waveform while recording. */
    fun currentAmplitude(): Float = try {
        val amplitude = recorder?.maxAmplitude ?: 0
        (amplitude.toFloat() / MAX_AMPLITUDE).coerceIn(0f, 1f)
    } catch (error: Exception) {
        0f
    }

    private fun releaseQuietly() {
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
        current = null
    }

    private companion object {
        const val TAG = "VoiceRecorder"
        const val BIT_RATE = 64_000
        const val SAMPLE_RATE = 44_100
        const val MAX_DURATION_MILLIS = 5 * 60 * 1000
        const val MIN_DURATION_MILLIS = 700L
        const val MAX_AMPLITUDE = 32_767f
    }
}
