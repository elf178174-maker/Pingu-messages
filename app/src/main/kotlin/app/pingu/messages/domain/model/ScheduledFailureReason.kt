package app.pingu.messages.domain.model

/**
 * Why a scheduled message did not go out.
 *
 * These keys are persisted, so the UI can render the reason in whatever language the phone is set
 * to when it is read - rather than in whatever language it was in when the send failed. They are
 * deliberately plain strings: the column is a nullable text field shared with older rows.
 */
object ScheduledFailureReason {
    const val NOT_DEFAULT_SMS_APP = "NOT_DEFAULT_SMS_APP"
    const val NO_SIM = "NO_SIM"
    const val NO_SERVICE = "NO_SERVICE"
    const val NO_MOBILE_DATA = "NO_MOBILE_DATA"
    const val TOO_LARGE = "TOO_LARGE"
    const val PERMISSION_REQUIRED = "PERMISSION_REQUIRED"
    const val ATTACHMENT_UNREADABLE = "ATTACHMENT_UNREADABLE"
    const val NO_HANDLING_APP = "NO_HANDLING_APP"
    const val SEND_FAILED = "SEND_FAILED"
    const val UNEXPECTED = "UNEXPECTED"
}
