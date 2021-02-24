package tech.relaycorp.relaydroid.messaging

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tech.relaycorp.relaydroid.Relaynet
import tech.relaycorp.relaydroid.StorageImpl
import tech.relaycorp.relaynet.bindings.pdc.ClientBindingException
import tech.relaycorp.relaynet.bindings.pdc.NonceSignerException
import tech.relaycorp.relaynet.bindings.pdc.ParcelCollection
import tech.relaycorp.relaynet.bindings.pdc.ServerBindingException
import tech.relaycorp.relaynet.issueDeliveryAuthorization
import tech.relaycorp.relaynet.messages.Parcel
import tech.relaycorp.relaynet.testing.pdc.CollectParcelsCall
import tech.relaycorp.relaynet.testing.pdc.MockPDCClient
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath
import tech.relaycorp.relaynet.wrappers.privateAddress
import java.time.ZonedDateTime
import java.util.UUID

internal class ReceiveMessagesTest {

    private lateinit var pdcClient: MockPDCClient
    private val subject = ReceiveMessages { pdcClient }
    private val storage = mock<StorageImpl>()

    @Before
    fun setUp() {
        runBlockingTest {
            Relaynet.storage = storage
            whenever(storage.listEndpoints()).thenReturn(listOf("1234"))
            whenever(storage.getIdentityCertificate(any())).thenReturn(PDACertPath.PRIVATE_ENDPOINT)
            whenever(storage.getIdentityKeyPair(any())).thenReturn(KeyPairSet.PRIVATE_ENDPOINT)
            whenever(storage.getGatewayCertificate()).thenReturn(PDACertPath.PRIVATE_GW)
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
        assertEquals(parcel.id, messages.first().id.value)
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

    @Test(expected = ReceiveMessagesException::class)
    fun collectParcelsGetsServerError() = runBlockingTest {
        val collectParcelsCall = CollectParcelsCall(Result.failure(ServerBindingException("")))
        pdcClient = MockPDCClient(collectParcelsCall)

        subject.receive().collect()
    }

    @Test(expected = ReceiveMessagesException::class)
    fun collectParcelsGetsClientError() = runBlockingTest {
        val collectParcelsCall = CollectParcelsCall(Result.failure(ClientBindingException("")))
        pdcClient = MockPDCClient(collectParcelsCall)

        subject.receive().collect()
    }

    @Test(expected = ReceiveMessagesException::class)
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

    private fun buildParcel() = Parcel(
        recipientAddress = KeyPairSet.PRIVATE_ENDPOINT.public.privateAddress,
        payload = "1234".toByteArray(),
        senderCertificate = issueDeliveryAuthorization(
            subjectPublicKey = KeyPairSet.PRIVATE_ENDPOINT.public,
            issuerPrivateKey = KeyPairSet.PRIVATE_ENDPOINT.private,
            issuerCertificate = PDACertPath.PRIVATE_ENDPOINT,
            validityStartDate = ZonedDateTime.now().minusDays(1),
            validityEndDate = ZonedDateTime.now().plusDays(1)
        ),
        senderCertificateChain = setOf(PDACertPath.PRIVATE_ENDPOINT, PDACertPath.PRIVATE_GW)
    )

    private fun Parcel.toParcelCollection() = ParcelCollection(
        parcelSerialized = serialize(KeyPairSet.PRIVATE_ENDPOINT.private),
        trustedCertificates = listOf(PDACertPath.PRIVATE_ENDPOINT, PDACertPath.PRIVATE_GW),
        ack = {}
    )
}
