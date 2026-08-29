package app.pingu.messages.data.mms

import app.pingu.messages.data.mms.pdu.MmsPart
import app.pingu.messages.data.mms.pdu.PduCharsets
import app.pingu.messages.data.mms.pdu.PduComposer
import app.pingu.messages.data.mms.pdu.PduContentTypes
import app.pingu.messages.data.mms.pdu.PduHeaders
import app.pingu.messages.data.mms.pdu.PduParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The MMS encoder and decoder, checked against each other.
 *
 * A PDU that cannot be read back is a message the carrier will reject, and there is no way to find
 * that out on a device without spending real money on a failed send. Round-tripping every field the
 * app writes is the closest thing to a wire test that can run on a build server.
 */
class PduRoundTripTest {

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02, 0x03, 0xFF.toByte(), 0xD9.toByte())

    @Test
    fun `a text-only send request round-trips`() {
        val pdu = PduComposer.composeSendRequest(
            transactionId = "T0123456789ABCDEF",
            recipients = listOf("+447700900123"),
            parts = listOf(
                MmsPart(
                    contentType = PduContentTypes.TEXT_PLAIN,
                    data = "Hello from Pingu".toByteArray(Charsets.UTF_8),
                    fileName = "text.txt",
                    charsetMib = PduCharsets.UTF_8,
                ),
            ),
        )

        val decoded = PduParser.parse(pdu)
        assertThat(decoded).isNotNull()
        assertThat(decoded!!.messageType).isEqualTo(PduHeaders.MESSAGE_TYPE_SEND_REQ)
        assertThat(decoded.transactionId).isEqualTo("T0123456789ABCDEF")
        assertThat(decoded.to.map { PduParser.stripAddressType(it) }).containsExactly("+447700900123")
        assertThat(decoded.bodyText).isEqualTo("Hello from Pingu")
    }

    @Test
    fun `a subject survives the round trip`() {
        val pdu = PduComposer.composeSendRequest(
            transactionId = "T1",
            recipients = listOf("+447700900123"),
            subject = "Weekend plans",
            parts = listOf(
                MmsPart(PduContentTypes.TEXT_PLAIN, "See attached".toByteArray(), fileName = "text.txt"),
            ),
        )
        val decoded = PduParser.parse(pdu)
        assertThat(decoded?.subject).isEqualTo("Weekend plans")
    }

    @Test
    fun `binary attachments survive byte for byte`() {
        val pdu = PduComposer.composeSendRequest(
            transactionId = "T2",
            recipients = listOf("+447700900123"),
            parts = listOf(
                MmsPart(PduContentTypes.TEXT_PLAIN, "Look".toByteArray(), fileName = "text.txt"),
                MmsPart("image/jpeg", jpeg, fileName = "photo.jpg"),
            ),
        )

        val decoded = PduParser.parse(pdu)
        assertThat(decoded).isNotNull()
        val image = decoded!!.contentParts.first { it.contentType == "image/jpeg" }
        assertThat(image.data).isEqualTo(jpeg)
        assertThat(image.contentLocation).isEqualTo("photo.jpg")
    }

    @Test
    fun `a presentation part is generated and excluded from the content`() {
        val pdu = PduComposer.composeSendRequest(
            transactionId = "T3",
            recipients = listOf("+447700900123"),
            parts = listOf(MmsPart("image/jpeg", jpeg, fileName = "photo.jpg")),
        )
        val decoded = PduParser.parse(pdu)!!
        assertThat(decoded.parts.any { it.isSmil }).isTrue()
        assertThat(decoded.contentParts.any { it.isSmil }).isFalse()
        // The SMIL must reference the media part or handsets show an empty slide.
        val smil = decoded.parts.first { it.isSmil }
        assertThat(String(smil.data, Charsets.UTF_8)).contains("photo.jpg")
    }

    @Test
    fun `several recipients are all encoded`() {
        val pdu = PduComposer.composeSendRequest(
            transactionId = "T4",
            recipients = listOf("+447700900123", "+447700900124", "+447700900125"),
            parts = listOf(MmsPart(PduContentTypes.TEXT_PLAIN, "Group".toByteArray(), fileName = "t.txt")),
        )
        val decoded = PduParser.parse(pdu)!!
        assertThat(decoded.to.map { PduParser.stripAddressType(it) })
            .containsExactly("+447700900123", "+447700900124", "+447700900125")
    }

    @Test
    fun `non-latin text keeps its characters`() {
        val body = "Привет, как дела?"
        val pdu = PduComposer.composeSendRequest(
            transactionId = "T5",
            recipients = listOf("+447700900123"),
            parts = listOf(
                MmsPart(
                    PduContentTypes.TEXT_PLAIN,
                    body.toByteArray(Charsets.UTF_8),
                    fileName = "text.txt",
                    charsetMib = PduCharsets.UTF_8,
                ),
            ),
        )
        assertThat(PduParser.parse(pdu)?.bodyText).isEqualTo(body)
    }

    @Test
    fun `delivery and read report flags are written`() {
        val pdu = PduComposer.composeSendRequest(
            transactionId = "T6",
            recipients = listOf("+447700900123"),
            parts = listOf(MmsPart(PduContentTypes.TEXT_PLAIN, "x".toByteArray(), fileName = "t.txt")),
            requestDeliveryReport = true,
            requestReadReport = true,
        )
        val decoded = PduParser.parse(pdu)!!
        assertThat(decoded.deliveryReport).isEqualTo(PduHeaders.VALUE_YES)
        assertThat(decoded.readReport).isEqualTo(PduHeaders.VALUE_YES)
    }

    @Test
    fun `an acknowledgement is a valid pdu`() {
        val decoded = PduParser.parse(PduComposer.composeAcknowledge("T7"))
        assertThat(decoded?.messageType).isEqualTo(PduHeaders.MESSAGE_TYPE_ACKNOWLEDGE_IND)
        assertThat(decoded?.transactionId).isEqualTo("T7")
    }

    @Test
    fun `a notify response carries the status`() {
        val decoded = PduParser.parse(
            PduComposer.composeNotifyResponse("T8", PduHeaders.STATUS_DEFERRED),
        )
        assertThat(decoded?.messageType).isEqualTo(PduHeaders.MESSAGE_TYPE_NOTIFYRESP_IND)
        assertThat(decoded?.status).isEqualTo(PduHeaders.STATUS_DEFERRED)
    }

    @Test
    fun `a read report names the message it refers to`() {
        val decoded = PduParser.parse(
            PduComposer.composeReadReport("mid-42", "+447700900123", 1_700_000_000L),
        )
        assertThat(decoded?.messageType).isEqualTo(PduHeaders.MESSAGE_TYPE_READ_REC_IND)
        assertThat(decoded?.messageId).isEqualTo("mid-42")
        assertThat(decoded?.to?.map { PduParser.stripAddressType(it) })
            .containsExactly("+447700900123")
    }

    @Test
    fun `an empty recipient list is rejected rather than sent`() {
        val error = runCatching {
            PduComposer.composeSendRequest("T9", emptyList(), emptyList())
        }.exceptionOrNull()
        assertThat(error).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `random bytes are rejected instead of being misread`() {
        assertThat(PduParser.parse(ByteArray(32) { it.toByte() })).isNull()
        assertThat(PduParser.parse(ByteArray(0))).isNull()
    }

    @Test
    fun `phone numbers are tagged with their numbering plan`() {
        assertThat(PduComposer.encodeAddress("+447700900123")).isEqualTo("+447700900123/TYPE=PLMN")
        assertThat(PduComposer.encodeAddress("someone@example.com")).isEqualTo("someone@example.com")
    }
}
