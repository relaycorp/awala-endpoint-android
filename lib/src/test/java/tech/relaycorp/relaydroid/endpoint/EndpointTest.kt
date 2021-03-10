package tech.relaycorp.relaydroid.endpoint

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.relaycorp.relaynet.testing.pki.PDACertPath

internal class EndpointTest {
    @Test
    fun privateAddress() {
        val endpoint = PublicThirdPartyEndpoint(
            "example.com",
            PDACertPath.PRIVATE_ENDPOINT
        )

        assertEquals(PDACertPath.PRIVATE_ENDPOINT.subjectPrivateAddress, endpoint.privateAddress)
    }
}
