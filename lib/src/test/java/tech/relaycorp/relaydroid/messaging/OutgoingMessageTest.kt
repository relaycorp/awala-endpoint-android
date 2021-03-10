package tech.relaycorp.relaydroid.messaging

import java.time.ZonedDateTime
import kotlin.random.Random
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.relaycorp.relaydroid.test.FirstPartyEndpointFactory
import tech.relaycorp.relaydroid.test.MessageFactory
import tech.relaycorp.relaydroid.test.ThirdPartyEndpointFactory
import tech.relaycorp.relaydroid.test.assertSameDateTime
import tech.relaycorp.relaynet.ramf.RecipientAddressType
import tech.relaycorp.relaynet.testing.pki.KeyPairSet

internal class OutgoingMessageTest {

    @Test
    fun buildForPublicRecipient_checkBaseValues() = runBlockingTest {
        val message = MessageFactory.buildOutgoing(RecipientAddressType.PUBLIC)
        val parcel = message.parcel

        assertEquals(message.recipientEndpoint.address, parcel.recipientAddress)
        assertEquals(message.parcelId.value, parcel.id)
        assertSameDateTime(message.parcelCreationDate, parcel.creationDate)
        assertEquals(message.ttl, parcel.ttl)
    }

    @Test
    fun buildForPublicRecipient_checkTTL() = runBlockingTest {
        val senderEndpoint = FirstPartyEndpointFactory.build()
        val recipientEndpoint = ThirdPartyEndpointFactory.buildPublic()

        val message = OutgoingMessage.build(
            "the type",
            Random.Default.nextBytes(10),
            senderEndpoint = senderEndpoint,
            recipientEndpoint = recipientEndpoint,
            parcelExpiryDate = ZonedDateTime.now().plusMinutes(1)
        )

        assertTrue(58 < message.ttl)
        assertTrue(message.ttl <= 60)
    }

    @Test
    fun buildForPublicRecipient_checkServiceMessage() = runBlockingTest {
        val message = MessageFactory.buildOutgoing(RecipientAddressType.PUBLIC)
        val parcel = message.parcel

        val serviceMessageDecrypted = parcel.unwrapPayload(KeyPairSet.PDA_GRANTEE.private)
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
            assertSameDateTime(message.parcelCreationDate, cert.startDate)
            assertSameDateTime(message.parcelExpiryDate, cert.expiryDate)
        }
    }

    @Test
    internal fun buildForPublicRecipient_checkSenderCertificateChain() = runBlockingTest {
        val message = MessageFactory.buildOutgoing(RecipientAddressType.PUBLIC)

        assertTrue(message.parcel.senderCertificateChain.isEmpty())
    }
}
