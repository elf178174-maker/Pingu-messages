package app.pingu.messages.data.mms.pdu

/**
 * MMS header field identifiers and well-known values from OMA-TS-MMS_ENC-V1_3.
 *
 * Only the fields Pingu Messages actually reads or writes are listed. The numbering is fixed by
 * the specification, so these constants are a transcription, not a design choice.
 */
object PduHeaders {

    // ---- Message types (X-Mms-Message-Type) -----------------------------------------------
    const val MESSAGE_TYPE_SEND_REQ = 0x80
    const val MESSAGE_TYPE_SEND_CONF = 0x81
    const val MESSAGE_TYPE_NOTIFICATION_IND = 0x82
    const val MESSAGE_TYPE_NOTIFYRESP_IND = 0x83
    const val MESSAGE_TYPE_RETRIEVE_CONF = 0x84
    const val MESSAGE_TYPE_ACKNOWLEDGE_IND = 0x85
    const val MESSAGE_TYPE_DELIVERY_IND = 0x86
    const val MESSAGE_TYPE_READ_REC_IND = 0x87
    const val MESSAGE_TYPE_READ_ORIG_IND = 0x88

    // ---- Header field ids -----------------------------------------------------------------
    const val BCC = 0x81
    const val CC = 0x82
    const val CONTENT_LOCATION = 0x83
    const val CONTENT_TYPE = 0x84
    const val DATE = 0x85
    const val DELIVERY_REPORT = 0x86
    const val DELIVERY_TIME = 0x87
    const val EXPIRY = 0x88
    const val FROM = 0x89
    const val MESSAGE_CLASS = 0x8A
    const val MESSAGE_ID = 0x8B
    const val MESSAGE_TYPE = 0x8C
    const val MMS_VERSION = 0x8D
    const val MESSAGE_SIZE = 0x8E
    const val PRIORITY = 0x8F
    const val READ_REPLY = 0x90
    const val REPORT_ALLOWED = 0x91
    const val RESPONSE_STATUS = 0x92
    const val RESPONSE_TEXT = 0x93
    const val SENDER_VISIBILITY = 0x94
    const val STATUS = 0x95
    const val SUBJECT = 0x96
    const val TO = 0x97
    const val TRANSACTION_ID = 0x98
    const val RETRIEVE_STATUS = 0x99
    const val RETRIEVE_TEXT = 0x9A
    const val READ_STATUS = 0x9B
    const val REPLY_CHARGING = 0x9C
    const val MESSAGE_REPORT = 0xA0
    const val CONTENT_CLASS = 0xAF

    // ---- Well-known values ----------------------------------------------------------------
    const val VALUE_YES = 0x80
    const val VALUE_NO = 0x81

    const val MMS_VERSION_1_0 = 0x10
    const val MMS_VERSION_1_1 = 0x11
    const val MMS_VERSION_1_2 = 0x12
    const val MMS_VERSION_1_3 = 0x13

    const val PRIORITY_LOW = 0x80
    const val PRIORITY_NORMAL = 0x81
    const val PRIORITY_HIGH = 0x82

    const val STATUS_EXPIRED = 0x80
    const val STATUS_RETRIEVED = 0x81
    const val STATUS_REJECTED = 0x82
    const val STATUS_DEFERRED = 0x83
    const val STATUS_UNRECOGNIZED = 0x84

    const val RESPONSE_STATUS_OK = 0x80

    /** Address the sender inserts to let the MMSC fill in the real number. */
    const val FROM_ADDRESS_PRESENT_TOKEN = 0x80
    const val FROM_INSERT_ADDRESS_TOKEN = 0x81

    /** Suffix carriers use to mark a phone-number address inside an MMS PDU. */
    const val PHONE_NUMBER_SUFFIX = "/TYPE=PLMN"
}
