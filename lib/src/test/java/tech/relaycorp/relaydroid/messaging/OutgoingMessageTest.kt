package tech.relaycorp.relaydroid.messaging

import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.relaycorp.relaydroid.PublicThirdPartyEndpoint
import tech.relaycorp.relaydroid.test.FirstPartyEndpointFactory
import tech.relaycorp.relaydroid.test.MessageFactory
import tech.relaycorp.relaynet.ramf.RecipientAddressType
import java.time.Duration
import java.time.ZonedDateTime

internal class OutgoingMessageTest {

    @Test(expected = InvalidMessageException::class)
    internal fun buildInvalidMessage() = runBlockingTest {
        OutgoingMessage.build(
            ByteArray(0),
            FirstPartyEndpointFactory.build(),
            PublicThirdPartyEndpoint("http://example.org"),
            creationDate = ZonedDateTime.now().plusDays(1)
        )
    }

    @Test
    internal fun buildForPublicRecipient_checkBaseValues() = runBlockingTest {
        val message = MessageFactory.buildOutgoing(RecipientAddressType.PUBLIC)
        val parcel = message.parcel

        assertEquals(message.recipientEndpoint.address, parcel.recipientAddress)
        assertArrayEquals(message.payload, parcel.payload)
        assertEquals(message.id.value, parcel.id)
        assertSameDateTime(message.creationDate, parcel.creationDate)
        assertEquals(message.ttl, parcel.ttl)
    }

    @Test
    internal fun buildForPublicRecipient_checkSenderCertificate() = runBlockingTest {
        val message = MessageFactory.buildOutgoing(RecipientAddressType.PUBLIC)
        val parcel = message.parcel

        parcel.senderCertificate.let { cert ->
            cert.validate()
            assertEquals(message.senderEndpoint.keyPair.public, cert.subjectPublicKey)
            assertSameDateTime(message.creationDate, cert.startDate)
            assertSameDateTime(message.expirationDate, cert.expiryDate)
        }
    }

    @Test
    internal fun buildForPublicRecipient_checkSenderCertificateChain() = runBlockingTest {
        val message = MessageFactory.buildOutgoing(RecipientAddressType.PUBLIC)
        val parcel = message.parcel

        assertArrayEquals(
            arrayOf(message.senderEndpoint.gatewayCertificate),
            parcel.senderCertificateChain.toTypedArray()
        )
    }

    private fun assertSameDateTime(date1: ZonedDateTime, date2: ZonedDateTime) =
        assertTrue(Duration.between(date1, date2).seconds < 2)
}
