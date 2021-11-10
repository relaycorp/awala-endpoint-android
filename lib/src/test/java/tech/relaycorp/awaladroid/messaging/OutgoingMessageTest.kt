package tech.relaycorp.awaladroid.messaging

import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.relaycorp.awaladroid.endpoint.PrivateThirdPartyEndpoint
import tech.relaycorp.awaladroid.test.MessageFactory
import tech.relaycorp.awaladroid.test.MockContextTestCase
import tech.relaycorp.awaladroid.test.assertSameDateTime
import tech.relaycorp.relaynet.ramf.RecipientAddressType

internal class OutgoingMessageTest : MockContextTestCase() {

    // Public Recipient

    @Test
    fun buildForPublicRecipient_checkBaseValues() = runBlockingTest {
        val channel = createEndpointChannel(RecipientAddressType.PUBLIC)

        val message = MessageFactory.buildOutgoing(channel)

        assertEquals(message.recipientEndpoint.address, message.parcel.recipientAddress)
        assertEquals(message.parcelId.value, message.parcel.id)
        assertSameDateTime(message.parcelCreationDate, message.parcel.creationDate)
        assertEquals(message.ttl, message.parcel.ttl)
    }

    @Test
    fun buildForPublicRecipient_checkTTL() = runBlockingTest {
        val (senderEndpoint, recipientEndpoint) = createEndpointChannel(RecipientAddressType.PUBLIC)

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
    fun buildForPublicRecipient_expiryDateDefaultsToMax() = runBlockingTest {
        val (senderEndpoint, recipientEndpoint) = createEndpointChannel(RecipientAddressType.PUBLIC)

        val message = OutgoingMessage.build(
            "the type",
            Random.Default.nextBytes(10),
            senderEndpoint = senderEndpoint,
            recipientEndpoint = recipientEndpoint,
        )

        val ttlExpected =
            Duration.between(ZonedDateTime.now(), OutgoingMessage.maxExpiryDate()).seconds
        assertTrue(abs(ttlExpected - message.ttl) < 2)
    }

    @Test
    fun buildForPublicRecipient_checkServiceMessage() = runBlockingTest {
        val channel = createEndpointChannel(RecipientAddressType.PUBLIC)

        val message = MessageFactory.buildOutgoing(channel)

        val (serviceMessageDecrypted) =
            message.parcel.unwrapPayload(channel.thirdPartySessionKeyPair.privateKey)
        assertEquals(MessageFactory.serviceMessage.type, serviceMessageDecrypted.type)
        assertArrayEquals(MessageFactory.serviceMessage.content, serviceMessageDecrypted.content)
    }

    @Test
    internal fun buildForPublicRecipient_checkSenderCertificate() = runBlockingTest {
        val channel = createEndpointChannel(RecipientAddressType.PUBLIC)

        val message = MessageFactory.buildOutgoing(channel)

        message.parcel.senderCertificate.let { cert ->
            cert.validate()
            assertEquals(
                message.senderEndpoint.identityCertificate.subjectPublicKey,
                cert.subjectPublicKey,
            )
            assertSameDateTime(message.parcelCreationDate, cert.startDate)
            assertSameDateTime(message.parcelExpiryDate, cert.expiryDate)
        }
    }

    @Test
    internal fun buildForPublicRecipient_checkSenderCertificateChain() = runBlockingTest {
        val channel = createEndpointChannel(RecipientAddressType.PUBLIC)

        val message = MessageFactory.buildOutgoing(channel)

        assertTrue(message.parcel.senderCertificateChain.isEmpty())
    }

    // Private Recipient

    @Test
    fun buildForPrivateRecipient_checkBaseValues() = runBlockingTest {
        val channel = createEndpointChannel(RecipientAddressType.PRIVATE)
        val message = MessageFactory.buildOutgoing(channel)

        assertEquals(message.recipientEndpoint.address, message.parcel.recipientAddress)
        assertEquals(message.parcelId.value, message.parcel.id)
        assertSameDateTime(message.parcelCreationDate, message.parcel.creationDate)
        assertEquals(message.ttl, message.parcel.ttl)
    }

    @Test
    internal fun buildForPrivateRecipient_checkSenderCertificate() = runBlockingTest {
        val channel = createEndpointChannel(RecipientAddressType.PRIVATE)

        val message = MessageFactory.buildOutgoing(channel)

        assertEquals(
            (message.recipientEndpoint as PrivateThirdPartyEndpoint).pda,
            message.parcel.senderCertificate
        )
    }

    @Test
    internal fun buildForPrivateRecipient_checkSenderCertificateChain() = runBlockingTest {
        val channel = createEndpointChannel(RecipientAddressType.PRIVATE)

        val message = MessageFactory.buildOutgoing(channel)

        assertArrayEquals(
            (message.recipientEndpoint as PrivateThirdPartyEndpoint).pdaChain.toTypedArray(),
            message.parcel.senderCertificateChain.toTypedArray()
        )
    }
}
