package tech.relaycorp.relaydroid.messaging

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tech.relaycorp.relaydroid.GatewayProtocolException
import tech.relaycorp.relaydroid.Relaynet
import tech.relaycorp.relaydroid.endpoint.AuthorizationBundle
import tech.relaycorp.relaydroid.endpoint.PrivateThirdPartyEndpointData
import tech.relaycorp.relaydroid.storage.mockStorage
import tech.relaycorp.relaynet.bindings.pdc.ClientBindingException
import tech.relaycorp.relaynet.bindings.pdc.NonceSignerException
import tech.relaycorp.relaynet.bindings.pdc.ParcelCollection
import tech.relaycorp.relaynet.bindings.pdc.ServerBindingException
import tech.relaycorp.relaynet.issueDeliveryAuthorization
import tech.relaycorp.relaynet.messages.Parcel
import tech.relaycorp.relaynet.messages.payloads.CargoMessageSet
import tech.relaycorp.relaynet.messages.payloads.ServiceMessage
import tech.relaycorp.relaynet.testing.pdc.CollectParcelsCall
import tech.relaycorp.relaynet.testing.pdc.MockPDCClient
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath
import tech.relaycorp.relaynet.wrappers.privateAddress

internal class ReceiveMessagesTest {

    private lateinit var pdcClient: MockPDCClient
    private val subject = ReceiveMessages { pdcClient }
    private val storage = mockStorage()

    private val serviceMessage = ServiceMessage("type", "content".toByteArray())

    @Before
    fun setUp() {
        runBlockingTest {
            Relaynet.storage = storage
            whenever(storage.identityCertificate.list()).thenReturn(listOf("1234"))
            whenever(storage.identityCertificate.get(any()))
                .thenReturn(PDACertPath.PRIVATE_ENDPOINT)
            whenever(storage.identityKeyPair.get(any())).thenReturn(KeyPairSet.PRIVATE_ENDPOINT)
            whenever(storage.gatewayCertificate.get()).thenReturn(PDACertPath.PRIVATE_GW)
            whenever(Relaynet.storage.privateThirdParty.get(any())).thenReturn(
                PrivateThirdPartyEndpointData(
                    PDACertPath.PRIVATE_ENDPOINT,
                    AuthorizationBundle(PDACertPath.PRIVATE_ENDPOINT.serialize(), emptyList())
                )
            )
        }
    }

    @Test
    fun receiveParcelSuccessfully() = runBlockingTest {
        val parcel = buildParcel()
        val parcelCollection = parcel.toParcelCollection()
        val collectParcelsCall = CollectParcelsCall(Result.success(flowOf(parcelCollection)))
        pdcClient = MockPDCClient(collectParcelsCall)

        val messages = subject.receive().toCollection(mutableListOf())

        assertTrue(pdcClient.wasClosed)
        assertTrue(collectParcelsCall.wasCalled)
        assertEquals(1, messages.size)
    }

