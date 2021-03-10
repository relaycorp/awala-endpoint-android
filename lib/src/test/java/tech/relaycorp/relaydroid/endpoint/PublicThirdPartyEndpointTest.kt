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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import tech.relaycorp.relaydroid.Relaynet
import tech.relaycorp.relaydroid.storage.StorageImpl
import tech.relaycorp.relaydroid.storage.mockStorage
import tech.relaycorp.relaynet.issueEndpointCertificate
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath

internal class PublicThirdPartyEndpointTest {

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
        val privateAddress = UUID.randomUUID().toString()
        val publicAddress = "example.org"
        whenever(storage.publicThirdPartyCertificate.get(any()))
            .thenReturn(PublicThirdPartyEndpoint.StoredData(publicAddress, PDACertPath.PDA))

        val endpoint = PublicThirdPartyEndpoint.load(privateAddress)!!
        assertEquals(publicAddress, endpoint.publicAddress)
        assertEquals("https://$publicAddress", endpoint.address)
        assertEquals(PDACertPath.PDA, endpoint.identityCertificate)
    }

    @Test
    fun load_nonExistent() = runBlockingTest {
        whenever(storage.publicThirdPartyCertificate.get(any())).thenReturn(null)

        assertNull(PublicThirdPartyEndpoint.load(UUID.randomUUID().toString()))
    }

    @Test
    fun import_successful() = runBlockingTest {
        val publicAddress = "example.org"
        with(PublicThirdPartyEndpoint.import(publicAddress, PDACertPath.PDA)) {
            assertEquals(publicAddress, this.publicAddress)
            assertEquals(PDACertPath.PDA, identityCertificate)
            assertEquals("https://$publicAddress", this.address)
        }

        verify(storage.publicThirdPartyCertificate).set(
            PDACertPath.PDA.subjectPrivateAddress,
            PublicThirdPartyEndpoint.StoredData(
                publicAddress,
                PDACertPath.PDA
            )
        )
    }

    @Test
    fun import_invalidCertificate() = runBlockingTest {
        val cert = issueEndpointCertificate(
            subjectPublicKey = KeyPairSet.PRIVATE_GW.public,
            issuerPrivateKey = KeyPairSet.PRIVATE_GW.private,
            validityStartDate = ZonedDateTime.now().minusDays(2),
            validityEndDate = ZonedDateTime.now().minusDays(1)
        )

        expectedException.expect(InvalidThirdPartyEndpoint::class.java)
        expectedException.expectMessage("Invalid identity certificate")

        PublicThirdPartyEndpoint.import("example.org", cert)
    }

    @Test
    fun storedDataSerialization() {
        val publicAddress = "example.org"
        val certificate = PDACertPath.PDA

        val dataSerialized =
            PublicThirdPartyEndpoint.StoredData(publicAddress, certificate).serialize()
        val data = PublicThirdPartyEndpoint.StoredData.deserialize(dataSerialized)

        assertEquals(publicAddress, data.publicAddress)
        assertEquals(certificate, data.identityCertificate)
    }
}
