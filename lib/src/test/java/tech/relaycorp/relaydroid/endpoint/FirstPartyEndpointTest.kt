package tech.relaycorp.relaydroid.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import java.security.KeyPair
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tech.relaycorp.relaydroid.GatewayClientImpl
import tech.relaycorp.relaydroid.GatewayProtocolException
import tech.relaycorp.relaydroid.RegistrationFailedException
import tech.relaycorp.relaydroid.Relaynet
import tech.relaycorp.relaydroid.storage.mockStorage
import tech.relaycorp.relaydroid.test.FirstPartyEndpointFactory
import tech.relaycorp.relaydroid.test.ThirdPartyEndpointFactory
import tech.relaycorp.relaydroid.test.assertSameDateTime
import tech.relaycorp.relaynet.messages.control.PrivateNodeRegistration
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath
import tech.relaycorp.relaynet.wrappers.privateAddress
import tech.relaycorp.relaynet.wrappers.x509.Certificate

internal class FirstPartyEndpointTest {

    private val gateway = mock<GatewayClientImpl>()
    private val storage = mockStorage()

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
    fun publicKey() {
        val endpoint = FirstPartyEndpointFactory.build()
        assertEquals(endpoint.keyPair.public, endpoint.publicKey)
    }

    @Test
    fun pdaChain() {
        val endpoint = FirstPartyEndpointFactory.build()

        assertTrue(endpoint.identityCertificate in endpoint.pdaChain)
        assertTrue(PDACertPath.PRIVATE_GW in endpoint.pdaChain)
    }

    @Test
    fun register() = runBlockingTest {
        whenever(gateway.registerEndpoint(any())).thenReturn(PrivateNodeRegistration(
            PDACertPath.PRIVATE_ENDPOINT,
            PDACertPath.PRIVATE_GW
        ))

        val endpoint = FirstPartyEndpoint.register()

        val keyPairCaptor = argumentCaptor<KeyPair>()
        verify(gateway)
            .registerEndpoint(keyPairCaptor.capture())
        verify(storage.identityKeyPair)
            .set(eq(endpoint.address), eq(keyPairCaptor.firstValue))
        verify(storage.identityCertificate)
            .set(eq(endpoint.address), eq(PDACertPath.PRIVATE_ENDPOINT))
        verify(storage.gatewayCertificate)
            .set(eq(PDACertPath.PRIVATE_GW))
    }

    @Test(expected = RegistrationFailedException::class)
    fun register_failed() = runBlockingTest {
        whenever(gateway.registerEndpoint(any())).thenThrow(RegistrationFailedException(""))

        FirstPartyEndpoint.register()

        verifyZeroInteractions(storage)
    }

    @Test(expected = GatewayProtocolException::class)
    fun register_failedDueToProtocol() = runBlockingTest {
        whenever(gateway.registerEndpoint(any())).thenThrow(GatewayProtocolException(""))

        FirstPartyEndpoint.register()

        verifyZeroInteractions(storage)
    }

    @Test
    fun load_nonExistent() = runBlockingTest {
        assertNull(FirstPartyEndpoint.load("non-existent"))
    }

    @Test
    fun load_withResult() = runBlockingTest {
        val address = UUID.randomUUID().toString()

        whenever(storage.identityKeyPair.get(eq(address)))
            .thenReturn(KeyPairSet.PRIVATE_ENDPOINT)
        whenever(storage.identityCertificate.get(eq(address)))
            .thenReturn(PDACertPath.PRIVATE_ENDPOINT)
        whenever(storage.gatewayCertificate.get())
            .thenReturn(PDACertPath.PRIVATE_GW)

        with(FirstPartyEndpoint.load(address)) {
            assertNotNull(this)
            assertEquals(KeyPairSet.PRIVATE_ENDPOINT, this?.keyPair)
            assertEquals(PDACertPath.PRIVATE_ENDPOINT, this?.identityCertificate)
            assertEquals(PDACertPath.PRIVATE_GW, this?.gatewayCertificate)
        }
    }

    @Test
    fun issueAuthorization_thirdPartyEndpoint() {
        val firstPartyEndpoint = FirstPartyEndpointFactory.build()
        val thirdPartyEndpoint = ThirdPartyEndpointFactory.buildPublic()
        val expiryDate = ZonedDateTime.now().plusDays(1)

        val authorization = firstPartyEndpoint.issueAuthorization(thirdPartyEndpoint, expiryDate)

        validateAuthorization(authorization, firstPartyEndpoint, expiryDate)
    }

    @Test
    fun issueAuthorization_publicKey_valid() {
        val firstPartyEndpoint = FirstPartyEndpointFactory.build()
        val expiryDate = ZonedDateTime.now().plusDays(1)

        val authorization = firstPartyEndpoint.issueAuthorization(
            KeyPairSet.PDA_GRANTEE.public.encoded,
            expiryDate
        )

        validateAuthorization(authorization, firstPartyEndpoint, expiryDate)
    }

    @Test(expected = AuthorizationIssuanceException::class)
    fun issueAuthorization_publicKey_invalid() {
        val firstPartyEndpoint = FirstPartyEndpointFactory.build()
        val expiryDate = ZonedDateTime.now().plusDays(1)

        firstPartyEndpoint.issueAuthorization(
            "This is not a public key".toByteArray(),
            expiryDate
        )
    }
}

private fun validateAuthorization(
    authorization: AuthorizationBundle,
    firstPartyEndpoint: FirstPartyEndpoint,
    expiryDate: ZonedDateTime
) {
    // PDA
    val pda = Certificate.deserialize(authorization.pdaSerialized)
    assertEquals(
        KeyPairSet.PDA_GRANTEE.public.encoded.asList(),
        pda.subjectPublicKey.encoded.asList()
    )
    assertEquals(
        2,
        pda.getCertificationPath(emptyList(), listOf(PDACertPath.PRIVATE_ENDPOINT)).size
    )
    assertSameDateTime(
        expiryDate,
        pda.expiryDate
    )

    // PDA chain
    assertEquals(
        firstPartyEndpoint.pdaChain.map { it.serialize().asList() },
        authorization.pdaChainSerialized.map { it.asList() }
    )
}
