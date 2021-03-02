package tech.relaycorp.relaydroid

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.test.runBlockingTest
import org.bouncycastle.asn1.x500.style.BCStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import tech.relaycorp.relaydroid.storage.StorageImpl
import tech.relaycorp.relaydroid.storage.mockStorage
import tech.relaycorp.relaydroid.storage.persistence.PersistenceException
import tech.relaycorp.relaydroid.test.FirstPartyEndpointFactory
import tech.relaycorp.relaydroid.test.assertSameDateTime
import tech.relaycorp.relaynet.issueDeliveryAuthorization
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath
import java.time.ZonedDateTime
import java.util.UUID

internal class ThirdPartyEndpointTest {

    private lateinit var storage: StorageImpl

    @Before
    fun setUp() {
        storage = mockStorage().also { Relaynet.storage = it }
    }

    // Private

    @Test
    internal fun loadPrivate_successful() = runBlockingTest {
        whenever(storage.privateThirdPartyAuthorization.get(any()))
            .thenReturn(PDACertPath.PRIVATE_ENDPOINT)
        val firstAddress = UUID.randomUUID().toString()
        val thirdAddress = UUID.randomUUID().toString()

        with(ThirdPartyEndpoint.loadPrivate(firstAddress, thirdAddress)!!) {
            assertEquals(firstAddress, firstPartyAddress)
            assertEquals(thirdAddress, thirdPartyAddress)
            assertEquals(PDACertPath.PRIVATE_ENDPOINT, authorization)
        }

        verify(storage.privateThirdPartyAuthorization).get("${firstAddress}_$thirdAddress")
    }

    @Test
    internal fun loadPrivate_nonExistent() = runBlockingTest {
        whenever(storage.privateThirdPartyAuthorization.get(any())).thenReturn(null)

        assertNull(
            ThirdPartyEndpoint.loadPrivate(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString()
            )
        )
    }

    @Test
    fun importPrivateAuthorization_successful() = runBlockingTest {
        val firstPartyEndpoint = FirstPartyEndpointFactory.build()
        val firstPartyAddress = firstPartyEndpoint.certificate.subjectPrivateAddress
        whenever(storage.identityCertificate.get(any())).thenReturn(firstPartyEndpoint.certificate)

        val thirdPartyAddress = PDACertPath.PRIVATE_ENDPOINT.subjectPrivateAddress
        val authorization = issueDeliveryAuthorization(
            subjectPublicKey = firstPartyEndpoint.keyPair.public,
            issuerPrivateKey = KeyPairSet.PRIVATE_ENDPOINT.private,
            validityEndDate = ZonedDateTime.now().plusDays(1),
            issuerCertificate = PDACertPath.PRIVATE_ENDPOINT
        )

        val endpoint = ThirdPartyEndpoint.importPrivateAuthorization(authorization)
        assertEquals(
            firstPartyAddress,
            endpoint.firstPartyAddress
        )
        assertEquals(
            thirdPartyAddress,
            endpoint.thirdPartyAddress
        )
        assertEquals(
            authorization,
            endpoint.authorization
        )

        verify(storage.identityCertificate).get(firstPartyAddress)
        verify(storage.privateThirdPartyAuthorization).set(
            "${firstPartyAddress}_$thirdPartyAddress",
            authorization
        )
    }

    @Test(expected = InvalidFirstPartyEndpointAddressException::class)
    fun importPrivateAuthorization_invalidFirstParty() = runBlockingTest {
        val firstPartyEndpoint = FirstPartyEndpointFactory.build()
        val authorization = issueDeliveryAuthorization(
            subjectPublicKey = firstPartyEndpoint.keyPair.public,
            issuerPrivateKey = KeyPairSet.PRIVATE_ENDPOINT.private,
            validityEndDate = ZonedDateTime.now().plusDays(1),
            issuerCertificate = PDACertPath.PRIVATE_ENDPOINT
        )

        ThirdPartyEndpoint.importPrivateAuthorization(authorization)
    }

    @Test(expected = PersistenceException::class)
    fun importPrivateAuthorization_persistenceException() = runBlockingTest {
        val firstPartyEndpoint = FirstPartyEndpointFactory.build()
        whenever(storage.identityCertificate.get(any())).thenThrow(PersistenceException(""))

        val authorization = issueDeliveryAuthorization(
            subjectPublicKey = firstPartyEndpoint.keyPair.public,
            issuerPrivateKey = KeyPairSet.PRIVATE_ENDPOINT.private,
            validityEndDate = ZonedDateTime.now().plusDays(1),
            issuerCertificate = PDACertPath.PRIVATE_ENDPOINT
        )

        ThirdPartyEndpoint.importPrivateAuthorization(authorization)
    }

    @Test
    fun issueAuthorization() {
        val firstPartyEndpoint = FirstPartyEndpointFactory.build()
        val expiryDate = ZonedDateTime.now().plusDays(1)

        val authorization = ThirdPartyEndpoint.issueAuthorization(
            firstPartyEndpoint,
            KeyPairSet.PRIVATE_ENDPOINT.public,
            expiryDate
        )

        assertEquals(
            firstPartyEndpoint.certificate.subjectPrivateAddress,
            authorization.subjectPrivateAddress
        )
        assertEquals(
            firstPartyEndpoint.certificate.subjectPrivateAddress,
            authorization.certificateHolder.subject.getRDNs(BCStyle.CN)
                .first().first.value.toString()
        )
        assertSameDateTime(
            expiryDate,
            authorization.expiryDate
        )
    }

    // Public

    @Test
    internal fun loadPublic_successful() = runBlockingTest {
        whenever(storage.publicThirdPartyCertificate.get(any()))
            .thenReturn(PDACertPath.PUBLIC_GW)
        val address = "example.org"

        with(ThirdPartyEndpoint.loadPublic(address)!!) {
            assertEquals(address, thirdPartyAddress)
            assertEquals(PDACertPath.PUBLIC_GW, certificate)
        }
    }

    @Test
    internal fun loadPublic_nonExistent() = runBlockingTest {
        whenever(storage.publicThirdPartyCertificate.get(any())).thenReturn(null)

        assertNull(ThirdPartyEndpoint.loadPublic("example.org"))
    }

    @Test
    fun importPublicEndpointCertificate() = runBlockingTest {
        val address = "example.org"

        with(ThirdPartyEndpoint.importPublicEndpointCertificate(address, PDACertPath.PUBLIC_GW)) {
            assertEquals(address, thirdPartyAddress)
            assertEquals(PDACertPath.PUBLIC_GW, certificate)
        }

        verify(storage.publicThirdPartyCertificate).set(address, PDACertPath.PUBLIC_GW)
    }
}
