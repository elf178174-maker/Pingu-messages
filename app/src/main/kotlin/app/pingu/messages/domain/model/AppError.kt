package app.pingu.messages.domain.model

/**
 * Failures the UI has to explain to a person, rather than a stack trace.
 *
 * Every case carries enough information for the presentation layer to write a specific sentence and
 * to offer the right recovery action, which is what turns "something went wrong" into "no mobile
 * service, we will not send this until you have signal".
 */
sealed interface AppError {

    /** The app is not the default SMS app, so the platform forbids sending. */
    data object NotDefaultSmsApp : AppError

    /** No SIM or no telephony hardware at all. */
    data object NoSim : AppError

    /** The radio is off (airplane mode) or there is no service. */
    data object NoService : AppError

    /** Mobile data is required for MMS and is unavailable. */
    data object NoMobileData : AppError

    /** The carrier rejected the message size. */
    data class MessageTooLarge(val limitBytes: Int) : AppError

    /** A runtime permission is missing. */
    data class PermissionRequired(val permission: String) : AppError

    /** A selected file could not be opened or read. */
    data class AttachmentUnreadable(val uri: String) : AppError

    /** No activity on the device can handle an intent the user asked for. */
    data object NoHandlingApp : AppError

    /** Sending failed with a platform result code; [resultCode] is `SmsManager.RESULT_*`. */
    data class SendFailed(val resultCode: Int) : AppError

    /** Anything unexpected. [cause] is kept for logs but never shown verbatim. */
    data class Unexpected(val cause: Throwable? = null) : AppError
}

/** A result that either succeeded or carries an [AppError] the UI can explain. */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = (this as? Success)?.value

    fun errorOrNull(): AppError? = (this as? Failure)?.error
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T> runCatchingOutcome(block: () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (error: SecurityException) {
    Outcome.Failure(AppError.PermissionRequired(error.message.orEmpty()))
} catch (error: Exception) {
    Outcome.Failure(AppError.Unexpected(error))
}
