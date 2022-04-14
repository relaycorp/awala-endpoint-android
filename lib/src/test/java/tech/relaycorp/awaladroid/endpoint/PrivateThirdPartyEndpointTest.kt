package tech.relaycorp.awaladroid.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.relaycorp.awaladroid.test.MockContextTestCase
import tech.relaycorp.relaynet.SessionKeyPair
import tech.relaycorp.relaynet.issueDeliveryAuthorization
import tech.relaycorp.relaynet.issueEndpointCertificate
import tech.relaycorp.relaynet.pki.CertificationPath
import tech.relaycorp.relaynet.pki.CertificationPathException
import tech.relaycorp.relaynet.ramf.RecipientAddressType
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath
import tech.relaycorp.relaynet.wrappers.generateRSAKeyPair
import tech.relaycorp.relaynet.wrappers.privateAddress
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

    @Test
    fun load_successful() = runBlockingTest {
        whenever(storage.privateThirdParty.get(any())).thenReturn(
            PrivateThirdPartyEndpointData(
                KeyPairSet.PRIVATE_ENDPOINT.public,
                CertificationPath(
                    PDACertPath.PDA,
                    listOf(PDACertPath.PRIVATE_ENDPOINT, PDACertPath.PRIVATE_GW)
                )
            )
        )
        val firstAddress = UUID.randomUUID().toString()
        val thirdAddress = UUID.randomUUID().toString()

        with(PrivateThirdPartyEndpoint.load(thirdAddress, firstAddress)!!) {
            assertEquals(firstAddress, firstPartyEndpointAddress)
            assertEquals(PDACertPath.PRIVATE_ENDPOINT.subjectPrivateAddress, address)
            assertEquals(PDACertPath.PDA, pda)
            assertEquals(listOf(PDACertPath.PRIVATE_ENDPOINT, PDACertPath.PRIVATE_GW), pdaChain)
        }

        verify(storage.privateThirdParty).get("${firstAddress}_$thirdAddress")
    }

    @Test
    fun load_nonExistent() = runBlockingTest {
        whenever(storage.privateThirdParty.get(any())).thenReturn(null)

        assertNull(
            PrivateThirdPartyEndpoint.load(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString()
            )
        )
    }

    @Test
    fun import_successful() = runBlockingTest {
        val firstPartyEndpoint = createFirstPartyEndpoint()

        val pdaPath = CertificationPath(
            pda,
            listOf(thirdPartyEndpointCertificate)
        )
        val endpoint = PrivateThirdPartyEndpoint.import(
            KeyPairSet.PDA_GRANTEE.public.encoded,
            pdaPath.serialize(),
            sessionKey,
        )

        assertEquals(
            firstPartyEndpoint.privateAddress,
            endpoint.firstPartyEndpointAddress
        )
        assertEquals(
            KeyPairSet.PDA_GRANTEE.public.privateAddress,
            endpoint.address
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
            eq("${firstPartyEndpoint.privateAddress}_${endpoint.privateAddress}"),
            argThat {
                identityKey == KeyPairSet.PDA_GRANTEE.public &&
                    this.pdaPath.leafCertificate == pda &&
                    this.pdaPath.certificateAuthorities == pdaPath.certificateAuthorities
            }
        )

        assertEquals(sessionKey, sessionPublicKeystore.retrieve(endpoint.privateAddress))
    }

    @Test
    fun import_invalidIdentityKey() = runBlockingTest {
        val pdaPath = CertificationPath(thirdPartyEndpointCertificate, emptyList())

        val exception = assertThrows(InvalidThirdPartyEndpoint::class.java) {
            runBlockingTest {
                PrivateThirdPartyEndpoint.import(
                    "123456".toByteArray(),
                    pdaPath.serialize(),
                    sessionKey,
                )
            }
        }

        assertEquals("Identity key is not a well-formed RSA public key", exception.message)
    }

    @Test
    fun import_invalidFirstParty() = runBlockingTest {
        val firstPartyCert = PDACertPath.PRIVATE_ENDPOINT
        val pdaPath = CertificationPath(firstPartyCert, emptyList())

        val exception = assertThrows(UnknownFirstPartyEndpointException::class.java) {
            runBlockingTest {
                PrivateThirdPartyEndpoint.import(
                    KeyPairSet.PDA_GRANTEE.public.encoded,
                    pdaPath.serialize(),
                    sessionKey,
                )
            }
        }

        assertEquals(
            "First-party endpoint ${firstPartyCert.subjectPrivateAddress} is not registered",
            exception.message
        )
    }

    @Test
    fun import_wrongAuthorizationIssuer() = runBlockingTest {
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
        val exception = assertThrows(InvalidAuthorizationException::class.java) {
            runBlockingTest {
                PrivateThirdPartyEndpoint.import(
                    KeyPairSet.PDA_GRANTEE.public.encoded,
                    pdaPath.serialize(),
                    sessionKey,
                )
            }
        }

        assertEquals("PDA path is invalid", exception.message)
        assertTrue(exception.cause is CertificationPathException)
        assertTrue(exception.cause?.cause is CertificateException)
    }

    @Test
    fun import_malformedAuthorization() = runBlockingTest {
        val exception = assertThrows(InvalidAuthorizationException::class.java) {
            runBlockingTest {
                PrivateThirdPartyEndpoint.import(
                    KeyPairSet.PDA_GRANTEE.public.encoded,
                    "malformed".toByteArray(),
                    sessionKey,
                )
            }
        }

        assertEquals("PDA path is malformed", exception.message)
        assertTrue(exception.cause is CertificationPathException)
    }

    @Test
    fun import_invalidPDAPath() = runBlockingTest {
        createFirstPartyEndpoint()
        val pdaPath = CertificationPath(
            pda,
            emptyList(), // Shouldn't be empty
        )

        val exception = assertThrows(InvalidAuthorizationException::class.java) {
            runBlockingTest {
                PrivateThirdPartyEndpoint.import(
                    KeyPairSet.PDA_GRANTEE.public.encoded,
                    pdaPath.serialize(),
                    sessionKey,
                )
            }
        }

        assertEquals("PDA path is invalid", exception.message)
    }

    @Test
    fun import_expiredPDA() = runBlockingTest {
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
        val exception = assertThrows(InvalidAuthorizationException::class.java) {
            runBlockingTest {
                PrivateThirdPartyEndpoint.import(
                    KeyPairSet.PDA_GRANTEE.public.encoded,
                    pdaPath.serialize(),
                    sessionKey,
                )
            }
        }

        assertEquals("PDA path is invalid", exception.message)
        assertTrue(exception.cause is CertificationPathException)
    }

    @Test
    fun dataSerialization() {
        val pda = PDACertPath.PDA
        val identityKey = KeyPairSet.PRIVATE_ENDPOINT.public
        val pdaPath = CertificationPath(
            pda,
            listOf(PDACertPath.PRIVATE_GW, PDACertPath.PUBLIC_GW)
        )
        val dataSerialized = PrivateThirdPartyEndpointData(
            identityKey,
            pdaPath
        ).serialize()
        val data = PrivateThirdPartyEndpointData.deserialize(dataSerialized)

        assertEquals(identityKey, data.identityKey)
        assertEquals(pda, data.pdaPath.leafCertificate)
        assertEquals(
            listOf(PDACertPath.PRIVATE_GW, PDACertPath.PUBLIC_GW),
            data.pdaPath.certificateAuthorities
        )
    }

    @Test
    fun delete() = runBlockingTest {
        val channel = createEndpointChannel(RecipientAddressType.PRIVATE)
        val endpoint = channel.thirdPartyEndpoint as PrivateThirdPartyEndpoint
        val firstPartyEndpoint = channel.firstPartyEndpoint

        endpoint.delete()

        verify(storage.privateThirdParty)
            .delete("${firstPartyEndpoint.privateAddress}_${endpoint.privateAddress}")
        assertEquals(0, privateKeyStore.sessionKeys[firstPartyEndpoint.privateAddress]!!.size)
        assertEquals(0, sessionPublicKeystore.keys.size)
        verify(channelManager).delete(endpoint)
    }
}
