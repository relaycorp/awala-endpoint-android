package tech.relaycorp.relaydroid.messaging

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.util.UUID
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import tech.relaycorp.relaydroid.Relaynet
import tech.relaycorp.relaydroid.endpoint.AuthorizationBundle
import tech.relaycorp.relaydroid.endpoint.PrivateThirdPartyEndpointData
import tech.relaycorp.relaydroid.storage.mockStorage
import tech.relaycorp.relaynet.messages.Parcel
import tech.relaycorp.relaynet.messages.payloads.ServiceMessage
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath

internal class IncomingMessageTest {

    @Before
    fun setUp() {
        runBlockingTest {
            Relaynet.storage = mockStorage()
            whenever(Relaynet.storage.identityCertificate.get(any()))
                .thenReturn(PDACertPath.PRIVATE_ENDPOINT)
            whenever(Relaynet.storage.identityKeyPair.get(any()))
                .thenReturn(KeyPairSet.PRIVATE_ENDPOINT)
            whenever(Relaynet.storage.gatewayCertificate.get()).thenReturn(PDACertPath.PRIVATE_GW)
            whenever(Relaynet.storage.privateThirdParty.get(any())).thenReturn(
                PrivateThirdPartyEndpointData(
                    PDACertPath.PRIVATE_ENDPOINT,
                    AuthorizationBundle(PDACertPath.PRIVATE_ENDPOINT.serialize(), emptyList())
                )
            )
        }
    }

    @Test
    fun buildFromParcel() = runBlockingTest {
        val serviceMessage = ServiceMessage("the type", "the content".toByteArray())
        val parcel = Parcel(
            recipientAddress = UUID.randomUUID().toString(),
            payload = serviceMessage.encrypt(PDACertPath.PRIVATE_ENDPOINT),
            senderCertificate = PDACertPath.PDA
        )

        val message = IncomingMessage.build(parcel) {}

        verify(Relaynet.storage.identityCertificate).get(eq(parcel.recipientAddress))

        assertEquals(PDACertPath.PRIVATE_ENDPOINT, message.recipientEndpoint.identityCertificate)
        assertEquals(serviceMessage.type, message.type)
        assertArrayEquals(serviceMessage.content, message.content)
    }
}
