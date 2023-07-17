package tech.relaycorp.awaladroid.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.relaycorp.awaladroid.test.MockContextTestCase
import tech.relaycorp.awaladroid.test.RecipientAddressType
import tech.relaycorp.relaynet.InvalidNodeConnectionParams
import tech.relaycorp.relaynet.PrivateEndpointConnParams
import tech.relaycorp.relaynet.SessionKeyPair
import tech.relaycorp.relaynet.issueDeliveryAuthorization
import tech.relaycorp.relaynet.issueEndpointCertificate
import tech.relaycorp.relaynet.pki.CertificationPath
import tech.relaycorp.relaynet.pki.CertificationPathException
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath
import tech.relaycorp.relaynet.wrappers.generateRSAKeyPair
import tech.relaycorp.relaynet.wrappers.nodeId
import tech.relaycorp.relaynet.wrappers.x509.CertificateException

internal class PrivateThirdPartyEndpointTest : MockContextTestCase() {
    private val thirdPartyEndpointCertificate = issueEndpointCertificate(
        KeyPairSet.PDA_GRANTEE.public,
        KeyPairSet.PRIVATE_GW.private,
        ZonedDateTime.now().plusDays(1),
        PDACertPath.PRIVATE_GW,
    )
    private val pda = issueDeliveryAuthorization(
        subjectPublicKey = KeyPairSet.PRIVATE_ENDPOINT.public,
        issuerPrivateKey = KeyPairSet.PDA_GRANTEE.private,
        validityEndDate = ZonedDateTime.now().plusDays(1),
        issuerCertificate = thirdPartyEndpointCertificate,
    )

    private val sessionKey = SessionKeyPair.generate().sessionKey

    private val internetGatewayAddress = "example.com"

    @Test
    fun recipient() {
        val endpoint = PrivateThirdPartyEndpoint(
            "the id",
            KeyPairSet.PDA_GRANTEE.public,
            pda,
            listOf(PDACertPath.PRIVATE_ENDPOINT, PDACertPath.PRIVATE_GW),
            internetGatewayAddress,
        )

        val recipient = endpoint.recipient
        assertEquals(endpoint.nodeId, recipient.id)
        assertEquals(internetGatewayAddress, recipient.internetAddress)
    }

    @Test
    fun load_successful() = runTest {
        whenever(storage.privateThirdParty.get(any())).thenReturn(
            PrivateThirdPartyEndpointData(
                KeyPairSet.PRIVATE_ENDPOINT.public,
                CertificationPath(
                    PDACertPath.PDA,
                    listOf(PDACertPath.PRIVATE_ENDPOINT, PDACertPath.PRIVATE_GW)
                ),
                internetGatewayAddress,
            )
        )
        val firstAddress = UUID.randomUUID().toString()
        val thirdAddress = UUID.randomUUID().toString()

        with(PrivateThirdPartyEndpoint.load(thirdAddress, firstAddress)!!) {
            assertEquals(firstAddress, firstPartyEndpointAddress)
            assertEquals(PDACertPath.PRIVATE_ENDPOINT.subjectId, nodeId)
            assertEquals(PDACertPath.PDA, pda)
            assertEquals(listOf(PDACertPath.PRIVATE_ENDPOINT, PDACertPath.PRIVATE_GW), pdaChain)
            assertEquals(internetGatewayAddress, internetAddress)
        }

        verify(storage.privateThirdParty).get("${firstAddress}_$thirdAddress")
    }

    @Test
    fun load_nonExistent() = runTest {
        whenever(storage.privateThirdParty.get(any())).thenReturn(null)

        assertNull(
            PrivateThirdPartyEndpoint.load(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString()
            )
        )
    }

    @Test
    fun import_successful() = runTest {
        val firstPartyEndpoint = createFirstPartyEndpoint()

        val deliveryAuth = CertificationPath(
            pda,
            listOf(thirdPartyEndpointCertificate)
        )
        val paramsSerialized = serializeConnectionParams(deliveryAuth)
        val endpoint = PrivateThirdPartyEndpoint.import(paramsSerialized)

        assertEquals(
            firstPartyEndpoint.nodeId,
            endpoint.firstPartyEndpointAddress
        )
        assertEquals(
            KeyPairSet.PDA_GRANTEE.public.nodeId,
            endpoint.nodeId
        )
        assertEquals(
            KeyPairSet.PDA_GRANTEE.public,
            endpoint.identityKey
        )
        assertEquals(pda, endpoint.pda)
        assertArrayEquals(
            arrayOf(thirdPartyEndpointCertificate),
            endpoint.pdaChain.toTypedArray()
        )

        verify(storage.privateThirdParty).set(
            eq("${firstPartyEndpoint.nodeId}_${endpoint.nodeId}"),
            argThat {
                identityKey == KeyPairSet.PDA_GRANTEE.public &&
                    this.pdaPath.leafCertificate == pda &&
                    this.pdaPath.certificateAuthorities == deliveryAuth.certificateAuthorities &&
                    this.internetGatewayAddress == internetGatewayAddress
            }
        )

        assertEquals(sessionKey, sessionPublicKeystore.retrieve(endpoint.nodeId))
    }

