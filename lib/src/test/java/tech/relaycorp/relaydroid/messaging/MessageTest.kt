package tech.relaycorp.relaydroid.messaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.relaycorp.relaydroid.PublicThirdPartyEndpoint
import tech.relaycorp.relaydroid.test.FirstPartyEndpointFactory
import tech.relaycorp.relaydroid.test.MessageFactory
import java.time.ZonedDateTime
import kotlin.random.Random

internal class MessageTest {

    private val senderEndpoint = FirstPartyEndpointFactory.build()
    private val receiverEndpoint = PublicThirdPartyEndpoint("http://example.org")

    @Test
    fun ttl() {
        val creationDate = ZonedDateTime.now()
        val message = OutgoingMessage(
            Random.Default.nextBytes(10),
            senderEndpoint = senderEndpoint,
            receiverEndpoint = receiverEndpoint,
            creationDate,
            expirationDate = creationDate.plusMinutes(1)
        )

        assertEquals(
            60,
            message.ttl
        )
    }

    @Test
    fun validate_whenCorrect() {
        MessageFactory.buildOutgoing().validate()
    }

    @Test
    fun validate_withEmptyMessage() {
        val message = OutgoingMessage(
            ByteArray(0),
            senderEndpoint = senderEndpoint,
            receiverEndpoint = receiverEndpoint
        )

        val e = getValidationErrorOrFail(message)
        assertTrue(e is InvalidMessageException)
        assertEquals("Empty message", e.message)
    }

    @Test
    fun validate_withFutureCreationDate() {
        val message = OutgoingMessage(
            Random.Default.nextBytes(10),
            senderEndpoint = senderEndpoint,
            receiverEndpoint = receiverEndpoint,
            creationDate = ZonedDateTime.now().plusMinutes(1)
        )

        val e = getValidationErrorOrFail(message)
        assertTrue(e is InvalidMessageException)
        assertEquals("Creation date must be in the past", e.message)
    }

    @Test
    fun validate_withExpirationBeforeCreation() {
        val message = OutgoingMessage(
            Random.Default.nextBytes(10),
            senderEndpoint = senderEndpoint,
            receiverEndpoint = receiverEndpoint,
            creationDate = ZonedDateTime.now().minusMinutes(1),
            expirationDate = ZonedDateTime.now().minusMinutes(2)
        )

        val e = getValidationErrorOrFail(message)
        assertTrue(e is InvalidMessageException)
        assertEquals("Expiration date must be after creation date", e.message)
    }

    @Test
    fun validate_withExpirationBiggerThanMax() {
        val message = OutgoingMessage(
            Random.Default.nextBytes(10),
            senderEndpoint = senderEndpoint,
            receiverEndpoint = receiverEndpoint,
            creationDate = ZonedDateTime.now().minusDays(10),
            expirationDate = ZonedDateTime.now().plusDays(21)
        )

        val e = getValidationErrorOrFail(message)
        assertTrue(e is InvalidMessageException)
        assertEquals("Expiration date cannot be longer than 30 days after creation date", e.message)
    }

    private fun getValidationErrorOrFail(message: OutgoingMessage): Exception {
        try {
            message.validate()
            throw AssertionError("Message should not be valid")
        } catch (e: Exception) {
            return e
        }
    }
}
