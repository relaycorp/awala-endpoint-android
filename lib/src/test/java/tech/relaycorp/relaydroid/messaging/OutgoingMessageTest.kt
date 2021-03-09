package tech.relaycorp.relaydroid.messaging

import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.relaycorp.relaydroid.endpoint.PublicThirdPartyEndpoint
import tech.relaycorp.relaydroid.test.FirstPartyEndpointFactory
import tech.relaycorp.relaydroid.test.MessageFactory
import tech.relaycorp.relaydroid.test.ThirdPartyEndpointFactory
import tech.relaycorp.relaydroid.test.assertSameDateTime
import tech.relaycorp.relaynet.messages.InvalidMessageException
import tech.relaycorp.relaynet.ramf.RecipientAddressType
import tech.relaycorp.relaynet.testing.pki.PDACertPath
import java.time.ZonedDateTime
import tech.relaycorp.relaynet.testing.pki.KeyPairSet

internal class OutgoingMessageTest {

    @Test(expected = InvalidMessageException::class)
    internal fun buildInvalidMessage() = runBlockingTest {
        OutgoingMessage.build(
            "the type",
            ByteArray(0),
            FirstPartyEndpointFactory.build(),
            ThirdPartyEndpointFactory.buildPublic(),
            creationDate = ZonedDateTime.now().plusDays(1)
        )
    }

    @Test
    fun buildForPublicRecipient_checkBaseValues() = runBlockingTest {
        val message = MessageFactory.buildOutgoing(RecipientAddressType.PUBLIC)
        val parcel = message.parcel

        assertEquals(message.recipientEndpoint.address, parcel.recipientAddress)
        assertEquals(message.id.value, parcel.id)
        assertSameDateTime(message.creationDate, parcel.creationDate)
        assertEquals(message.ttl, parcel.ttl)
    }

    @Test
    fun buildForPublicRecipient_checkServiceMessage() = runBlockingTest {
        val message = MessageFactory.buildOutgoing(RecipientAddressType.PUBLIC)
        val parcel = message.parcel

        val serviceMessageDecrypted = parcel.unwrapPayload(KeyPairSet.PUBLIC_GW.private)
        assertEquals(MessageFactory.serviceMessage.type, serviceMessageDecrypted.type)
        assertArrayEquals(MessageFactory.serviceMessage.content, serviceMessageDecrypted.content)
    }

    @Test
    internal fun buildForPublicRecipient_checkSenderCertificate() = runBlockingTest {
        val message = MessageFactory.buildOutgoing(RecipientAddressType.PUBLIC)
        val parcel = message.parcel

        parcel.senderCertificate.let { cert ->
            cert.validate()
            assertEquals(message.senderEndpoint.keyPair.public, cert.subjectPublicKey)
            assertSameDateTime(message.creationDate, cert.startDate)
            assertSameDateTime(message.expiryDate, cert.expiryDate)
        }
    }

    @Test
    internal fun buildForPublicRecipient_checkSenderCertificateChain() = runBlockingTest {
        val message = MessageFactory.buildOutgoing(RecipientAddressType.PUBLIC)

        assertTrue(message.parcel.senderCertificateChain.isEmpty())
    }
}
