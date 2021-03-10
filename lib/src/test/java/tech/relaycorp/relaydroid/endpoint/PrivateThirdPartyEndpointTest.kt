package tech.relaycorp.relaydroid.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import tech.relaycorp.relaydroid.Relaynet
import tech.relaycorp.relaydroid.storage.StorageImpl
import tech.relaycorp.relaydroid.storage.mockStorage
import tech.relaycorp.relaydroid.storage.persistence.PersistenceException
import tech.relaycorp.relaydroid.test.FirstPartyEndpointFactory
import tech.relaycorp.relaynet.issueDeliveryAuthorization
import tech.relaycorp.relaynet.issueEndpointCertificate
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath
import tech.relaycorp.relaynet.wrappers.generateRSAKeyPair

internal class PrivateThirdPartyEndpointTest {

    private lateinit var storage: StorageImpl

    @Before
    fun setUp() {
        storage = mockStorage().also { Relaynet.storage = it }
    }

    @Test
    fun load_successful() = runBlockingTest {
        whenever(storage.thirdPartyAuthorization.get(any()))
            .thenReturn(PDACertPath.PRIVATE_ENDPOINT)
        whenever(storage.thirdPartyIdentityCertificate.get(any()))
            .thenReturn(PDACertPath.PRIVATE_ENDPOINT)
        val firstAddress = UUID.randomUUID().toString()
        val thirdAddress = UUID.randomUUID().toString()

        with(PrivateThirdPartyEndpoint.load(firstAddress, thirdAddress)!!) {
            assertEquals(firstAddress, firstPartyAddress)
            assertEquals(PDACertPath.PRIVATE_ENDPOINT.subjectPrivateAddress, address)
            assertEquals(PDACertPath.PRIVATE_ENDPOINT, pda)
            assertEquals(PDACertPath.PRIVATE_ENDPOINT, identityCertificate)
        }

        verify(storage.thirdPartyAuthorization).get("${firstAddress}_$thirdAddress")
    }

    @Test
    fun load_nonExistent() = runBlockingTest {
        whenever(storage.thirdPartyAuthorization.get(any())).thenReturn(null)

        assertNull(
            PrivateThirdPartyEndpoint.load(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString()
            )
        )
    }

    @Test
    fun importAuthorization_successful() = runBlockingTest {
        val firstPartyEndpoint = FirstPartyEndpointFactory.build()
        val firstPartyAddress = firstPartyEndpoint.identityCertificate.subjectPrivateAddress
        whenever(storage.identityCertificate.get(any()))
            .thenReturn(firstPartyEndpoint.identityCertificate)

        val thirdPartyAddress = PDACertPath.PRIVATE_ENDPOINT.subjectPrivateAddress
        val authorization = issueDeliveryAuthorization(
            subjectPublicKey = firstPartyEndpoint.keyPair.public,
            issuerPrivateKey = KeyPairSet.PRIVATE_ENDPOINT.private,
            validityEndDate = ZonedDateTime.now().plusDays(1),
            issuerCertificate = PDACertPath.PRIVATE_ENDPOINT
        )

        val endpoint = PrivateThirdPartyEndpoint.importAuthorization(
            authorization, PDACertPath.PRIVATE_ENDPOINT
        )

        assertEquals(
            firstPartyAddress,
            endpoint.firstPartyAddress
        )
        assertEquals(
            thirdPartyAddress,
            endpoint.address
        )
        assertEquals(
            authorization,
            endpoint.pda
        )
        assertEquals(
            PDACertPath.PRIVATE_ENDPOINT,
            endpoint.identityCertificate
        )

        verify(storage.identityCertificate).get(firstPartyAddress)
        verify(storage.thirdPartyAuthorization).set(
            "${firstPartyAddress}_$thirdPartyAddress",
            authorization
        )
        verify(storage.thirdPartyIdentityCertificate).set(
            "${firstPartyAddress}_$thirdPartyAddress",
            PDACertPath.PRIVATE_ENDPOINT
        )
    }

    @Test(expected = UnknownFirstPartyEndpointException::class)
    fun importAuthorization_invalidFirstParty() = runBlockingTest {
        val firstPartyEndpoint = FirstPartyEndpointFactory.build()
        val authorization = issueDeliveryAuthorization(
            subjectPublicKey = firstPartyEndpoint.keyPair.public,
            issuerPrivateKey = KeyPairSet.PRIVATE_ENDPOINT.private,
            validityEndDate = ZonedDateTime.now().plusDays(1),
            issuerCertificate = PDACertPath.PRIVATE_ENDPOINT
        )

        PrivateThirdPartyEndpoint.importAuthorization(authorization, PDACertPath.PRIVATE_ENDPOINT)
    }

    @Test(expected = InvalidAuthorizationException::class)
    fun importAuthorization_invalidAuthorization() = runBlockingTest {
        val firstPartyEndpoint = FirstPartyEndpointFactory.build()
        whenever(storage.identityCertificate.get(any()))
            .thenReturn(firstPartyEndpoint.identityCertificate)

        val unrelatedKeyPair = generateRSAKeyPair()
        val unrelatedCertificate = issueEndpointCertificate(
            unrelatedKeyPair.public,
            unrelatedKeyPair.private,
            ZonedDateTime.now().plusDays(1)
        )

        val authorization = issueDeliveryAuthorization(
            subjectPublicKey = firstPartyEndpoint.keyPair.public,
            issuerPrivateKey = KeyPairSet.PRIVATE_ENDPOINT.private,
            validityEndDate = ZonedDateTime.now().plusDays(1),
            issuerCertificate = PDACertPath.PRIVATE_ENDPOINT
        )

        PrivateThirdPartyEndpoint.importAuthorization(authorization, unrelatedCertificate)
    }

    @Test(expected = PersistenceException::class)
    fun importAuthorization_persistenceException() = runBlockingTest {
        val firstPartyEndpoint = FirstPartyEndpointFactory.build()
        whenever(storage.identityCertificate.get(any())).thenThrow(PersistenceException(""))

        val authorization = issueDeliveryAuthorization(
            subjectPublicKey = firstPartyEndpoint.keyPair.public,
            issuerPrivateKey = KeyPairSet.PRIVATE_ENDPOINT.private,
            validityEndDate = ZonedDateTime.now().plusDays(1),
            issuerCertificate = PDACertPath.PRIVATE_ENDPOINT
        )

        PrivateThirdPartyEndpoint.importAuthorization(authorization, PDACertPath.PRIVATE_ENDPOINT)
    }
}