    @Test
    fun collectParcelsWithCorrectNonceSigners() = runBlockingTest {
        val parcel = buildParcel()
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
    fun collectParcelsGetsServerError() = runBlockingTest {
        val collectParcelsCall = CollectParcelsCall(Result.failure(ServerBindingException("")))
        pdcClient = MockPDCClient(collectParcelsCall)

        subject.receive().collect()
    }

    @Test(expected = GatewayProtocolException::class)
    fun collectParcelsGetsClientError() = runBlockingTest {
        val collectParcelsCall = CollectParcelsCall(Result.failure(ClientBindingException("")))
        pdcClient = MockPDCClient(collectParcelsCall)

        subject.receive().collect()
    }

    @Test(expected = GatewayProtocolException::class)
    fun collectParcelsGetsSigningError() = runBlockingTest {
        val collectParcelsCall = CollectParcelsCall(Result.failure(NonceSignerException("")))
        pdcClient = MockPDCClient(collectParcelsCall)

        subject.receive().collect()
    }

    @Test
    fun receiveInvalidParcel_ackedButNotDeliveredToApp() = runBlockingTest {
        val invalidParcel = Parcel(
            recipientAddress = UUID.randomUUID().toString(),
            payload = "1234".toByteArray(),
            senderCertificate = PDACertPath.PRIVATE_ENDPOINT,
            creationDate = ZonedDateTime.now().plusDays(1)
        )
        var ackWasCalled = false
        val parcelCollection = ParcelCollection(
            parcelSerialized = invalidParcel.serialize(KeyPairSet.PRIVATE_ENDPOINT.private),
            trustedCertificates = emptyList(),
            ack = { ackWasCalled = true }
        )
        val collectParcelsCall = CollectParcelsCall(Result.success(flowOf(parcelCollection)))
        pdcClient = MockPDCClient(collectParcelsCall)

        val messages = subject.receive().toCollection(mutableListOf())

        assertTrue(pdcClient.wasClosed)
        assertTrue(collectParcelsCall.wasCalled)
        assertTrue(messages.isEmpty())
        assertTrue(ackWasCalled)
    }

    @Test
    fun receiveMalformedParcel_ackedButNotDeliveredToApp() = runBlockingTest {
        var ackWasCalled = false
        val parcelCollection = ParcelCollection(
            parcelSerialized = "1234".toByteArray(),
            trustedCertificates = emptyList(),
            ack = { ackWasCalled = true }
        )
        val collectParcelsCall = CollectParcelsCall(Result.success(flowOf(parcelCollection)))
        pdcClient = MockPDCClient(collectParcelsCall)

        val messages = subject.receive().toCollection(mutableListOf())

        assertTrue(pdcClient.wasClosed)
        assertTrue(collectParcelsCall.wasCalled)
        assertTrue(messages.isEmpty())
        assertTrue(ackWasCalled)
    }

    @Test
    fun receiveValidParcel_invalidPayloadEncryption() = runBlockingTest {
        val parcelPayload = serviceMessage.encrypt(
            PDACertPath.PUBLIC_GW // Invalid encryption key
        )
        val parcel = Parcel(
            recipientAddress = PDACertPath.PRIVATE_ENDPOINT.subjectPrivateAddress,
            payload = parcelPayload,
            senderCertificate = PDACertPath.PDA,
            senderCertificateChain = setOf(PDACertPath.PRIVATE_ENDPOINT, PDACertPath.PRIVATE_GW)
        )
        var ackWasCalled = false
        val parcelCollection = parcel.toParcelCollection { ackWasCalled = true }
        val collectParcelsCall = CollectParcelsCall(Result.success(flowOf(parcelCollection)))
        pdcClient = MockPDCClient(collectParcelsCall)

        val messages = subject.receive().toCollection(mutableListOf())

        assertTrue(pdcClient.wasClosed)
        assertTrue(messages.isEmpty())
        assertTrue(ackWasCalled)
    }

    @Test
    fun receiveValidParcel_invalidServiceMessage() = runBlockingTest {
        val invalidServiceMessage = CargoMessageSet(emptyArray())
        val parcel = Parcel(
            recipientAddress = PDACertPath.PRIVATE_ENDPOINT.subjectPrivateAddress,
            payload = invalidServiceMessage.encrypt(PDACertPath.PRIVATE_ENDPOINT),
            senderCertificate = PDACertPath.PDA,
            senderCertificateChain = setOf(PDACertPath.PRIVATE_ENDPOINT, PDACertPath.PRIVATE_GW)
        )
        var ackWasCalled = false
        val parcelCollection = parcel.toParcelCollection { ackWasCalled = true }
        val collectParcelsCall = CollectParcelsCall(Result.success(flowOf(parcelCollection)))
        pdcClient = MockPDCClient(collectParcelsCall)

        val messages = subject.receive().toCollection(mutableListOf())

        assertTrue(pdcClient.wasClosed)
        assertTrue(messages.isEmpty())
        assertTrue(ackWasCalled)
    }

    private fun buildParcel() = Parcel(
        recipientAddress = KeyPairSet.PRIVATE_ENDPOINT.public.privateAddress,
        payload = serviceMessage.encrypt(PDACertPath.PRIVATE_ENDPOINT),
        senderCertificate = issueDeliveryAuthorization(
            subjectPublicKey = KeyPairSet.PDA_GRANTEE.public,
            issuerPrivateKey = KeyPairSet.PRIVATE_ENDPOINT.private,
            issuerCertificate = PDACertPath.PRIVATE_ENDPOINT,
            validityStartDate = ZonedDateTime.now().minusDays(1),
            validityEndDate = ZonedDateTime.now().plusDays(1)
        ),
        senderCertificateChain = setOf(PDACertPath.PRIVATE_ENDPOINT, PDACertPath.PRIVATE_GW)
    )

    private fun Parcel.toParcelCollection(ack: suspend () -> Unit = {}) = ParcelCollection(
        parcelSerialized = serialize(KeyPairSet.PDA_GRANTEE.private),
        trustedCertificates = listOf(PDACertPath.PRIVATE_ENDPOINT, PDACertPath.PRIVATE_GW),
        ack = ack
    )
}
