package tech.relaycorp.awaladroid.endpoint

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import tech.relaycorp.relaynet.issueEndpointCertificate
import tech.relaycorp.relaynet.keystores.PrivateKeyStore
import tech.relaycorp.relaynet.testing.pki.KeyPairSet

internal class RenewExpiringCertificatesTest() {

    private val privateKeyStore = mock<PrivateKeyStore>()

    @Before
    fun setUp() = runTest {
        whenever(privateKeyStore.retrieveAllIdentityKeys())
            .thenReturn(listOf(KeyPairSet.PRIVATE_ENDPOINT.private))
    }

    @Test
    fun `renews expiring certificates`() = runTest {
        val expiringEndpoint = buildFirstPartyEndpoint(ZonedDateTime.now().plusDays(50))
        val subject = RenewExpiringCertificates(privateKeyStore) { expiringEndpoint }

        subject()

        verify(expiringEndpoint).reRegister()
    }

    @Test
    fun `does not renew not expiring certificates`() = runTest {
        val notExpiringEndpoint = buildFirstPartyEndpoint(ZonedDateTime.now().plusDays(70))
        val subject = RenewExpiringCertificates(privateKeyStore) { notExpiringEndpoint }

        subject()

        verify(notExpiringEndpoint, never()).reRegister()
    }

    private fun buildFirstPartyEndpoint(certExpiryDate: ZonedDateTime): FirstPartyEndpoint {
        val firstPartyEndpoint = mock<FirstPartyEndpoint>()
        val expiringCert = issueEndpointCertificate(
            KeyPairSet.PRIVATE_ENDPOINT.public,
            KeyPairSet.PRIVATE_GW.private,
            certExpiryDate
        )
        whenever(firstPartyEndpoint.identityCertificate).thenReturn(expiringCert)
        return firstPartyEndpoint
    }
}
