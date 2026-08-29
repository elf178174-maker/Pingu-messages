package app.pingu.messages.ui.util

import android.content.Context
import app.pingu.messages.R
import app.pingu.messages.core.util.FileSizes
import app.pingu.messages.domain.model.AppError

/**
 * Turns an [AppError] into a sentence a person can act on.
 *
 * Every case says what happened and, where there is one, what to do about it. "Something went
 * wrong" appears only for genuinely unexpected failures, because using it everywhere is how an app
 * teaches its users that error messages are not worth reading.
 */
fun errorMessage(context: Context, error: AppError): String = when (error) {
    AppError.NotDefaultSmsApp -> context.getString(R.string.error_not_default_app)
    AppError.NoSim -> context.getString(R.string.error_no_sim)
    AppError.NoService -> context.getString(R.string.error_send_failed_no_service)
    AppError.NoMobileData -> context.getString(R.string.error_mms_no_data)
    is AppError.MessageTooLarge -> context.getString(
        R.string.error_mms_too_large,
        FileSizes.format(error.limitBytes.toLong()),
    )

    is AppError.PermissionRequired -> context.getString(R.string.error_permission_generic)
    is AppError.AttachmentUnreadable -> context.getString(R.string.error_attachment_unreadable)
    AppError.NoHandlingApp -> context.getString(R.string.error_no_app_for_action)
    is AppError.SendFailed -> context.getString(
        when (error.resultCode) {
            android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE ->
                R.string.error_send_failed_no_service

            android.telephony.SmsManager.RESULT_ERROR_RADIO_OFF ->
                R.string.error_send_failed_radio_off

            android.telephony.SmsManager.RESULT_ERROR_NULL_PDU ->
                R.string.error_send_failed_null_pdu

            else -> R.string.error_send_failed_generic
        },
    )

    AppError.RecordingFailed -> context.getString(R.string.error_recording_failed)
    AppError.LocationUnavailable -> context.getString(R.string.error_location_unavailable)
    is AppError.Unexpected -> context.getString(R.string.error_generic)
}
