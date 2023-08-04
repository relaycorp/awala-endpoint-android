package tech.relaycorp.awaladroid.messaging

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.test.runTest
import nl.altindag.log.LogCaptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.relaycorp.awaladroid.GatewayProtocolException
import tech.relaycorp.awaladroid.endpoint.PublicThirdPartyEndpointData
import tech.relaycorp.awaladroid.test.EndpointChannel
import tech.relaycorp.awaladroid.test.MockContextTestCase
import tech.relaycorp.awaladroid.test.RecipientAddressType
import tech.relaycorp.relaynet.bindings.pdc.ClientBindingException
import tech.relaycorp.relaynet.bindings.pdc.NonceSignerException
import tech.relaycorp.relaynet.bindings.pdc.ParcelCollection
import tech.relaycorp.relaynet.bindings.pdc.ServerBindingException
import tech.relaycorp.relaynet.issueDeliveryAuthorization
import tech.relaycorp.relaynet.messages.Parcel
import tech.relaycorp.relaynet.messages.Recipient
import tech.relaycorp.relaynet.messages.payloads.CargoMessageSet
import tech.relaycorp.relaynet.messages.payloads.ServiceMessage
import tech.relaycorp.relaynet.testing.pdc.CollectParcelsCall
import tech.relaycorp.relaynet.testing.pdc.MockPDCClient
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath
import tech.relaycorp.relaynet.wrappers.generateECDHKeyPair
import tech.relaycorp.relaynet.wrappers.nodeId
import java.time.ZonedDateTime

internal class ReceiveMessagesTest : MockContextTestCase() {

    private lateinit var pdcClient: MockPDCClient
    private val subject = ReceiveMessages { pdcClient }

    private val serviceMessage = ServiceMessage("type", "content".toByteArray())
    private val logCaptor = LogCaptor.forClass(ParcelCollection::class.java)

    @Test
    fun receiveParcelSuccessfully() = runTest {
        val channel = createEndpointChannel(RecipientAddressType.PUBLIC)
        val parcel = buildParcel(channel)
        val parcelCollection = parcel.toParcelCollection()
        val collectParcelsCall = CollectParcelsCall(Result.success(flowOf(parcelCollection)))
        pdcClient = MockPDCClient(collectParcelsCall)

        val messages = subject.receive().toCollection(mutableListOf())

        assertTrue(pdcClient.wasClosed)
        assertTrue(collectParcelsCall.wasCalled)
        assertEquals(1, messages.size)
    }

    @Test
    fun collectParcelsWithCorrectNonceSigners() = runTest {
        val channel = createEndpointChannel(RecipientAddressType.PUBLIC)
        val parcel = buildParcel(channel)
        val parcelCollection = parcel.toParcelCollection()
        val collectParcelsCall = CollectParcelsCall(Result.success(flowOf(parcelCollection)))
        pdcClient = MockPDCClient(collectParcelsCall)

        subject.receive().collect()

        assertTrue(pdcClient.wasClosed)
        assertTrue(collectParcelsCall.wasCalled)
        val nonceSigners = collectParcelsCall.arguments!!.nonceSigners
        assertEquals(1, nonceSigners.size)
        assertEquals(PDACertPath.PRIVATE_ENDPOINT, nonceSigners.first().certificate)
    }

    @Test(expected = ReceiveMessageException::class)
    fun collectParcelsGetsServerError() = runTest {
        val collectParcelsCall = CollectParcelsCall(Result.failure(ServerBindingException("")))
        pdcClient = MockPDCClient(collectParcelsCall)

        subject.receive().collect()
    }

    @Test(expected = GatewayProtocolException::class)
    fun collectParcelsGetsClientError() = runTest {
        val collectParcelsCall = CollectParcelsCall(Result.failure(ClientBindingException("")))
        pdcClient = MockPDCClient(collectParcelsCall)

        subject.receive().collect()
    }

    @Test(expected = GatewayProtocolException::class)
    fun collectParcelsGetsSigningError() = runTest {
        val collectParcelsCall = CollectParcelsCall(Result.failure(NonceSignerException("")))
        pdcClient = MockPDCClient(collectParcelsCall)

        subject.receive().collect()
    }

