package tech.relaycorp.awaladroid.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.relaycorp.awaladroid.GatewayProtocolException
import tech.relaycorp.awaladroid.RegistrationFailedException
import tech.relaycorp.awaladroid.test.FirstPartyEndpointFactory
import tech.relaycorp.awaladroid.test.MockContextTestCase
import tech.relaycorp.awaladroid.test.ThirdPartyEndpointFactory
import tech.relaycorp.awaladroid.test.assertSameDateTime
import tech.relaycorp.relaynet.messages.control.PrivateNodeRegistration
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath
import tech.relaycorp.relaynet.wrappers.privateAddress
import tech.relaycorp.relaynet.wrappers.x509.Certificate

internal class FirstPartyEndpointTest : MockContextTestCase() {
    @Test
    fun address() {
        val endpoint = FirstPartyEndpointFactory.build()
        assertEquals(endpoint.identityCertificate.subjectPrivateAddress, endpoint.address)
    }

    @Test
    fun publicKey() {
        val endpoint = FirstPartyEndpointFactory.build()
        assertEquals(endpoint.identityCertificate.subjectPublicKey, endpoint.publicKey)
    }

    @Test
    fun pdaChain() {
        val endpoint = FirstPartyEndpointFactory.build()

        assertTrue(endpoint.identityCertificate in endpoint.pdaChain)
        assertTrue(PDACertPath.PRIVATE_GW in endpoint.pdaChain)
    }

    @Test
    fun register(): Unit = runBlockingTest {
        whenever(gatewayClient.registerEndpoint(any())).thenReturn(
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
    fun register_failed(): Unit = runBlockingTest {
        whenever(gatewayClient.registerEndpoint(any())).thenThrow(RegistrationFailedException(""))

        FirstPartyEndpoint.register()

        verifyZeroInteractions(storage)
        assertEquals(0, privateKeyStore.identityKeys.size)
    }

    @Test(expected = GatewayProtocolException::class)
    fun register_failedDueToProtocol(): Unit = runBlockingTest {
        whenever(gatewayClient.registerEndpoint(any())).thenThrow(GatewayProtocolException(""))

        FirstPartyEndpoint.register()

        verifyZeroInteractions(storage)
        assertEquals(0, privateKeyStore.identityKeys.size)
    }

    @Test
    fun load_nonExistent(): Unit = runBlockingTest {
        assertNull(FirstPartyEndpoint.load("non-existent"))
    }

    @Test
    fun load_withResult(): Unit = runBlockingTest {
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
            "This is not a key".toByteArray(),
            expiryDate
        )
    }

    @Test
    fun delete(): Unit = runBlockingTest {
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
