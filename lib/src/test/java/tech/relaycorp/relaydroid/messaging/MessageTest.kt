package tech.relaycorp.relaydroid.messaging

import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Test
import tech.relaycorp.relaydroid.PublicThirdPartyEndpoint
import tech.relaycorp.relaydroid.test.FirstPartyEndpointFactory
import java.time.ZonedDateTime
import kotlin.random.Random

internal class MessageTest {

    private val senderEndpoint = FirstPartyEndpointFactory.build()
    private val recipientEndpoint = PublicThirdPartyEndpoint("http://example.org")

    @Test
    fun ttl() = runBlockingTest {
        val creationDate = ZonedDateTime.now()
        val message = OutgoingMessage.build(
            Random.Default.nextBytes(10),
            senderEndpoint = senderEndpoint,
            recipientEndpoint = recipientEndpoint,
            creationDate,
            expirationDate = creationDate.plusMinutes(1)
        )

        assertEquals(
            60,
            message.ttl
        )
    }
}