    @Test
    fun receiveInvalidParcel_ackedButNotDeliveredToApp() = runTest {
        val invalidParcel = Parcel(
            recipient = Recipient(KeyPairSet.PRIVATE_ENDPOINT.public.nodeId),
            payload = "".toByteArray(),
            senderCertificate = PDACertPath.PRIVATE_ENDPOINT,
        )
        var ackWasCalled = false
        val parcelCollection = ParcelCollection(
            parcelSerialized = invalidParcel.serialize(KeyPairSet.PRIVATE_ENDPOINT.private),
            trustedCertificates = emptyList(), // sender won't be trusted
            ack = { ackWasCalled = true },
        )
        val collectParcelsCall = CollectParcelsCall(Result.success(flowOf(parcelCollection)))
        pdcClient = MockPDCClient(collectParcelsCall)

        val messages = subject.receive().toCollection(mutableListOf())

        assertTrue(pdcClient.wasClosed)
        assertTrue(collectParcelsCall.wasCalled)
        assertTrue(messages.isEmpty())
        assertTrue(ackWasCalled)
        assertTrue(logCaptor.warnLogs.contains("Invalid incoming parcel"))
    }

    @Test
    fun receiveMalformedParcel_ackedButNotDeliveredToApp() = runTest {
        var ackWasCalled = false
        val parcelCollection = ParcelCollection(
            parcelSerialized = "1234".toByteArray(),
            trustedCertificates = emptyList(),
            ack = { ackWasCalled = true },
        )
        val collectParcelsCall = CollectParcelsCall(Result.success(flowOf(parcelCollection)))
        pdcClient = MockPDCClient(collectParcelsCall)

        val messages = subject.receive().toCollection(mutableListOf())

        assertTrue(pdcClient.wasClosed)
        assertTrue(collectParcelsCall.wasCalled)
        assertTrue(messages.isEmpty())
        assertTrue(ackWasCalled)
        assertTrue(logCaptor.warnLogs.contains("Malformed incoming parcel"))
    }

    @Test
    fun receiveParcelWithUnknownRecipient_ackedButNotDeliveredToApp() = runTest {
        val channel = createEndpointChannel(RecipientAddressType.PUBLIC)
        val parcel = buildParcel(channel)
        var ackWasCalled = false
        val parcelCollection = parcel.toParcelCollection { ackWasCalled = true }
        val collectParcelsCall = CollectParcelsCall(Result.success(flowOf(parcelCollection)))
        pdcClient = MockPDCClient(collectParcelsCall)

        channel.firstPartyEndpoint.delete()

        val messages = subject.receive().toCollection(mutableListOf())

        assertTrue(pdcClient.wasClosed)
        assertTrue(collectParcelsCall.wasCalled)
        assertTrue(messages.isEmpty())
        assertTrue(ackWasCalled)
        assertTrue(logCaptor.warnLogs.contains("Incoming parcel with invalid recipient"))
    }

    @Test
    fun receiveParcelWithUnknownSender_ackedButNotDeliveredToApp() = runTest {
        val channel = createEndpointChannel(RecipientAddressType.PUBLIC)
        val parcel = buildParcel(channel)
        var ackWasCalled = false
        val parcelCollection = parcel.toParcelCollection { ackWasCalled = true }
        val collectParcelsCall = CollectParcelsCall(Result.success(flowOf(parcelCollection)))
        pdcClient = MockPDCClient(collectParcelsCall)

        channel.thirdPartyEndpoint.delete()

        val messages = subject.receive().toCollection(mutableListOf())

        assertTrue(pdcClient.wasClosed)
        assertTrue(collectParcelsCall.wasCalled)
        assertTrue(messages.isEmpty())
        assertTrue(ackWasCalled)
        assertTrue(logCaptor.warnLogs.contains("Incoming parcel issues with invalid sender"))
    }

