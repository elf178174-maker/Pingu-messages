package app.pingu.messages.platform

import android.content.BroadcastReceiver
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Runs suspending work from a broadcast receiver.
 *
 * `goAsync` keeps the process alive past `onReceive`, which is the only supported way for a
 * receiver to do I/O. The platform gives a receiver roughly ten seconds before it is killed, so the
 * work is bounded: exceeding it would mean the message is dropped silently, and a timeout that
 * logs is far easier to diagnose than a process that vanishes.
 */
object BroadcastScope {

    private const val TAG = "BroadcastScope"
    private const val TIMEOUT_MILLIS = 9_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun launch(receiver: BroadcastReceiver, name: String, block: suspend () -> Unit) {
        val pendingResult = receiver.goAsync()
        scope.launch {
            try {
                withTimeout(TIMEOUT_MILLIS) { block() }
            } catch (error: Exception) {
                Log.e(TAG, "$name failed", error)
            } finally {
                runCatching { pendingResult.finish() }
            }
        }
    }
}
