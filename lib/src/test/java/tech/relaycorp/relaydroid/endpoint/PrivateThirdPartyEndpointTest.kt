package tech.relaycorp.relaydroid.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
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
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import java.time.ZonedDateTime
import java.util.UUID

internal class PrivateThirdPartyEndpointTest {

    private lateinit var storage: StorageImpl

    @Rule
    @JvmField
    val expectedException: ExpectedException = ExpectedException.none()

    @Before
    fun setUp() {
        storage = mockStorage().also { Relaynet.storage = it }
    }

    @Test
    fun load_successful() = runBlockingTest {
        whenever(storage.privateThirdParty.get(any())).thenReturn(
            PrivateThirdPartyEndpointData(
                PDACertPath.PRIVATE_ENDPOINT,
                AuthorizationBundle(
                    PDACertPath.PRIVATE_ENDPOINT.serialize(),
                    listOf(PDACertPath.PRIVATE_GW.serialize())
                )
            )
        )
        val firstAddress = UUID.randomUUID().toString()
        val thirdAddress = UUID.randomUUID().toString()

        with(PrivateThirdPartyEndpoint.load(thirdAddress, firstAddress)!!) {
            assertEquals(firstAddress, firstPartyEndpointAddress)
            assertEquals(PDACertPath.PRIVATE_ENDPOINT.subjectPrivateAddress, address)
            assertEquals(PDACertPath.PRIVATE_ENDPOINT, pda)
            assertEquals(listOf(PDACertPath.PRIVATE_GW), pdaChain)
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

        val authBundle = AuthorizationBundle(
            authorization.serialize(),
            listOf(PDACertPath.PRIVATE_ENDPOINT.serialize())
        )
        val endpoint = PrivateThirdPartyEndpoint.import(PDACertPath.PRIVATE_ENDPOINT, authBundle)

        assertEquals(
            firstPartyAddress,
            endpoint.firstPartyEndpointAddress
        )
        assertEquals(
            thirdPartyAddress,
            endpoint.address
        )
        assertEquals(
            PDACertPath.PRIVATE_ENDPOINT,
            endpoint.identityCertificate
        )
        assertEquals(
            authorization,
            endpoint.pda
        )
        assertArrayEquals(
            arrayOf(PDACertPath.PRIVATE_ENDPOINT),
            endpoint.pdaChain.toTypedArray()
        )

        verify(storage.identityCertificate).get(firstPartyAddress)
        verify(storage.privateThirdParty).set(
            "${firstPartyAddress}_$thirdPartyAddress",
            PrivateThirdPartyEndpointData(
                PDACertPath.PRIVATE_ENDPOINT,
                authBundle
            )
        )
    }

    @Test(expected = UnknownFirstPartyEndpointException::class)
    fun import_invalidFirstParty() = runBlockingTest {
        val firstPartyEndpoint = FirstPartyEndpointFactory.build()
        val authorization = issueDeliveryAuthorization(
            subjectPublicKey = firstPartyEndpoint.keyPair.public,
            issuerPrivateKey = KeyPairSet.PRIVATE_ENDPOINT.private,
            validityEndDate = ZonedDateTime.now().plusDays(1),
            issuerCertificate = PDACertPath.PRIVATE_ENDPOINT
        )

        PrivateThirdPartyEndpoint.import(
            PDACertPath.PRIVATE_ENDPOINT,
            AuthorizationBundle(authorization.serialize(), emptyList())
        )
    }

    @Test
    fun import_wrongAuthorizationIssuer() = runBlockingTest {
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

        expectedException.expect(InvalidAuthorizationException::class.java)
        expectedException.expectMessage("PDA was not issued by third-party endpoint")
        PrivateThirdPartyEndpoint.import(
            PDACertPath.PRIVATE_ENDPOINT,
            AuthorizationBundle(
                authorization.serialize(),
                listOf(unrelatedCertificate.serialize())
            )
        )
    }

    @Test
    fun import_invalidAuthorization() = runBlockingTest {
        val firstPartyEndpoint = FirstPartyEndpointFactory.build()
        whenever(storage.identityCertificate.get(any()))
            .thenReturn(firstPartyEndpoint.identityCertificate)

        val authorization = issueDeliveryAuthorization(
            firstPartyEndpoint.keyPair.public,
            KeyPairSet.PRIVATE_ENDPOINT.private,
            validityEndDate = ZonedDateTime.now().minusDays(1),
            issuerCertificate = PDACertPath.PRIVATE_ENDPOINT,
            validityStartDate = ZonedDateTime.now().minusDays(2)
        )

        expectedException.expect(InvalidAuthorizationException::class.java)
        expectedException.expectMessage("PDA is invalid")
        PrivateThirdPartyEndpoint.import(
            PDACertPath.PRIVATE_ENDPOINT,
            AuthorizationBundle(authorization.serialize(), emptyList())
        )
    }

    @Test(expected = PersistenceException::class)
    fun import_persistenceException() = runBlockingTest {
        val firstPartyEndpoint = FirstPartyEndpointFactory.build()
        whenever(storage.identityCertificate.get(any())).thenThrow(PersistenceException(""))

        val authorization = issueDeliveryAuthorization(
            subjectPublicKey = firstPartyEndpoint.keyPair.public,
            issuerPrivateKey = KeyPairSet.PRIVATE_ENDPOINT.private,
            validityEndDate = ZonedDateTime.now().plusDays(1),
            issuerCertificate = PDACertPath.PRIVATE_ENDPOINT
        )

        PrivateThirdPartyEndpoint.import(
            PDACertPath.PRIVATE_ENDPOINT,
            AuthorizationBundle(authorization.serialize(), emptyList())
        )
    }

    @Test
    fun dataSerialization() {
        val pda = PDACertPath.PDA
        val dataSerialized = PrivateThirdPartyEndpointData(
            PDACertPath.PRIVATE_ENDPOINT,
            AuthorizationBundle(
                pda.serialize(),
                listOf(PDACertPath.PRIVATE_GW.serialize(), PDACertPath.PUBLIC_GW.serialize())
            )
        ).serialize()
        val data = PrivateThirdPartyEndpointData.deserialize(dataSerialized)

        assertEquals(PDACertPath.PRIVATE_ENDPOINT, data.identityCertificate)
        assertEquals(pda, Certificate.deserialize(data.authBundle.pdaSerialized))
        assertArrayEquals(
            arrayOf(PDACertPath.PRIVATE_GW, PDACertPath.PUBLIC_GW),
            data.authBundle.pdaChainSerialized.map { Certificate.deserialize(it) }.toTypedArray()
        )
    }
}