    @Test
    fun receiveValidParcel_invalidPayloadEncryption() = runTest {
        val channel = createEndpointChannel(RecipientAddressType.PUBLIC)
        storage.publicThirdParty.set(
            channel.thirdPartyEndpoint.nodeId,
            PublicThirdPartyEndpointData(
                channel.thirdPartyEndpoint.nodeId,
                channel.thirdPartyEndpoint.identityKey,
            ),
        )
        val parcelPayload = serviceMessage.encrypt(
            channel.firstPartySessionKeyPair.sessionKey.copy(
                publicKey = generateECDHKeyPair().public, // Invalid encryption key
            ),
            channel.thirdPartySessionKeyPair,
        )
        val parcel = Parcel(
            recipient = Recipient(PDACertPath.PRIVATE_ENDPOINT.subjectId),
            payload = parcelPayload,
            senderCertificate = PDACertPath.PDA,
            senderCertificateChain = setOf(PDACertPath.PRIVATE_ENDPOINT, PDACertPath.PRIVATE_GW),
        )
        var ackWasCalled = false
        val parcelCollection = parcel.toParcelCollection { ackWasCalled = true }
        val collectParcelsCall = CollectParcelsCall(Result.success(flowOf(parcelCollection)))
        pdcClient = MockPDCClient(collectParcelsCall)

        val messages = subject.receive().toCollection(mutableListOf())

        assertTrue(pdcClient.wasClosed)
        assertTrue(messages.isEmpty())
        assertTrue(ackWasCalled)
        assertTrue(
            logCaptor.warnLogs.contains(
                "Failed to decrypt parcel; sender might have used wrong key",
            ),
        )
    }

    @Test
    fun receiveValidParcel_invalidServiceMessage() = runTest {
        val invalidServiceMessage = CargoMessageSet(emptyArray())
        val channel = createEndpointChannel(RecipientAddressType.PUBLIC)
        storage.publicThirdParty.set(
            channel.thirdPartyEndpoint.nodeId,
            PublicThirdPartyEndpointData(
                channel.thirdPartyEndpoint.nodeId,
                channel.thirdPartyEndpoint.identityKey,
            ),
        )
        val parcel = Parcel(
            recipient = Recipient(PDACertPath.PRIVATE_ENDPOINT.subjectId),
            payload = invalidServiceMessage.encrypt(
                channel.firstPartySessionKeyPair.sessionKey,
                channel.thirdPartySessionKeyPair,
            ),
            senderCertificate = PDACertPath.PDA,
            senderCertificateChain = setOf(PDACertPath.PRIVATE_ENDPOINT, PDACertPath.PRIVATE_GW),
        )
        var ackWasCalled = false
        val parcelCollection = parcel.toParcelCollection { ackWasCalled = true }
        val collectParcelsCall = CollectParcelsCall(Result.success(flowOf(parcelCollection)))
        pdcClient = MockPDCClient(collectParcelsCall)

        val messages = subject.receive().toCollection(mutableListOf())

        assertTrue(pdcClient.wasClosed)
        assertTrue(messages.isEmpty())
        assertTrue(ackWasCalled)
        assertTrue(
            logCaptor.warnLogs.contains(
                "Incoming parcel did not encapsulate a valid service message",
            ),
        )
    }

    private fun buildParcel(channel: EndpointChannel) = Parcel(
        recipient = Recipient(KeyPairSet.PRIVATE_ENDPOINT.public.nodeId),
        payload = serviceMessage.encrypt(
            channel.firstPartySessionKeyPair.sessionKey,
            channel.thirdPartySessionKeyPair,
        ),
        senderCertificate = issueDeliveryAuthorization(
            subjectPublicKey = KeyPairSet.PDA_GRANTEE.public,
            issuerPrivateKey = KeyPairSet.PRIVATE_ENDPOINT.private,
            issuerCertificate = PDACertPath.PRIVATE_ENDPOINT,
            validityStartDate = ZonedDateTime.now().minusDays(1),
            validityEndDate = ZonedDateTime.now().plusDays(1),
        ),
        senderCertificateChain = setOf(PDACertPath.PRIVATE_ENDPOINT, PDACertPath.PRIVATE_GW),
    )

    private fun Parcel.toParcelCollection(ack: suspend () -> Unit = {}) = ParcelCollection(
        parcelSerialized = serialize(KeyPairSet.PDA_GRANTEE.private),
        trustedCertificates = listOf(PDACertPath.PRIVATE_ENDPOINT, PDACertPath.PRIVATE_GW),
        ack = ack,
    )
}