    @Test
    fun import_invalidFirstParty() = runTest {
        val firstPartyCert = PDACertPath.PRIVATE_ENDPOINT
        val pdaPath = CertificationPath(firstPartyCert, emptyList())
        val paramsSerialized = serializeConnectionParams(pdaPath)
        try {
            PrivateThirdPartyEndpoint.import(paramsSerialized)
        } catch (exception: UnknownFirstPartyEndpointException) {
            assertEquals(
                "First-party endpoint ${firstPartyCert.subjectId} is not registered",
                exception.message
            )
            return@runTest
        }

        assert(false)
    }

    @Test
    fun import_wrongAuthorizationIssuer() = runTest {
        val firstPartyEndpoint = createFirstPartyEndpoint()

        val unrelatedKeyPair = generateRSAKeyPair()
        val unrelatedCertificate = issueEndpointCertificate(
            unrelatedKeyPair.public,
            unrelatedKeyPair.private,
            ZonedDateTime.now().plusDays(1)
        )

        val invalidPDA = issueDeliveryAuthorization(
            subjectPublicKey = firstPartyEndpoint.identityCertificate.subjectPublicKey,
            issuerPrivateKey = unrelatedKeyPair.private,
            validityEndDate = ZonedDateTime.now().plusDays(1),
            issuerCertificate = unrelatedCertificate
        )

        val pdaPath = CertificationPath(
            invalidPDA,
            listOf(thirdPartyEndpointCertificate)
        )
        val paramsSerialized = serializeConnectionParams(pdaPath)
        try {
            PrivateThirdPartyEndpoint.import(paramsSerialized)
        } catch (exception: InvalidAuthorizationException) {
            assertEquals("PDA path is invalid", exception.message)
            assertTrue(exception.cause is CertificationPathException)
            assertTrue(exception.cause?.cause is CertificateException)
            return@runTest
        }

        assert(false)
    }

    @Test
    fun import_malformedParams() = runTest {
        try {
            PrivateThirdPartyEndpoint.import("malformed".toByteArray())
        } catch (exception: InvalidThirdPartyEndpoint) {
            assertEquals("Malformed connection params", exception.message)
            assertTrue(exception.cause is InvalidNodeConnectionParams)
            return@runTest
        }

        assert(false)
    }

    @Test
    fun import_invalidPDAPath() = runTest {
        createFirstPartyEndpoint()
        val pdaPath = CertificationPath(
            pda,
            emptyList(), // Shouldn't be empty
        )
        val paramsSerialized = serializeConnectionParams(pdaPath)
        try {
            PrivateThirdPartyEndpoint.import(paramsSerialized)
        } catch (exception: InvalidAuthorizationException) {
            assertEquals("PDA path is invalid", exception.message)
            return@runTest
        }

        assert(false)
    }

    @Test
    fun import_expiredPDA() = runTest {
        val firstPartyEndpoint = createFirstPartyEndpoint()

        val now = ZonedDateTime.now()
        val expiredPDA = issueDeliveryAuthorization(
            firstPartyEndpoint.identityCertificate.subjectPublicKey,
            KeyPairSet.PDA_GRANTEE.private,
            now.minusSeconds(1),
            thirdPartyEndpointCertificate,
            now.minusSeconds(2)
        )

        val pdaPath = CertificationPath(expiredPDA, listOf(thirdPartyEndpointCertificate))
        val paramsSerialized = serializeConnectionParams(pdaPath)
        try {
            PrivateThirdPartyEndpoint.import(paramsSerialized)
        } catch (exception: InvalidAuthorizationException) {
            assertEquals("PDA path is invalid", exception.message)
            assertTrue(exception.cause is CertificationPathException)
            return@runTest
        }

        assert(false)
    }

    @Test
    fun dataSerialization() {
        val pda = PDACertPath.PDA
        val identityKey = KeyPairSet.PRIVATE_ENDPOINT.public
        val pdaPath = CertificationPath(
            pda,
            listOf(PDACertPath.PRIVATE_GW, PDACertPath.INTERNET_GW)
        )
        val dataSerialized = PrivateThirdPartyEndpointData(
            identityKey,
            pdaPath,
            internetGatewayAddress,
        ).serialize()
        val data = PrivateThirdPartyEndpointData.deserialize(dataSerialized)

        assertEquals(identityKey, data.identityKey)
        assertEquals(pda, data.pdaPath.leafCertificate)
        assertEquals(
            listOf(PDACertPath.PRIVATE_GW, PDACertPath.INTERNET_GW),
            data.pdaPath.certificateAuthorities
        )
        assertEquals(internetGatewayAddress, data.internetGatewayAddress)
    }

