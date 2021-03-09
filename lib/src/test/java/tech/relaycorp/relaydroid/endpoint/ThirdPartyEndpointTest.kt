package tech.relaycorp.relaydroid.endpoint

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.relaycorp.relaydroid.test.PUBLIC_ENDPOINT_CERTIFICATE
import tech.relaycorp.relaynet.messages.Parcel
import tech.relaycorp.relaynet.messages.payloads.ServiceMessage
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath

public class ThirdPartyEndpointTest {
    @Test
    public fun encryptServiceMessage() {
        val endpoint =
            PublicThirdPartyEndpoint("example.com", PUBLIC_ENDPOINT_CERTIFICATE)
        val serviceMessage = ServiceMessage("foo/bar", "content".toByteArray())

        val serviceMessageEncrypted = endpoint.encryptServiceMessage(serviceMessage)

        val parcel = Parcel(endpoint.address, serviceMessageEncrypted, PDACertPath.PRIVATE_ENDPOINT)
        val serviceMessageDecrypted = parcel.unwrapPayload(KeyPairSet.PDA_GRANTEE.private)
        assertEquals(serviceMessage.type, serviceMessageDecrypted.type)
        assertEquals(serviceMessage.content.asList(), serviceMessageDecrypted.content.asList())
    }
}
