package tech.relaycorp.relaydroid.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import tech.relaycorp.relaydroid.Relaynet
import tech.relaycorp.relaydroid.storage.StorageImpl
import tech.relaycorp.relaydroid.storage.mockStorage
import tech.relaycorp.relaynet.issueEndpointCertificate
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath
import tech.relaycorp.relaynet.wrappers.x509.CertificateException
import java.time.ZonedDateTime

internal class PublicThirdPartyEndpointTest {

    private lateinit var storage: StorageImpl

    @Before
    fun setUp() {
        storage = mockStorage().also { Relaynet.storage = it }
    }

    @Test
    fun load_successful() = runBlockingTest {
        whenever(storage.publicThirdPartyCertificate.get(any()))
            .thenReturn(PDACertPath.PUBLIC_GW)
        val address = "example.org"

        with(PublicThirdPartyEndpoint.load(address)!!) {
            assertEquals(address, this.address)
            assertEquals(PDACertPath.PUBLIC_GW, certificate)
        }
    }

    @Test
    fun load_nonExistent() = runBlockingTest {
        whenever(storage.publicThirdPartyCertificate.get(any())).thenReturn(null)

        assertNull(PublicThirdPartyEndpoint.load("example.org"))
    }

    @Test
    fun import_successful() = runBlockingTest {
        with(PublicThirdPartyEndpoint.import(PDACertPath.PUBLIC_GW)) {
            assertEquals(address, this.address)
            assertEquals(PDACertPath.PUBLIC_GW, certificate)
        }

        verify(storage.publicThirdPartyCertificate).set(
            PDACertPath.PUBLIC_GW.subjectPrivateAddress,
            PDACertPath.PUBLIC_GW
        )
    }

    @Test(expected = CertificateException::class)
    fun import_invalidCertificate() = runBlockingTest {
        val cert = issueEndpointCertificate(
            subjectPublicKey = KeyPairSet.PRIVATE_GW.public,
            issuerPrivateKey = KeyPairSet.PRIVATE_GW.private,
            validityEndDate = ZonedDateTime.now().minusDays(1)
        )

        PublicThirdPartyEndpoint.import(cert)
    }
}
