package tech.relaycorp.relaydroid

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tech.relaycorp.relaydroid.test.FirstPartyEndpointFactory
import tech.relaycorp.relaydroid.test.ParcelDeliveryCertificates
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.wrappers.privateAddress
import java.security.KeyPair

class FirstPartyEndpointTest {

    @Test
    fun address() {
        val endpoint = FirstPartyEndpointFactory.build()
        assertEquals(endpoint.keyPair.public.privateAddress, endpoint.address)
    }

    @Test
    fun register() = runBlockingTest {
        val gateway = mock<GatewayClientI>()
        val storage = mock<StorageImpl>()
        Relaynet.storage = storage
        Relaynet.gatewayClientImpl = gateway

        whenever(gateway.registerEndpoint(any())).thenReturn(Pair(
            ParcelDeliveryCertificates.PRIVATE_ENDPOINT,
            ParcelDeliveryCertificates.PRIVATE_GW
        ))

        val endpoint = FirstPartyEndpoint.register()

        val keyPairCaptor = argumentCaptor<KeyPair>()
        verify(gateway).registerEndpoint(keyPairCaptor.capture())
        verify(storage).setIdentityKeyPair(eq(endpoint.address), eq(keyPairCaptor.firstValue))
        verify(storage).setIdentityCertificate(eq(endpoint.address), eq(ParcelDeliveryCertificates.PRIVATE_ENDPOINT))
        verify(storage).setGatewayCertificate(eq(ParcelDeliveryCertificates.PRIVATE_GW))
    }

    @Test
    fun load() = runBlockingTest {
        val gateway = mock<GatewayClientI>()
        val storage = mock<StorageImpl>()
        Relaynet.storage = storage
        Relaynet.gatewayClientImpl = gateway

        val address = "123456"

        assertNull(FirstPartyEndpoint.load(address))

        whenever(storage.getIdentityKeyPair(address)).thenReturn(KeyPairSet.PRIVATE_ENDPOINT)
        whenever(storage.getIdentityCertificate(address)).thenReturn(ParcelDeliveryCertificates.PRIVATE_ENDPOINT)
        whenever(storage.getGatewayCertificate()).thenReturn(ParcelDeliveryCertificates.PRIVATE_GW)

        val endpoint = FirstPartyEndpoint.load(address)!!
        assertEquals(KeyPairSet.PRIVATE_ENDPOINT, endpoint.keyPair)
        assertEquals(ParcelDeliveryCertificates.PRIVATE_ENDPOINT, endpoint.certificate)
        assertEquals(ParcelDeliveryCertificates.PRIVATE_GW, endpoint.gatewayCertificate)
    }
}
