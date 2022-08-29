package tech.relaycorp.awaladroid.messaging

import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import tech.relaycorp.awaladroid.endpoint.PrivateThirdPartyEndpoint
import tech.relaycorp.awaladroid.endpoint.PublicThirdPartyEndpoint
import tech.relaycorp.awaladroid.test.MessageFactory
import tech.relaycorp.awaladroid.test.MockContextTestCase
import tech.relaycorp.awaladroid.test.RecipientAddressType
import tech.relaycorp.awaladroid.test.assertSameDateTime

internal class OutgoingMessageTest : MockContextTestCase() {

    @Test
    fun build_creationDate() = runTest {
        val channel = createEndpointChannel(RecipientAddressType.PRIVATE)
        val dateBeforeCreation = ZonedDateTime.now()

        val message = MessageFactory.buildOutgoing(channel)

        assertTrue(dateBeforeCreation.minusMinutes(5) <= message.parcel.creationDate)
        assertTrue(message.parcel.creationDate <= ZonedDateTime.now().minusMinutes(5))
    }

    @Test
    fun build_defaultExpiryDate() = runTest {
        val channel = createEndpointChannel(RecipientAddressType.PUBLIC)

        val message = MessageFactory.buildOutgoing(channel)

        val difference = Duration.between(
            message.parcel.expiryDate,
            message.parcel.creationDate.plusDays(180)
        )
        assertTrue(abs(difference.toDays()) == 0L)
    }

    @Test
    fun build_customExpiryDate() = runTest {
        val (senderEndpoint, recipientEndpoint) = createEndpointChannel(RecipientAddressType.PUBLIC)
        val parcelExpiryDate = ZonedDateTime.now().plusMinutes(1)

        val message = OutgoingMessage.build(
            "the type",
            Random.Default.nextBytes(10),
            senderEndpoint,
            recipientEndpoint,
            parcelExpiryDate
        )

        val differenceSeconds =
            Duration.between(message.parcel.expiryDate, parcelExpiryDate).seconds
        assertTrue(abs(differenceSeconds) < 3)
    }

    // Public Recipient

    @Test
    fun buildForPublicRecipient_checkBaseValues() = runTest {
        val channel = createEndpointChannel(RecipientAddressType.PUBLIC)
        val recipientPublicEndpoint = channel.thirdPartyEndpoint as PublicThirdPartyEndpoint

        val message = MessageFactory.buildOutgoing(channel)

        assertEquals(message.recipientEndpoint.nodeId, message.parcel.recipient.id)
        assertEquals(recipientPublicEndpoint.internetAddress, message.parcel.recipient.internetAddress)
        assertEquals(message.parcelId.value, message.parcel.id)
        assertSameDateTime(message.parcelCreationDate, message.parcel.creationDate)
        assertEquals(message.ttl, message.parcel.ttl)
    }

    @Test
    fun buildForPublicRecipient_checkServiceMessage() = runTest {
        val channel = createEndpointChannel(RecipientAddressType.PUBLIC)

        val message = MessageFactory.buildOutgoing(channel)

        val (serviceMessageDecrypted) =
            message.parcel.unwrapPayload(channel.thirdPartySessionKeyPair.privateKey)
        assertEquals(MessageFactory.serviceMessage.type, serviceMessageDecrypted.type)
        assertArrayEquals(MessageFactory.serviceMessage.content, serviceMessageDecrypted.content)
    }

    @Test
    internal fun buildForPublicRecipient_checkSenderCertificate() = runTest {
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
    internal fun buildForPublicRecipient_checkSenderCertificateChain() = runTest {
        val channel = createEndpointChannel(RecipientAddressType.PUBLIC)

        val message = MessageFactory.buildOutgoing(channel)

        assertTrue(message.parcel.senderCertificateChain.isEmpty())
    }

    // Private Recipient

    @Test
    fun buildForPrivateRecipient_checkBaseValues() = runTest {
        val channel = createEndpointChannel(RecipientAddressType.PRIVATE)
        val message = MessageFactory.buildOutgoing(channel)

        assertEquals(message.recipientEndpoint.nodeId, message.parcel.recipient.id)
        assertNull(message.parcel.recipient.internetAddress)
        assertEquals(message.parcelId.value, message.parcel.id)
        assertSameDateTime(message.parcelCreationDate, message.parcel.creationDate)
        assertEquals(message.ttl, message.parcel.ttl)
    }

    @Test
    internal fun buildForPrivateRecipient_checkSenderCertificate() = runTest {
        val channel = createEndpointChannel(RecipientAddressType.PRIVATE)

        val message = MessageFactory.buildOutgoing(channel)

        assertEquals(
            (message.recipientEndpoint as PrivateThirdPartyEndpoint).pda,
            message.parcel.senderCertificate
        )
    }

    @Test
    internal fun buildForPrivateRecipient_checkSenderCertificateChain() = runTest {
        val channel = createEndpointChannel(RecipientAddressType.PRIVATE)

        val message = MessageFactory.buildOutgoing(channel)

        assertArrayEquals(
            (message.recipientEndpoint as PrivateThirdPartyEndpoint).pdaChain.toTypedArray(),
            message.parcel.senderCertificateChain.toTypedArray()
        )
    }
}
