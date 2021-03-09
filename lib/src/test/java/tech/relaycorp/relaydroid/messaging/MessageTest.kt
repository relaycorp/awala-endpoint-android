package tech.relaycorp.relaydroid.messaging

import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Test
import tech.relaycorp.relaydroid.endpoint.PublicThirdPartyEndpoint
import tech.relaycorp.relaydroid.test.FirstPartyEndpointFactory
import tech.relaycorp.relaynet.testing.pki.PDACertPath
import java.time.ZonedDateTime
import kotlin.random.Random

internal class MessageTest {

    private val senderEndpoint = FirstPartyEndpointFactory.build()
    private val recipientEndpoint = PublicThirdPartyEndpoint("http://example.org", PDACertPath.PUBLIC_GW)

    @Test
    fun ttl() = runBlockingTest {
        val creationDate = ZonedDateTime.now()
        val message = OutgoingMessage.build(
            "the type",
            Random.Default.nextBytes(10),
            senderEndpoint = senderEndpoint,
            recipientEndpoint = recipientEndpoint,
            creationDate,
            expiryDate = creationDate.plusMinutes(1)
        )

        assertEquals(
            60,
            message.ttl
        )
    }
}
