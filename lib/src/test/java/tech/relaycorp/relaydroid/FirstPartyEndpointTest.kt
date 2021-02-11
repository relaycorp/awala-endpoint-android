package tech.relaycorp.relaydroid

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import tech.relaycorp.relaydroid.test.FirstPartyEndpointFactory
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath
import tech.relaycorp.relaynet.wrappers.privateAddress
import java.security.KeyPair
import java.util.UUID

internal class FirstPartyEndpointTest {

    private val gateway = mock<GatewayClientI>()
    private val storage = mock<StorageImpl>()

    @Before
    fun setUp() {
        runBlockingTest {
            Relaynet.storage = storage
            Relaynet.gatewayClientImpl = gateway
        }
    }

    @Test
    fun address() {
        val endpoint = FirstPartyEndpointFactory.build()
        assertEquals(endpoint.keyPair.public.privateAddress, endpoint.address)
    }

    @Test
    fun register() = runBlockingTest {
        whenever(gateway.registerEndpoint(any())).thenReturn(Pair(
            PDACertPath.PRIVATE_ENDPOINT,
            PDACertPath.PRIVATE_GW
        ))

        val endpoint = FirstPartyEndpoint.register()

        val keyPairCaptor = argumentCaptor<KeyPair>()
        verify(gateway)
            .registerEndpoint(keyPairCaptor.capture())
        verify(storage)
            .setIdentityKeyPair(eq(endpoint.address), eq(keyPairCaptor.firstValue))
        verify(storage)
            .setIdentityCertificate(eq(endpoint.address), eq(PDACertPath.PRIVATE_ENDPOINT))
        verify(storage)
            .setGatewayCertificate(eq(PDACertPath.PRIVATE_GW))
    }

    @Test
    fun load_nonExistent() = runBlockingTest {
        assertNull(FirstPartyEndpoint.load("non-existent"))
    }

    @Test
    fun load_withResult() = runBlockingTest {
        val address = UUID.randomUUID().toString()

        whenever(storage.getIdentityKeyPair(eq(address)))
            .thenReturn(KeyPairSet.PRIVATE_ENDPOINT)
        whenever(storage.getIdentityCertificate(eq(address)))
            .thenReturn(PDACertPath.PRIVATE_ENDPOINT)
        whenever(storage.getGatewayCertificate())
            .thenReturn(PDACertPath.PRIVATE_GW)

        with(FirstPartyEndpoint.load(address)) {
            assertNotNull(this)
            assertEquals(KeyPairSet.PRIVATE_ENDPOINT, this?.keyPair)
            assertEquals(PDACertPath.PRIVATE_ENDPOINT, this?.certificate)
            assertEquals(PDACertPath.PRIVATE_GW, this?.gatewayCertificate)
        }
    }
}