    @Test
    fun updateConnectionParams_invalidPath() = runTest {
        val channel = createEndpointChannel(RecipientAddressType.PRIVATE)
        val thirdPartyEndpoint = channel.thirdPartyEndpoint as PrivateThirdPartyEndpoint
        val deliveryAuth = CertificationPath(pda, listOf())
        val params = makeConnectionParams(thirdPartyEndpoint, deliveryAuth)

        try {
            thirdPartyEndpoint.updateParams(params)
        } catch (exception: InvalidAuthorizationException) {
            assertEquals("PDA path is invalid", exception.message)
            assertTrue(exception.cause is CertificationPathException)
            return@runTest
        }

        assert(false)
    }

    @Test
    fun updateConnectionParams_differentFirstPartyEndpoint() = runTest {
        val channel = createEndpointChannel(RecipientAddressType.PRIVATE)
        val thirdPartyEndpoint = channel.thirdPartyEndpoint as PrivateThirdPartyEndpoint
        val invalidSubjectPublicKey = KeyPairSet.INTERNET_GW.public
        val invalidPDA = issueDeliveryAuthorization(
            invalidSubjectPublicKey,
            KeyPairSet.PDA_GRANTEE.private,
            thirdPartyEndpointCertificate.expiryDate,
            thirdPartyEndpointCertificate,
        )
        val deliveryAuth = CertificationPath(invalidPDA, listOf(thirdPartyEndpointCertificate))
        val params = makeConnectionParams(thirdPartyEndpoint, deliveryAuth)

        try {
            thirdPartyEndpoint.updateParams(params)
        } catch (exception: InvalidAuthorizationException) {
            assertEquals(
                "PDA subject (${invalidSubjectPublicKey.nodeId}) " +
                    "is not first-party endpoint",
                exception.message,
            )
            return@runTest
        }

        assert(false)
    }

    @Test
    fun updateConnectionParams_differentThirdPartyEndpoint() = runTest {
        val channel = createEndpointChannel(RecipientAddressType.PRIVATE)
        val thirdPartyEndpoint = channel.thirdPartyEndpoint as PrivateThirdPartyEndpoint
        val invalidIssuer = PDACertPath.INTERNET_GW
        val invalidPDA = issueDeliveryAuthorization(
            channel.firstPartyEndpoint.publicKey,
            KeyPairSet.INTERNET_GW.private, // Invalid issuer
            invalidIssuer.expiryDate,
            invalidIssuer,
        )
        val deliveryAuth = CertificationPath(invalidPDA, listOf(invalidIssuer))
        val params = makeConnectionParams(thirdPartyEndpoint, deliveryAuth)

        try {
            thirdPartyEndpoint.updateParams(params)
        } catch (exception: InvalidAuthorizationException) {
            assertEquals(
                "PDA issuer (${invalidIssuer.subjectId}) is not third-party endpoint",
                exception.message,
            )
            return@runTest
        }

        assert(false)
    }

    @Test
    fun updateConnectionParams_valid() = runTest {
        val channel = createEndpointChannel(RecipientAddressType.PRIVATE)
        val thirdPartyEndpoint = channel.thirdPartyEndpoint as PrivateThirdPartyEndpoint
        val deliveryAuth = CertificationPath(pda, listOf(thirdPartyEndpointCertificate))
        val params = makeConnectionParams(thirdPartyEndpoint, deliveryAuth)

        thirdPartyEndpoint.updateParams(params)

        verify(storage.privateThirdParty).set(
            "${channel.firstPartyEndpoint.nodeId}_${thirdPartyEndpoint.nodeId}",
            PrivateThirdPartyEndpointData(
                KeyPairSet.PDA_GRANTEE.public,
                deliveryAuth,
                thirdPartyEndpoint.internetAddress,
            )
        )
    }

    @Test
    fun delete() = runTest {
        val channel = createEndpointChannel(RecipientAddressType.PRIVATE)
        val endpoint = channel.thirdPartyEndpoint as PrivateThirdPartyEndpoint
        val firstPartyEndpoint = channel.firstPartyEndpoint

        endpoint.delete()

        verify(storage.privateThirdParty)
            .delete("${firstPartyEndpoint.nodeId}_${endpoint.nodeId}")
        assertEquals(0, privateKeyStore.sessionKeys[firstPartyEndpoint.nodeId]!!.size)
        assertEquals(0, sessionPublicKeystore.keys.size)
        verify(channelManager).delete(endpoint)
    }

    private fun serializeConnectionParams(deliveryAuth: CertificationPath) =
        PrivateEndpointConnParams(
            KeyPairSet.PDA_GRANTEE.public,
            internetGatewayAddress,
            deliveryAuth,
            sessionKey,
        ).serialize()

    private fun makeConnectionParams(
        thirdPartyEndpoint: PrivateThirdPartyEndpoint,
        deliveryAuth: CertificationPath
    ) = PrivateEndpointConnParams(
        thirdPartyEndpoint.identityKey,
        thirdPartyEndpoint.internetAddress,
        deliveryAuth,
        sessionKey,
    )
}
