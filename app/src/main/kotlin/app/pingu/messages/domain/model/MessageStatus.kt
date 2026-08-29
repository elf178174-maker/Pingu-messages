package app.pingu.messages.domain.model

/**
 * The lifecycle of a message as far as SMS and MMS can actually report it.
 *
 * Deliberately absent: a "read" state for SMS. The GSM standard has no read receipt for SMS at all,
 * so claiming one would be fiction. MMS *can* carry a read report, and [READ] is only ever set from
 * a real M-Read-Orig.ind returned by the recipient's phone.
 */
enum class MessageStatus {
    /** Queued locally, handed to the radio, or waiting for the scheduler. */
    SENDING,

    /** The network accepted the message. */
    SENT,

    /** A delivery report came back from the network. Only available if reports are requested. */
    DELIVERED,

    /** An MMS read report came back. Never set for SMS. */
    READ,

    /** Sending failed; the message can be retried. */
    FAILED,

    /** An incoming message. */
    RECEIVED,

    /** An MMS whose notification arrived but whose body has not been downloaded yet. */
    PENDING_DOWNLOAD,

    /** An MMS currently being fetched from the carrier's MMSC. */
    DOWNLOADING,

    /** Downloading the MMS body failed; the user can retry until the carrier expires it. */
    DOWNLOAD_FAILED,

    /** The carrier expired the MMS before it was downloaded. */
    EXPIRED,

    /** Waiting for its scheduled send time. */
    SCHEDULED,

    /** Saved as a draft. */
    DRAFT,
    ;

    val isOutgoing: Boolean
        get() = this == SENDING || this == SENT || this == DELIVERED || this == READ ||
            this == FAILED || this == SCHEDULED || this == DRAFT

    val isFailure: Boolean
        get() = this == FAILED || this == DOWNLOAD_FAILED || this == EXPIRED

    val isInFlight: Boolean
        get() = this == SENDING || this == DOWNLOADING
}
