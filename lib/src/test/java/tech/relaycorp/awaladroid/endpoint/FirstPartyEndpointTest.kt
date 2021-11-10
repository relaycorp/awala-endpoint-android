package tech.relaycorp.awaladroid.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tech.relaycorp.awaladroid.AwalaContext
import tech.relaycorp.awaladroid.GatewayClientImpl
import tech.relaycorp.awaladroid.GatewayProtocolException
import tech.relaycorp.awaladroid.RegistrationFailedException
import tech.relaycorp.awaladroid.storage.mockStorage
import tech.relaycorp.awaladroid.test.FirstPartyEndpointFactory
import tech.relaycorp.awaladroid.test.ThirdPartyEndpointFactory
import tech.relaycorp.awaladroid.test.assertSameDateTime
import tech.relaycorp.awaladroid.test.setAwalaContext
import tech.relaycorp.awaladroid.test.unsetAwalaContext
import tech.relaycorp.relaynet.messages.control.PrivateNodeRegistration
import tech.relaycorp.relaynet.nodes.EndpointManager
import tech.relaycorp.relaynet.testing.keystores.MockPrivateKeyStore
import tech.relaycorp.relaynet.testing.keystores.MockSessionPublicKeyStore
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath
import tech.relaycorp.relaynet.wrappers.privateAddress
import tech.relaycorp.relaynet.wrappers.x509.Certificate

public class FirstPartyEndpointTest {
    private val gateway = mock<GatewayClientImpl>()
    private val storage = mockStorage()
    private val privateKeyStore = MockPrivateKeyStore()
    private val sessionPublicKeystore = MockSessionPublicKeyStore()

    @Before
    public fun setMockContext() {
        setAwalaContext(
            AwalaContext(
                storage,
                gateway,
                EndpointManager(privateKeyStore, sessionPublicKeystore),
                privateKeyStore,
                sessionPublicKeystore,
            )
        )
    }

    @Before
    public fun resetKeystores() {
        privateKeyStore.clear()
        sessionPublicKeystore.clear()
    }

    @After
    public fun unsetContext(): Unit = unsetAwalaContext()

    @Test
    public fun address() {
        val endpoint = FirstPartyEndpointFactory.build()
        assertEquals(endpoint.identityCertificate.subjectPrivateAddress, endpoint.address)
    }

    @Test
    public fun publicKey() {
        val endpoint = FirstPartyEndpointFactory.build()
        assertEquals(endpoint.identityCertificate.subjectPublicKey, endpoint.publicKey)
    }

    @Test
    public fun pdaChain() {
        val endpoint = FirstPartyEndpointFactory.build()

        assertTrue(endpoint.identityCertificate in endpoint.pdaChain)
        assertTrue(PDACertPath.PRIVATE_GW in endpoint.pdaChain)
    }

    @Test
    public fun register(): Unit = runBlockingTest {
        whenever(gateway.registerEndpoint(any())).thenReturn(
            PrivateNodeRegistration(
                PDACertPath.PRIVATE_ENDPOINT,
                PDACertPath.PRIVATE_GW
            )
        )

        val endpoint = FirstPartyEndpoint.register()

        val identityKeyPair =
            privateKeyStore.retrieveIdentityKey(PDACertPath.PRIVATE_ENDPOINT.subjectPrivateAddress)
        assertEquals(PDACertPath.PRIVATE_ENDPOINT, identityKeyPair.certificate)
        assertEquals(endpoint.identityPrivateKey, identityKeyPair.privateKey)
    }

    @Test(expected = RegistrationFailedException::class)
    public fun register_failed(): Unit = runBlockingTest {
        whenever(gateway.registerEndpoint(any())).thenThrow(RegistrationFailedException(""))

        FirstPartyEndpoint.register()

        verifyZeroInteractions(storage)
        assertEquals(0, privateKeyStore.identityKeys.size)
    }

    @Test(expected = GatewayProtocolException::class)
    public fun register_failedDueToProtocol(): Unit = runBlockingTest {
        whenever(gateway.registerEndpoint(any())).thenThrow(GatewayProtocolException(""))

        FirstPartyEndpoint.register()

        verifyZeroInteractions(storage)
        assertEquals(0, privateKeyStore.identityKeys.size)
    }

    @Test
    public fun load_nonExistent(): Unit = runBlockingTest {
        assertNull(FirstPartyEndpoint.load("non-existent"))
    }

    @Test
    public fun load_withResult(): Unit = runBlockingTest {
        privateKeyStore.saveIdentityKey(
            KeyPairSet.PRIVATE_ENDPOINT.private,
            PDACertPath.PRIVATE_ENDPOINT
        )
        whenever(storage.gatewayCertificate.get())
            .thenReturn(PDACertPath.PRIVATE_GW)

        val privateAddress = KeyPairSet.PRIVATE_ENDPOINT.public.privateAddress
        with(FirstPartyEndpoint.load(privateAddress)) {
            assertNotNull(this)
            assertEquals(KeyPairSet.PRIVATE_ENDPOINT.private, this?.identityPrivateKey)
            assertEquals(PDACertPath.PRIVATE_ENDPOINT, this?.identityCertificate)
            assertEquals(PDACertPath.PRIVATE_GW, this?.gatewayCertificate)
        }
    }

    @Test
    public fun issueAuthorization_thirdPartyEndpoint() {
        val firstPartyEndpoint = FirstPartyEndpointFactory.build()
        val thirdPartyEndpoint = ThirdPartyEndpointFactory.buildPublic()
        val expiryDate = ZonedDateTime.now().plusDays(1)

        val authorization = firstPartyEndpoint.issueAuthorization(thirdPartyEndpoint, expiryDate)

        validateAuthorization(authorization, firstPartyEndpoint, expiryDate)
    }

    @Test
    public fun issueAuthorization_publicKey_valid() {
        val firstPartyEndpoint = FirstPartyEndpointFactory.build()
        val expiryDate = ZonedDateTime.now().plusDays(1)

        val authorization = firstPartyEndpoint.issueAuthorization(
            KeyPairSet.PDA_GRANTEE.public.encoded,
            expiryDate
        )

        validateAuthorization(authorization, firstPartyEndpoint, expiryDate)
    }

    @Test(expected = AuthorizationIssuanceException::class)
    public fun issueAuthorization_publicKey_invalid() {
        val firstPartyEndpoint = FirstPartyEndpointFactory.build()
        val expiryDate = ZonedDateTime.now().plusDays(1)

        firstPartyEndpoint.issueAuthorization(
            "This is not a public key".toByteArray(),
            expiryDate
        )
    }

    @Test
    public fun delete(): Unit = runBlockingTest {
        privateKeyStore.saveIdentityKey(
            KeyPairSet.PRIVATE_ENDPOINT.private,
            PDACertPath.PRIVATE_ENDPOINT
        )
        val endpoint = FirstPartyEndpoint(
            KeyPairSet.PRIVATE_ENDPOINT.private,
            PDACertPath.PRIVATE_ENDPOINT,
            PDACertPath.PRIVATE_GW,
        )

        endpoint.delete()

        assertEquals(0, privateKeyStore.identityKeys.size)
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
